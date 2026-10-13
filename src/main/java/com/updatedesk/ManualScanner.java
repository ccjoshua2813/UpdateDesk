package com.updatedesk;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Lee los jars de la carpeta manual/ e identifica de qué plugin son por su plugin.yml. No modifica nada. */
final class ManualScanner {
	private ManualScanner() {}

	/** {nombre en minúsculas -> jar}. Si hay varios del mismo plugin, se usa el más reciente. */
	static Map<String, ManualJar> scan(Path dir, long maxBytes) {
		Map<String, ManualJar> out = new HashMap<>();
		if (!Files.isDirectory(dir)) return out;
		try (var files = Files.list(dir)) {
			for (Path p : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".jar") && Files.isRegularFile(x))::iterator) {
				try {
					long size = Files.size(p);
					if (size > maxBytes) continue;
					String[] meta = descriptor(p);
					if (meta == null) continue;
					ManualJar jar = new ManualJar(p, meta[0], meta[1], sha256(p), size);
					ManualJar prev = out.get(meta[0].toLowerCase());
					if (prev == null || Files.getLastModifiedTime(p).compareTo(Files.getLastModifiedTime(prev.path())) > 0) out.put(meta[0].toLowerCase(), jar);
				} catch (Exception ignored) { /* archivo ilegible: se ignora */ }
			}
		} catch (Exception ignored) { }
		return out;
	}

	/** {nombre, versión} declarados en el jar, o null si no es un plugin. */
	static String[] descriptor(Path jar) throws Exception {
		try (ZipFile z = new ZipFile(jar.toFile())) {
			ZipEntry e = z.getEntry("paper-plugin.yml");
			if (e == null) e = z.getEntry("plugin.yml");
			if (e == null) return null;
			try (var in = new InputStreamReader(z.getInputStream(e), StandardCharsets.UTF_8)) {
				YamlConfiguration y = YamlConfiguration.loadConfiguration(in);
				String name = y.getString("name");
				return name == null ? null : new String[]{name, String.valueOf(y.get("version", "?"))};
			}
		}
	}

	private static String sha256(Path p) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		try (InputStream in = Files.newInputStream(p)) {
			byte[] buf = new byte[65536];
			for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
		}
		return HexFormat.of().formatHex(md.digest());
	}

	/** ¿El jar de manual/ es exactamente el que ya está instalado (mismo SHA-256)? Entonces no hay nada que instalar. */
	static boolean identical(Path installedJar, ManualJar m) {
		try { return installedJar != null && sha256(installedJar).equalsIgnoreCase(m.sha256()); } catch (Exception e) { return false; }
	}
}
