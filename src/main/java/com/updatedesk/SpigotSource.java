package com.updatedesk;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Última versión publicada en SpigotMC, por el endpoint oficial de versiones que usan los propios plugins (api.spigotmc.org).
 * Spec: el número de recurso (la cifra al final de la URL en spigotmc.org). Solo informa: SpigotMC no permite descargas automáticas.
 */
final class SpigotSource implements ExternalSource {
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final String ua;

	SpigotSource(String ua) { this.ua = ua; }

	@Override
	public CompletableFuture<Latest> latest(String spec) {
		String id = spec.trim();
		if (!id.matches("\\d{1,9}")) throw new IllegalArgumentException("número de recurso de Spigot no válido: " + spec);
		HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.spigotmc.org/legacy/update.php?resource=" + id)).header("User-Agent", ua).timeout(Duration.ofSeconds(20)).GET().build();
		return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(r -> {
			String v = r.body().trim();
			if (r.statusCode() != 200 || v.isEmpty() || v.length() > 60 || v.contains("<")) throw new IllegalStateException("SpigotMC no devolvió una versión (HTTP " + r.statusCode() + ")");
			return new Latest(v, null, "https://www.spigotmc.org/resources/" + id + "/");
		});
	}
}
