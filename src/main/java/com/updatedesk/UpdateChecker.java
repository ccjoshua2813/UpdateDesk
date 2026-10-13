package com.updatedesk;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Orquesta la comprobación. Cada plugin se resuelve por una de dos vías:
 * 1) si aparece en "sources:" de la configuración, por esa fuente oficial (GeyserMC o GitHub), comparando hashes;
 * 2) si no, en Modrinth, identificándolo por el hash de su jar.
 */
final class UpdateChecker {
	record Report(List<UpdateResult> results, Instant time) {
		long count(UpdateResult.Status s) { return results.stream().filter(r -> r.status() == s).count(); }
	}

	static final String USER_AGENT_BASE = "ccjoshua2813/UpdateDesk/";

	private final UpdateDeskPlugin plugin;
	private final ModrinthClient modrinth;
	private final Map<String, ExternalSource> external = new HashMap<>();
	private volatile Report last;

	UpdateChecker(UpdateDeskPlugin plugin) {
		this.plugin = plugin;
		String ua = USER_AGENT_BASE + plugin.getPluginMeta().getVersion() + " (github.com/ccjoshua2813)";
		this.modrinth = new ModrinthClient(ua);
		external.put("geysermc", new GeyserMcSource(ua));
		external.put("github", new GithubSource(ua));
		external.put("jenkins", new JenkinsSource(ua));
		external.put("spigot", new SpigotSource(ua));
	}

	Report lastReport() { return last; }

	void invalidate() { last = null; }

	/** Plugins que se pueden preparar: hay una versión más nueva con archivo descargable y aún no está preparada. */
	List<UpdateResult> updatable(StagedStore staged) {
		Report r = last;
		if (r == null) return List.of();
		return r.results().stream().filter(x -> x.updatable() && !staged.contains(x.name())).toList();
	}

	boolean isFresh() {
		Report r = last;
		return r != null && r.time().plusSeconds(plugin.getConfig().getInt("cache-minutes", 10) * 60L).isAfter(Instant.now());
	}

	/** Página del plugin: la de "links:" en la configuración o, si no, el sitio web que declara en su plugin.yml. */
	private String linkFor(String pluginName) {
		String cfg = plugin.getConfig().getString("links." + pluginName, "");
		if (!cfg.isBlank()) return cfg;
		var pl = Bukkit.getPluginManager().getPlugin(pluginName);
		String web = pl == null ? null : pl.getPluginMeta().getWebsite();
		return web != null && web.startsWith("http") ? web : null;
	}

	private Set<String> lowerSet(String path) {
		Set<String> s = new HashSet<>();
		for (String n : plugin.getConfig().getStringList(path)) s.add(n.toLowerCase());
		return s;
	}

	CompletableFuture<Report> check() {
		Set<String> ignored = new HashSet<>(plugin.getConfig().getStringList("ignore"));
		Map<String, List<String>> sources = new HashMap<>();
		ConfigurationSection sec = plugin.getConfig().getConfigurationSection("sources");
		if (sec != null) for (String k : sec.getKeys(false)) {
			List<String> specs = sec.isList(k) ? sec.getStringList(k) : List.of(sec.getString(k, ""));
			specs = specs.stream().filter(x -> x != null && !x.isBlank()).toList();
			if (!specs.isEmpty()) sources.put(k, specs);
		}

		// UpdateDesk se revisa a sí mismo solo si lo indicas en sources: (por ejemplo desde su repositorio de GitHub)
		List<InstalledPlugin> installed = PluginScanner.scan(ignored, plugin, sources.containsKey(plugin.getName()));      // lee los jars, en el hilo que llama
		List<InstalledPlugin> viaModrinth = installed.stream().filter(p -> !sources.containsKey(p.name())).toList();
		List<InstalledPlugin> viaExternal = installed.stream().filter(p -> sources.containsKey(p.name())).toList();

		CompletableFuture<List<UpdateResult>> a = checkModrinth(viaModrinth);
		List<CompletableFuture<UpdateResult>> ext = new ArrayList<>();
		for (InstalledPlugin p : viaExternal) ext.add(checkExternal(p, sources.get(p.name())));

		return a.thenCombine(CompletableFuture.allOf(ext.toArray(new CompletableFuture[0])), (mr, v) -> {
			List<UpdateResult> all = new ArrayList<>(mr);
			ext.forEach(f -> all.add(f.join()));
			all.sort(Comparator.comparing(UpdateResult::name, String.CASE_INSENSITIVE_ORDER));
			return last = new Report(all, Instant.now());
		});
	}

	// ----- fuentes externas (por hash SHA-256 del jar instalado) -----

	private CompletableFuture<UpdateResult> checkExternal(InstalledPlugin p, List<String> specs) {
		List<CompletableFuture<Object[]>> parts = new ArrayList<>();
		for (String spec : specs) {
			int i = spec.indexOf(':');
			String kind = i > 0 ? spec.substring(0, i).toLowerCase() : "";
			ExternalSource src = external.get(kind);
			if (src == null) { parts.add(CompletableFuture.completedFuture(new Object[]{kind, null, "fuente no válida en sources: " + spec})); continue; }
			try {
				parts.add(src.latest(spec.substring(i + 1)).handle((l, err) -> new Object[]{kind, l, err == null ? null : rootMessage(err)}));
			} catch (RuntimeException e) { parts.add(CompletableFuture.completedFuture(new Object[]{kind, null, e.getMessage()})); }
		}
		return CompletableFuture.allOf(parts.toArray(new CompletableFuture[0])).thenApply(v -> {
			ExternalSource.Latest build = null, stable = null, spigot = null;
			String firstError = null;
			for (var f : parts) {
				Object[] o = f.join();
				if (o[1] == null) { if (firstError == null) firstError = (String) o[2]; continue; }
				ExternalSource.Latest l = (ExternalSource.Latest) o[1];
				if ("jenkins".equals(o[0])) { if (build == null) build = l; }
				else if ("spigot".equals(o[0])) { if (spigot == null) spigot = l; }
				else if (stable == null) stable = l;
			}
			if (build != null) return fromBuild(p, build, stable);
			if (spigot != null && stable == null) return fromInfoOnly(p, spigot, "SpigotMC");
			if (stable != null) return fromStable(p, stable);
			return failed(p, firstError == null ? "sin datos" : firstError);
		});
	}

	/** Build de desarrollo: sin hash oficial, así que solo se informa (descarga manual) y se explica la diferencia con la estable. */
	private UpdateResult fromBuild(InstalledPlugin p, ExternalSource.Latest build, ExternalSource.Latest stable) {
		if (!VersionCompare.newer(build.version(), p.version()))
			return new UpdateResult(p.name(), p.version(), UpdateResult.Status.UP_TO_DATE, build.version(), "dev", p.name(), build.pageUrl(), p.jar(), null, "al día con las builds de desarrollo");
		String note = "build de desarrollo " + build.version() + " (suele traer soporte de Minecraft nuevo, sin hash oficial: descarga manual)"
			+ (stable != null ? " · última estable: " + stable.version() + " (con hash oficial, pero puede ir en modo compatibilidad)" : "");
		return new UpdateResult(p.name(), p.version(), UpdateResult.Status.UPDATE_MANUAL, build.version(), "dev", p.name(), build.pageUrl(), p.jar(), null, note);
	}

	/** Fuente que solo informa la última versión (SpigotMC): si la instalada ya la iguala o supera, cuenta como al día; si no, descarga manual. */
	private UpdateResult fromInfoOnly(InstalledPlugin p, ExternalSource.Latest latest, String site) {
		String a = latest.version().trim().replaceFirst("^[vV]", ""), b = p.version().trim().replaceFirst("^[vV]", "");
		if (a.equalsIgnoreCase(b) || !VersionCompare.newer(latest.version(), p.version()))
			return new UpdateResult(p.name(), p.version(), UpdateResult.Status.UP_TO_DATE, latest.version(), "release", p.name(), latest.pageUrl(), p.jar(), null, "al día según " + site);
		return new UpdateResult(p.name(), p.version(), UpdateResult.Status.UPDATE_MANUAL, latest.version(), "release", p.name(), latest.pageUrl(), p.jar(), null,
			"versión " + latest.version() + " en " + site + " (descarga manual: no permite descargas automáticas)");
	}

	/** Última publicada con archivo y hash oficiales: compara el hash del jar instalado con el suyo. */
	private UpdateResult fromStable(InstalledPlugin p, ExternalSource.Latest latest) {
		UpdateResult.Download d = latest.download();
		boolean same = d != null && p.sha256().equalsIgnoreCase(d.hash());
		if (!same && d != null && VersionCompare.devAtLeast(p.version(), latest.version()))
			return new UpdateResult(p.name(), p.version(), UpdateResult.Status.UP_TO_DATE, latest.version(), "release", p.name(), latest.pageUrl(), p.jar(), d, "compilación de desarrollo");
		if (same) return new UpdateResult(p.name(), p.version(), UpdateResult.Status.UP_TO_DATE, latest.version(), "release", p.name(), latest.pageUrl(), p.jar(), d, null);
		if (d == null) return new UpdateResult(p.name(), p.version(), UpdateResult.Status.UPDATE_MANUAL, latest.version(), "release", p.name(), latest.pageUrl(), p.jar(), null, "sin hash oficial: descarga manual");
		return new UpdateResult(p.name(), p.version(), UpdateResult.Status.UPDATE_AVAILABLE, latest.version(), "release", p.name(), latest.pageUrl(), p.jar(), d, null);
	}

	private UpdateResult failed(InstalledPlugin p, String why) {
		return new UpdateResult(p.name(), p.version(), UpdateResult.Status.NOT_FOUND, null, null, null, linkFor(p.name()), p.jar(), null, "no se pudo consultar su fuente (" + why + ")");
	}

	// ----- Modrinth -----

	private CompletableFuture<List<UpdateResult>> checkModrinth(List<InstalledPlugin> list) {
		if (list.isEmpty()) return CompletableFuture.completedFuture(List.of());
		List<String> loaders = plugin.getConfig().getStringList("loaders");
		return modrinth.identify(list.stream().map(InstalledPlugin::sha1).toList()).thenCompose(identified -> {
			Set<String> projectIds = new LinkedHashSet<>();
			for (var e : identified.entrySet()) projectIds.add(e.getValue().getAsJsonObject().get("project_id").getAsString());
			Map<String, CompletableFuture<JsonArray>> versions = new HashMap<>();
			for (String id : projectIds) versions.put(id, modrinth.versions(id, loaders));
			CompletableFuture<Map<String, JsonObject>> projects = modrinth.projects(projectIds);
			return projects.thenCombine(CompletableFuture.allOf(versions.values().toArray(new CompletableFuture[0])), (proj, v) -> {
				Map<String, JsonArray> vs = new HashMap<>();
				versions.forEach((k, f) -> vs.put(k, f.join()));
				return classify(list, identified, vs, proj);
			});
		});
	}

	private List<UpdateResult> classify(List<InstalledPlugin> installed, JsonObject identified, Map<String, JsonArray> versions, Map<String, JsonObject> projects) {
		String mc = Bukkit.getMinecraftVersion();
		Set<String> prerelease = lowerSet("allow-prerelease"), assume = lowerSet("assume-compatible");
		List<UpdateResult> out = new ArrayList<>();
		for (InstalledPlugin p : installed) {
			JsonObject cur = identified.has(p.sha1()) ? identified.getAsJsonObject(p.sha1()) : null;
			if (cur == null) { out.add(new UpdateResult(p.name(), p.version(), UpdateResult.Status.NOT_FOUND, null, null, null, linkFor(p.name()), p.jar(), null, null)); continue; }
			String pid = cur.get("project_id").getAsString();
			JsonObject proj = projects.get(pid);
			String title = proj != null ? proj.get("title").getAsString() : p.name();
			String slug = proj != null ? proj.get("slug").getAsString() : pid;
			String curDate = cur.get("date_published").getAsString();

			// Versiones permitidas por el canal (release; beta/alpha solo si el plugin está en allow-prerelease), las más nuevas primero
			boolean pre = prerelease.contains(p.name().toLowerCase());
			JsonObject newestAllowed = null, newestCompat = null;
			boolean any = false;
			for (var e : versions.getOrDefault(pid, new JsonArray())) {
				JsonObject v = e.getAsJsonObject();
				any = true;
				if (!"release".equals(v.get("version_type").getAsString()) && !pre) continue;
				if (newestAllowed == null) newestAllowed = v;
				if (newestCompat == null && declares(v, mc)) newestCompat = v;
			}
			String pageBase = "https://modrinth.com/plugin/" + slug;
			if (!any) { out.add(new UpdateResult(p.name(), p.version(), UpdateResult.Status.NO_COMPATIBLE, null, null, title, pageBase, p.jar(), null, null)); continue; }

			JsonObject target = null;
			UpdateResult.Status status = UpdateResult.Status.UP_TO_DATE;
			if (newestCompat != null && newestCompat.get("date_published").getAsString().compareTo(curDate) > 0) {
				target = newestCompat; status = UpdateResult.Status.UPDATE_AVAILABLE;
			} else if (newestAllowed != null && newestAllowed.get("date_published").getAsString().compareTo(curDate) > 0) {
				target = newestAllowed;
				status = assume.contains(p.name().toLowerCase()) ? UpdateResult.Status.UPDATE_AVAILABLE : UpdateResult.Status.UPDATE_UNCONFIRMED;
			}
			if (target == null) { out.add(new UpdateResult(p.name(), p.version(), UpdateResult.Status.UP_TO_DATE, null, null, title, pageBase, p.jar(), null, null)); continue; }
			String ver = target.get("version_number").getAsString();
			out.add(new UpdateResult(p.name(), p.version(), status, ver, target.get("version_type").getAsString(), title, pageBase + "/version/" + ver, p.jar(), downloadOf(target), null));
		}
		return out;
	}

	private static boolean declares(JsonObject version, String mc) {
		for (var g : version.getAsJsonArray("game_versions")) if (g.getAsString().equals(mc)) return true;
		return false;
	}

	/** Archivo principal de una versión de Modrinth, con su URL y su hash SHA-512 oficial. */
	private static UpdateResult.Download downloadOf(JsonObject version) {
		JsonObject chosen = null;
		for (var f : version.getAsJsonArray("files")) {
			JsonObject o = f.getAsJsonObject();
			if (chosen == null || (o.has("primary") && o.get("primary").getAsBoolean())) chosen = o;
		}
		if (chosen == null) return null;
		return new UpdateResult.Download("modrinth", chosen.get("url").getAsString(), chosen.get("filename").getAsString(), "SHA-512",
			chosen.getAsJsonObject("hashes").get("sha512").getAsString(), chosen.get("size").getAsLong());
	}

	private static String rootMessage(Throwable t) {
		while (t.getCause() != null) t = t.getCause();
		return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
	}
}
