package com.updatedesk;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Prepara una actualización de forma segura: descarga solo de fuentes verificadas, comprueba el hash oficial y que el jar
 * sea el mismo plugin, guarda una copia del jar anterior y deja el nuevo en plugins/update/ (Paper lo aplica al reiniciar).
 */
final class Installer {
	/** Únicos servidores desde los que se descarga, según la fuente del archivo. */
	private static final Map<String, Set<String>> ALLOWED_HOSTS = Map.of(
		"modrinth", Set.of("cdn.modrinth.com"),
		"geysermc", Set.of("download.geysermc.org"),
		"github", Set.of("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com"));

	private final UpdateDeskPlugin plugin;
	private final StagedStore staged;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

	Installer(UpdateDeskPlugin plugin, StagedStore staged) {
		this.plugin = plugin;
		this.staged = staged;
	}

	/** Resultado de preparar un plugin: mensaje legible y si salió bien. */
	record Outcome(String plugin, boolean ok, String message) {}

	CompletableFuture<Outcome> stage(UpdateResult r) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return doStage(r);
			} catch (Exception e) {
				return new Outcome(r.name(), false, "no se actualizó: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
			}
		});
	}

	private Outcome doStage(UpdateResult r) throws Exception {
		UpdateResult.Download d = r.download();
		if (d == null) throw new IOException("Modrinth no publica un archivo para esta versión");
		URI uri = URI.create(d.url());
		Set<String> hosts = ALLOWED_HOSTS.getOrDefault(d.source(), Set.of());
		if (!"https".equals(uri.getScheme()) || !hosts.contains(uri.getHost())) throw new IOException("origen no permitido: " + uri.getHost());
		long max = plugin.getConfig().getLong("max-download-mb", 100) * 1024 * 1024;
		if (d.size() > max) throw new IOException("el archivo es demasiado grande (" + d.size() / 1024 / 1024 + " MB)");

		Path data = plugin.getDataFolder().toPath();
		Path tmp = Files.createDirectories(data.resolve("tmp")).resolve(d.fileName() + ".part");
		try {
			// 1) Descargar
			HttpResponse<Path> resp = http.send(HttpRequest.newBuilder(uri).header("User-Agent", "ccjoshua2813/UpdateDesk/" + plugin.getPluginMeta().getVersion()).timeout(Duration.ofMinutes(2)).GET().build(),
				HttpResponse.BodyHandlers.ofFile(tmp));
			if (resp.statusCode() != 200) throw new IOException("la descarga respondió HTTP " + resp.statusCode());
			if (!hosts.contains(resp.uri().getHost())) throw new IOException("la descarga se redirigió a un origen no permitido");
			if (Files.size(tmp) > max) throw new IOException("el archivo descargado supera el límite");

			// 2) Hash oficial de la fuente
			MessageDigest official = MessageDigest.getInstance(d.algorithm()), sha1 = MessageDigest.getInstance("SHA-1");
			try (InputStream in = Files.newInputStream(tmp)) {
				byte[] buf = new byte[65536];
				for (int n; (n = in.read(buf)) > 0; ) { official.update(buf, 0, n); sha1.update(buf, 0, n); }
			}
			if (!HexFormat.of().formatHex(official.digest()).equalsIgnoreCase(d.hash())) throw new IOException("el hash " + d.algorithm() + " no coincide con el publicado por la fuente oficial");

			// 3) Debe ser el mismo plugin
			String inside = pluginNameOf(tmp);
			if (inside == null || !inside.equalsIgnoreCase(r.name())) throw new IOException("el jar descargado es de otro plugin (" + inside + ")");

			// 4) Copia del jar actual
			backupCurrent(r);

			// 5) Dejarlo en plugins/update/ con el MISMO nombre del jar actual (así Paper lo reemplaza al arrancar)
			Path updateDir = Files.createDirectories(Bukkit.getUpdateFolderFile().toPath());
			Files.move(tmp, updateDir.resolve(r.jar().getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
			staged.put(r.name(), new StagedStore.Entry(r.installedVersion(), r.latestVersion(), HexFormat.of().formatHex(sha1.digest())));
			return new Outcome(r.name(), true, r.installedVersion() + " → " + r.latestVersion() + " preparado (se aplica al reiniciar); copia del anterior guardada");
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	private void backupCurrent(UpdateResult r) throws IOException {
		Path backupDir = Files.createDirectories(plugin.getDataFolder().toPath().resolve("backups").resolve(r.name()));
		String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		Files.copy(r.jar(), backupDir.resolve(stamp + "-" + r.jar().getFileName()), StandardCopyOption.COPY_ATTRIBUTES);
		pruneBackups(backupDir);
	}

	/** Instala un jar subido a mano a manual/: mismas comprobaciones locales, pero SIN hash oficial (el origen lo garantiza quien lo subió). */
	CompletableFuture<Outcome> stageManual(UpdateResult r, ManualJar m) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				if (!m.name().equalsIgnoreCase(r.name())) throw new IOException("el archivo es de otro plugin (" + m.name() + ")");
				if (!Files.isRegularFile(m.path())) throw new IOException("el archivo ya no existe");
				if (Files.size(m.path()) > plugin.getConfig().getLong("max-download-mb", 100) * 1024 * 1024) throw new IOException("el archivo es demasiado grande");
				if (m.path().equals(r.jar())) throw new IOException("el archivo manual no puede ser el jar instalado");
				backupCurrent(r);
				Path updateDir = Files.createDirectories(Bukkit.getUpdateFolderFile().toPath());
				Files.copy(m.path(), updateDir.resolve(r.jar().getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
				staged.put(r.name(), new StagedStore.Entry(r.installedVersion(), m.version(), PluginScanner.hashes(m.path())[0]));
				Path done = Files.createDirectories(m.path().getParent().resolve("aplicados"));
				Files.move(m.path(), done.resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + m.path().getFileName()), StandardCopyOption.REPLACE_EXISTING);
				return new Outcome(r.name(), true, r.installedVersion() + " → " + m.version() + " (manual) preparado; copia del anterior guardada");
			} catch (Exception e) {
				return new Outcome(r.name(), false, "no se instaló: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
			}
		});
	}

	private void pruneBackups(Path dir) throws IOException {
		int keep = Math.max(1, plugin.getConfig().getInt("backups-keep", 3));
		try (var s = Files.list(dir)) {
			List<Path> all = s.filter(p -> p.toString().endsWith(".jar")).sorted().toList();
			for (int i = 0; i < all.size() - keep; i++) Files.deleteIfExists(all.get(i));
		}
	}

	/** Nombre del plugin declarado dentro del jar (paper-plugin.yml o plugin.yml). */
	private static String pluginNameOf(Path jar) throws IOException {
		try (ZipFile z = new ZipFile(jar.toFile())) {
			ZipEntry e = z.getEntry("paper-plugin.yml");
			if (e == null) e = z.getEntry("plugin.yml");
			if (e == null) return null;
			try (var in = new InputStreamReader(z.getInputStream(e), StandardCharsets.UTF_8)) {
				return YamlConfiguration.loadConfiguration(in).getString("name");
			}
		}
	}
}
