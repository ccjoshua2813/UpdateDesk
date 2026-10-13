package com.updatedesk;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Recorre los plugins cargados y calcula el hash de cada jar. No toca ningún archivo. */
final class PluginScanner {
	private PluginScanner() {}

	static List<InstalledPlugin> scan(Set<String> ignored, Plugin self, boolean includeSelf) {
		List<InstalledPlugin> out = new ArrayList<>();
		for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
			if ((p == self && !includeSelf) || ignored.contains(p.getName())) continue;
			try {
				Path jar = Path.of(p.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
				if (!Files.isRegularFile(jar) || !jar.toString().endsWith(".jar")) continue;
				String[] h = hashes(jar);
				out.add(new InstalledPlugin(p.getName(), p.getPluginMeta().getVersion(), jar, h[0], h[1]));
			} catch (Exception e) {
				self.getLogger().warning("No se pudo leer el jar de " + p.getName() + ": " + e.getMessage());
			}
		}
		out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
		return out;
	}

	/** {SHA-1, SHA-256} del archivo, calculados de una sola lectura. */
	static String[] hashes(Path file) throws Exception {
		MessageDigest a = MessageDigest.getInstance("SHA-1"), b = MessageDigest.getInstance("SHA-256");
		try (InputStream in = Files.newInputStream(file)) {
			byte[] buf = new byte[65536];
			for (int n; (n = in.read(buf)) > 0; ) { a.update(buf, 0, n); b.update(buf, 0, n); }
		}
		return new String[]{HexFormat.of().formatHex(a.digest()), HexFormat.of().formatHex(b.digest())};
	}
}
