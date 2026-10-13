package com.updatedesk;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Actualizaciones ya preparadas en plugins/update/ que esperan a que el servidor se reinicie. Persiste en staged.yml. */
final class StagedStore {
	record Entry(String from, String to, String newSha1) {}

	private final File file;
	private final Map<String, Entry> entries = new LinkedHashMap<>();

	StagedStore(File dataFolder) {
		this.file = new File(dataFolder, "staged.yml");
		YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
		ConfigurationSection s = y.getConfigurationSection("staged");
		if (s != null) for (String k : s.getKeys(false))
			entries.put(k, new Entry(s.getString(k + ".from", "?"), s.getString(k + ".to", "?"), s.getString(k + ".sha1", "")));
	}

	synchronized boolean contains(String plugin) { return entries.containsKey(plugin); }
	synchronized Map<String, Entry> all() { return new LinkedHashMap<>(entries); }
	synchronized boolean isEmpty() { return entries.isEmpty(); }

	synchronized void put(String plugin, Entry e) { entries.put(plugin, e); save(); }

	synchronized void remove(String plugin) { if (entries.remove(plugin) != null) save(); }

	private void save() {
		YamlConfiguration y = new YamlConfiguration();
		entries.forEach((k, e) -> { y.set("staged." + k + ".from", e.from()); y.set("staged." + k + ".to", e.to()); y.set("staged." + k + ".sha1", e.newSha1()); });
		try { y.save(file); } catch (IOException ex) { throw new IllegalStateException(ex); }
	}
}
