package com.updatedesk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/** Releases de GitHub. Spec: "propietario/repositorio:expresión regular del nombre del archivo". Solo se descarga si GitHub publica el hash SHA-256 del archivo. */
final class GithubSource implements ExternalSource {
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final String ua;

	GithubSource(String ua) { this.ua = ua; }

	@Override
	public CompletableFuture<Latest> latest(String spec) {
		int i = spec.indexOf(':');
		if (i < 0) throw new IllegalArgumentException("Formato: propietario/repo:regex");
		String repo = spec.substring(0, i);
		Pattern asset = Pattern.compile(spec.substring(i + 1));
		HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repo + "/releases/latest"))
			.header("User-Agent", ua).header("Accept", "application/vnd.github+json").timeout(Duration.ofSeconds(20)).GET().build();
		return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(r -> {
			if (r.statusCode() != 200) throw new IllegalStateException("GitHub respondió HTTP " + r.statusCode());
			JsonObject d = JsonParser.parseString(r.body()).getAsJsonObject();
			String tag = d.get("tag_name").getAsString().replaceFirst("^[vV](?=\\d)", ""), page = d.get("html_url").getAsString();
			for (JsonElement e : d.getAsJsonArray("assets")) {
				JsonObject a = e.getAsJsonObject();
				if (!asset.matcher(a.get("name").getAsString()).matches()) continue;
				String digest = a.has("digest") && !a.get("digest").isJsonNull() ? a.get("digest").getAsString() : "";
				if (!digest.startsWith("sha256:")) return new Latest(tag, null, page);       // sin hash oficial: no se descarga solo
				return new Latest(tag, new UpdateResult.Download("github", a.get("browser_download_url").getAsString(), a.get("name").getAsString(),
					"SHA-256", digest.substring(7), a.get("size").getAsLong()), page);
			}
			throw new IllegalStateException("ningún archivo de la release " + tag + " coincide con " + asset.pattern());
		});
	}
}
