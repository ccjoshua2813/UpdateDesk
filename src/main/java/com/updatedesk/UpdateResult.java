package com.updatedesk;

import java.nio.file.Path;

/** Resultado de comprobar un plugin. */
record UpdateResult(String name, String installedVersion, Status status, String latestVersion, String versionType,
					String projectTitle, String url, Path jar, Download download, String note) {
	/** El orden de las constantes es el orden en que se muestran las filas. */
	enum Status {
		UPDATE_AVAILABLE,     // hay una versión más reciente y compatible con este Minecraft
		UPDATE_UNCONFIRMED,   // hay una más reciente, pero su autor no declara compatibilidad con este Minecraft
		UPDATE_MANUAL,        // hay una más reciente, pero no se puede descargar automáticamente
		UP_TO_DATE,           // ya tiene la última
		NO_COMPATIBLE,        // no hay versiones publicadas para este tipo de servidor
		NOT_FOUND             // no se pudo comprobar (no está en Modrinth ni en una fuente configurada)
	}

	/** Archivo a descargar, con el hash oficial de la fuente. "source" decide desde qué servidores se permite descargar. */
	record Download(String source, String url, String fileName, String algorithm, String hash, long size) {}

	boolean updatable() { return download != null && (status == Status.UPDATE_AVAILABLE || status == Status.UPDATE_UNCONFIRMED); }
}
