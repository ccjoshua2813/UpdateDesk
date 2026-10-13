package com.updatedesk;

import java.util.concurrent.CompletableFuture;

/** Una fuente oficial distinta de Modrinth para plugins concretos, indicada en la configuración (sources:). */
interface ExternalSource {
	/** Última versión publicada y el archivo (con su SHA-256 oficial) o null si no se puede descargar automáticamente. */
	record Latest(String version, UpdateResult.Download download, String pageUrl) {}

	CompletableFuture<Latest> latest(String spec);
}
