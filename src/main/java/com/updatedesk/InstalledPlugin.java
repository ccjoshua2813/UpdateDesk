package com.updatedesk;

import java.nio.file.Path;

/** Un plugin instalado: su nombre, la versión que declara y los hashes de su jar (sirven para identificarlo). */
record InstalledPlugin(String name, String version, Path jar, String sha1, String sha256) {}
