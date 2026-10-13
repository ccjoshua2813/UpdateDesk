package com.updatedesk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Fuente oficial de Geyser y Floodgate: https://download.geysermc.org. Spec: "geyser" o "floodgate" (opcional :plataforma, por defecto spigot). */
final class GeyserMcSource implements ExternalSource {
	private static final String BASE = "https://download.geysermc.org/v2/projects/";
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
	private final String ua;

	GeyserMcSource(String ua) { this.ua = ua; }

	@Override
	public CompletableFuture<Latest> latest(String spec) {
		String[] parts = spec.split(":");
		String project = parts[0].toLowerCase(), platform = parts.length > 1 ? parts[1] : "spigot";
		HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + project + "/versions/latest/builds/latest")).header("User-Agent", ua).timeout(Duration.ofSeconds(20)).GET().build();
		return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(r -> {
			if (r.statusCode() != 200) throw new IllegalStateException("GeyserMC respondió HTTP " + r.statusCode());
			JsonObject d = JsonParser.parseString(r.body()).getAsJsonObject();
			String version = d.get("version").getAsString();
			int build = d.get("build").getAsInt();
			JsonObject dl = d.getAsJsonObject("downloads").getAsJsonObject(platform);
			if (dl == null) throw new IllegalStateException("GeyserMC no publica la plataforma " + platform);
			String url = BASE + project + "/versions/" + version + "/builds/" + build + "/downloads/" + platform;
			return new Latest(version + "-b" + build, new UpdateResult.Download("geysermc", url, dl.get("name").getAsString(), "SHA-256", dl.get("sha256").getAsString(), 0), "https://geysermc.org/download");
		});
	}
}
