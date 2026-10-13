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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds de desarrollo en un Jenkins conocido. Spec: "URL-del-job:regex-del-archivo-con-un-grupo-para-la-versión".
 * Jenkins no publica un hash oficial, así que esta fuente solo INFORMA (descarga manual): nunca descarga ni instala sola.
 */
final class JenkinsSource implements ExternalSource {
	private static final Set<String> HOSTS = Set.of("ci.ender.zone");
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final String ua;

	JenkinsSource(String ua) { this.ua = ua; }

	@Override
	public CompletableFuture<Latest> latest(String spec) {
		int i = spec.lastIndexOf(':');
		String job = spec.substring(0, i);
		URI uri = URI.create(job.endsWith("/") ? job : job + "/");
		if (!"https".equals(uri.getScheme()) || !HOSTS.contains(uri.getHost())) throw new IllegalArgumentException("servidor Jenkins no permitido: " + uri.getHost());
		Pattern file = Pattern.compile(spec.substring(i + 1));
		HttpRequest req = HttpRequest.newBuilder(uri.resolve("lastSuccessfulBuild/api/json")).header("User-Agent", ua).timeout(Duration.ofSeconds(20)).GET().build();
		return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(r -> {
			if (r.statusCode() != 200) throw new IllegalStateException("Jenkins respondió HTTP " + r.statusCode());
			JsonObject d = JsonParser.parseString(r.body()).getAsJsonObject();
			for (JsonElement e : d.getAsJsonArray("artifacts")) {
				Matcher m = file.matcher(e.getAsJsonObject().get("fileName").getAsString());
				if (m.matches()) return new Latest(m.groupCount() > 0 ? m.group(1) : m.group(), null, d.get("url").getAsString());
			}
			throw new IllegalStateException("la última build no incluye un archivo que coincida con " + file.pattern());
		});
	}
}
