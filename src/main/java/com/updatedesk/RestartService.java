package com.updatedesk;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;

/** Reinicio con cuenta atrás visible para los jugadores, para aplicar las actualizaciones preparadas. */
final class RestartService {
	private static final Set<Integer> ANNOUNCE = Set.of(60, 30, 10, 5, 4, 3, 2, 1);

	private final UpdateDeskPlugin plugin;
	private BukkitTask task;
	private int remaining;

	RestartService(UpdateDeskPlugin plugin) { this.plugin = plugin; }

	boolean isRunning() { return task != null; }

	int countdownSeconds() { return Math.max(5, plugin.getConfig().getInt("restart.countdown-seconds", 30)); }

	void start() {
		if (task != null) return;
		remaining = countdownSeconds();
		task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			if (ANNOUNCE.contains(remaining) || remaining == countdownSeconds()) announce(remaining);
			if (remaining-- <= 0) { task.cancel(); task = null; execute(); }
		}, 0L, 20L);
	}

	boolean cancel() {
		if (task == null) return false;
		task.cancel();
		task = null;
		Bukkit.broadcast(Component.text("UpdateDesk: reinicio cancelado.", NamedTextColor.GREEN));
		return true;
	}

	private static void announce(int s) {
		Bukkit.broadcast(Component.text("⚠ El servidor se reiniciará en " + s + " s para aplicar actualizaciones de plugins.", NamedTextColor.GOLD));
	}

	private void execute() {
		String cmd = plugin.getConfig().getString("restart.command", "").trim();
		plugin.getLogger().info("Reiniciando el servidor para aplicar actualizaciones" + (cmd.isEmpty() ? " (apagado)." : " (comando: " + cmd + ")."));
		if (!cmd.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
		else Bukkit.shutdown();                                       // el panel/hosting lo vuelve a iniciar
	}
}
