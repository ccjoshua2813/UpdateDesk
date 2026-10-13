package com.updatedesk;

import java.nio.file.Path;

/** Un jar subido a plugins/UpdateDesk/manual/: de qué plugin es, su versión y su SHA-256 (no está verificado contra ninguna fuente). */
record ManualJar(Path path, String name, String version, String sha256, long size) {}
