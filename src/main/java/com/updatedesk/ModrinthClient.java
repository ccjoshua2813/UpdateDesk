package com.updatedesk;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Consultas de solo lectura a la API pública de Modrinth (https://docs.modrinth.com). */
final class ModrinthClient {
	private static final String API = "https://api.modrinth.com/v2";
	private static final Gson GSON = new Gson();

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final String userAgent;

	ModrinthClient(String userAgent) {
		this.userAgent = userAgent;       // Modrinth pide un User-Agent identificable
	}

	private CompletableFuture<JsonElement> send(HttpRequest.Builder b) {
		HttpRequest req = b.header("User-Agent", userAgent).timeout(Duration.ofSeconds(20)).build();
		return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(r -> {
			if (r.statusCode() / 100 != 2) throw new IllegalStateException("Modrinth respondió HTTP " + r.statusCode());
			return JsonParser.parseString(r.body());
		});
	}

	/** Identifica versiones a partir del SHA-1 de sus archivos: devuelve {hash: versión}. */
	CompletableFuture<JsonObject> identify(Collection<String> sha1s) {
		JsonObject body = new JsonObject();
		body.add("hashes", GSON.toJsonTree(sha1s));
		body.addProperty("algorithm", "sha1");
		return send(HttpRequest.newBuilder(URI.create(API + "/version_files"))
			.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()))).thenApply(JsonElement::getAsJsonObject);
	}

	/** Todas las versiones de un proyecto para los cargadores indicados (las más nuevas primero), sin filtrar por versión de Minecraft. */
	CompletableFuture<JsonArray> versions(String projectId, List<String> loaders) {
		String q = URLEncoder.encode(GSON.toJson(loaders), StandardCharsets.UTF_8);
		return send(HttpRequest.newBuilder(URI.create(API + "/project/" + projectId + "/version?loaders=" + q)).GET()).thenApply(JsonElement::getAsJsonArray);
	}

	/** Datos de proyectos (título y slug) por id. */
	CompletableFuture<Map<String, JsonObject>> projects(Collection<String> ids) {
		if (ids.isEmpty()) return CompletableFuture.completedFuture(Map.of());
		String q = URLEncoder.encode(GSON.toJson(ids), StandardCharsets.UTF_8);
		return send(HttpRequest.newBuilder(URI.create(API + "/projects?ids=" + q)).GET()).thenApply(e -> {
			Map<String, JsonObject> m = new java.util.HashMap<>();
			JsonArray arr = e.getAsJsonArray();
			for (JsonElement x : arr) m.put(x.getAsJsonObject().get("id").getAsString(), x.getAsJsonObject());
			return m;
		});
	}
}
