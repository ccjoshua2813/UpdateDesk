package com.updatedesk;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Menú de texto con números para la consola del servidor: escribes una opción y pulsas Enter.
 * Mientras está abierto, las líneas que son números o respuestas del menú no llegan al servidor como comandos.
 */
final class ConsoleMenu implements Listener {
	private enum State { MAIN, UPDATE_SELECT, UPDATE_CONFIRM, IGNORE_SELECT, RESTART_ASK, RESTART_CONFIRM, MANUAL_SELECT, MANUAL_CONFIRM }
	private static final Pattern MENU_INPUT = Pattern.compile("(?i)\\d+(\\s+\\d+)*|todos|si|sí|no|salir");
	private static final int IDLE_TICKS = 20 * 120;

	private final UpdateDeskPlugin plugin;
	private State state;
	private List<UpdateResult> choices = List.of();
	private List<UpdateDeskPlugin.ManualPair> manualChoices = List.of();
	private BukkitTask expiry;

	ConsoleMenu(UpdateDeskPlugin plugin) { this.plugin = plugin; }

	private static void out(Component c) { Bukkit.getConsoleSender().sendMessage(c); }
	private static Component t(String s, net.kyori.adventure.text.format.TextColor c) { return Component.text(s, c); }

	void open() {
		state = State.MAIN;
		touch();
		printMain();
	}

	/** Abre el menú directamente en la lista de plugins manuales. */
	void openManual() {
		state = State.MAIN;
		touch();
		plugin.withReport(rep -> showManual());
	}

	private void close(boolean timeout) {
		state = null;
		if (expiry != null) expiry.cancel();
		out(t(timeout ? "UpdateDesk: menú cerrado por inactividad." : "UpdateDesk: menú cerrado.", NamedTextColor.GRAY));
	}

	private void touch() {
		if (expiry != null) expiry.cancel();
		expiry = Bukkit.getScheduler().runTaskLater(plugin, () -> { if (state != null) close(true); }, IDLE_TICKS);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onConsole(ServerCommandEvent e) {
		if (state == null || !(e.getSender() instanceof ConsoleCommandSender)) return;
		String in = e.getCommand().trim();
		if (!MENU_INPUT.matcher(in).matches()) return;        // un comando normal pasa de largo
		e.setCancelled(true);
		touch();
		handle(in.toLowerCase());
	}

	private void printMain() {
		int n = plugin.checker().updatable(plugin.staged()).size(), pending = plugin.staged().all().size();
		out(Component.text("\nUpdateDesk · menú", NamedTextColor.AQUA));
		out(t(" 1) Ver actualizaciones", NamedTextColor.WHITE));
		out(t(" 2) Actualizar plugins" + (n > 0 ? "  (" + n + " disponibles)" : ""), NamedTextColor.WHITE));
		out(t(" 3) Ignorar un plugin", NamedTextColor.WHITE));
		out(t(" 4) Reiniciar el servidor" + (pending > 0 ? "  (" + pending + " actualizaciones pendientes de aplicar)" : ""), NamedTextColor.WHITE));
		out(t(" 5) Plugins manuales  (los que se actualizan subiendo el jar a manual/)", UpdateDialog.RED));
		out(t(" 0) Salir", NamedTextColor.GRAY));
		out(t("Escribe un número y pulsa Enter.", NamedTextColor.DARK_GRAY));
	}

	private void handle(String in) {
		if (in.equals("salir")) { close(false); return; }
		switch (state) {
			case MAIN -> main(in);
			case UPDATE_SELECT -> updateSelect(in);
			case UPDATE_CONFIRM -> updateConfirm(in);
			case IGNORE_SELECT -> ignoreSelect(in);
			case RESTART_ASK -> restartAsk(in);
			case RESTART_CONFIRM -> restartConfirm(in);
			case MANUAL_SELECT -> manualSelect(in);
			case MANUAL_CONFIRM -> manualConfirm(in);
		}
	}

	private void main(String in) {
		switch (in) {
			case "0" -> close(false);
			case "1" -> plugin.withReport(rep -> { printList(rep); printMain(); });
			case "2" -> plugin.withReport(rep -> {
				choices = plugin.checker().updatable(plugin.staged());
				if (choices.isEmpty()) { out(t("No hay actualizaciones disponibles para preparar.", NamedTextColor.GRAY)); printMain(); return; }
				state = State.UPDATE_SELECT;
				out(t("\nActualizaciones disponibles:", NamedTextColor.AQUA));
				for (int i = 0; i < choices.size(); i++) out(Component.text(String.format(" %d) ", i + 1), NamedTextColor.WHITE).append(UpdateDialog.line(choices.get(i))));
				out(t("Escribe los números a actualizar (ej: 1 2), \"todos\" (solo las confirmadas, sin ⚠) o 0 para volver:", NamedTextColor.DARK_GRAY));
			});
			case "3" -> plugin.withReport(rep -> {
				choices = new ArrayList<>(rep.results());
				state = State.IGNORE_SELECT;
				out(t("\nPlugins revisados:", NamedTextColor.AQUA));
				for (int i = 0; i < choices.size(); i++) out(t(String.format(" %d) %s", i + 1, choices.get(i).name()), NamedTextColor.WHITE));
				out(t("Escribe el número del plugin a ignorar (dejará de revisarse) o 0 para volver:", NamedTextColor.DARK_GRAY));
			});
			case "5" -> plugin.withReport(rep -> showManual());
			case "4" -> {
				if (plugin.staged().isEmpty()) { state = State.RESTART_CONFIRM; out(t("No hay actualizaciones pendientes. ¿Reiniciar igualmente? Escribe SI para confirmar o NO:", NamedTextColor.GOLD)); }
				else { state = State.RESTART_ASK; askRestart(); }
			}
			default -> out(t("Opción no válida.", NamedTextColor.RED));
		}
	}

	private void printList(UpdateChecker.Report rep) {
		out(Component.text("\n").append(UpdateDialog.summary(rep, plugin.staged())));
		rep.results().stream().sorted((a, b) -> Integer.compare(a.status().ordinal(), b.status().ordinal()))
			.forEach(r -> out(Component.text(" ").append(UpdateDialog.line(r, plugin.staged()))));
	}

	private void showManual() {
		UpdateChecker.Report rep = plugin.checker().lastReport();
		out(t("\n⚠ Plugins manuales: no se pueden actualizar solos (su fuente no permite descargas verificadas).", UpdateDialog.RED));
		out(t("  1) Abre la página y descarga el jar nuevo.  2) Súbelo a plugins/UpdateDesk/manual/ (panel → Files).  3) Vuelve aquí y elígelo.", NamedTextColor.GRAY));
		if (rep != null) rep.results().stream()
			.filter(r -> (r.status() == UpdateResult.Status.NOT_FOUND || r.status() == UpdateResult.Status.UPDATE_MANUAL) && !plugin.staged().contains(r.name()))
			.forEach(r -> {
				out(t(" ? " + r.name() + "  " + r.installedVersion() + (r.status() == UpdateResult.Status.UPDATE_MANUAL ? " → " + r.latestVersion() : "")
					+ (r.url() != null ? "   página: " + r.url() : "   (sin enlace: añádelo en links: del config.yml)"), NamedTextColor.GRAY));
				if (r.note() != null && r.status() == UpdateResult.Status.UPDATE_MANUAL) out(t("     " + r.note(), NamedTextColor.DARK_GRAY));
			});
		out(t("  ℹ Estable = con hash oficial, más segura pero puede ir en modo compatibilidad con un Minecraft nuevo. Build = desarrollo, suele traer el soporte nuevo pero sin hash oficial (por eso es manual).", NamedTextColor.DARK_GRAY));
		manualChoices = plugin.manualPairs();
		if (manualChoices.isEmpty()) { out(t("No hay archivos nuevos en manual/ todavía.", NamedTextColor.GRAY)); state = State.MAIN; printMain(); return; }
		state = State.MANUAL_SELECT;
		out(t("\nArchivos listos para instalar (NO verificados contra una fuente oficial):", UpdateDialog.RED));
		for (int i = 0; i < manualChoices.size(); i++) {
			var m = manualChoices.get(i);
			out(t(String.format(" %d) %s  %s → %s   (%s)", i + 1, m.result().name(), m.result().installedVersion(), m.jar().version(), m.jar().path().getFileName()), UpdateDialog.RED));
		}
		out(t("Escribe los números a instalar (ej: 1 2), \"todos\" o 0 para volver:", NamedTextColor.DARK_GRAY));
	}

	private void manualSelect(String in) {
		if (in.equals("0")) { state = State.MAIN; printMain(); return; }
		Set<Integer> idx = new LinkedHashSet<>();
		if (in.equals("todos")) for (int i = 0; i < manualChoices.size(); i++) idx.add(i);
		else for (String p : in.split("\\s+")) {
			try { int n = Integer.parseInt(p); if (n >= 1 && n <= manualChoices.size()) idx.add(n - 1); } catch (NumberFormatException ignored) { }
		}
		if (idx.isEmpty()) { out(t("Ninguna opción válida.", NamedTextColor.RED)); return; }
		List<UpdateDeskPlugin.ManualPair> sel = new ArrayList<>();
		idx.forEach(i -> sel.add(manualChoices.get(i)));
		manualChoices = sel;
		state = State.MANUAL_CONFIRM;
		out(t("\n⚠ Se instalarán estos archivos SIN verificar contra una fuente oficial (se guarda copia del jar actual):", UpdateDialog.RED));
		sel.forEach(m -> out(t(" • " + m.result().name() + "  " + m.result().installedVersion() + " → " + m.jar().version() + "   SHA-256 " + m.jar().sha256().substring(0, 16) + "…", UpdateDialog.RED)));
		out(t("Solo continúa si los descargaste de la página oficial del autor. Escribe SI para confirmar o 0 para cancelar:", UpdateDialog.GOLD));
	}

	private void manualConfirm(String in) {
		if (!in.equals("si") && !in.equals("sí")) { out(t("Cancelado.", NamedTextColor.GRAY)); state = State.MAIN; printMain(); return; }
		out(t("Preparando…", NamedTextColor.GRAY));
		plugin.stageManualAll(manualChoices, outcomes -> {
			boolean any = false;
			for (Installer.Outcome o : outcomes) {
				out(Component.text(o.ok() ? " ✔ " : " ✖ ", o.ok() ? UpdateDialog.GREEN : UpdateDialog.RED).append(t(o.plugin() + ": " + o.message(), NamedTextColor.WHITE)));
				any |= o.ok();
			}
			if (any && state != null) { state = State.RESTART_ASK; askRestart(); } else if (state != null) { state = State.MAIN; printMain(); }
		});
	}

	private void updateSelect(String in) {
		if (in.equals("0")) { state = State.MAIN; printMain(); return; }
		Set<Integer> idx = new LinkedHashSet<>();
		if (in.equals("todos")) { for (int i = 0; i < choices.size(); i++) if (choices.get(i).status() == UpdateResult.Status.UPDATE_AVAILABLE) idx.add(i); }
		else for (String p : in.split("\\s+")) {
			try { int n = Integer.parseInt(p); if (n >= 1 && n <= choices.size()) idx.add(n - 1); } catch (NumberFormatException ignored) { }
		}
		if (idx.isEmpty()) { out(t("Ninguna opción válida. Escribe números de la lista, \"todos\" o 0.", NamedTextColor.RED)); return; }
		List<UpdateResult> sel = new ArrayList<>();
		idx.forEach(i -> sel.add(choices.get(i)));
		choices = sel;
		state = State.UPDATE_CONFIRM;
		out(t("\nSe descargarán desde su fuente oficial (hash verificado) y se guardará una copia de cada jar actual:", NamedTextColor.AQUA));
		sel.forEach(r -> out(t(" • " + r.name() + "  " + r.installedVersion() + " → " + r.latestVersion()
			+ (r.status() == UpdateResult.Status.UPDATE_UNCONFIRMED ? "   ⚠ su autor no declara compatibilidad con este Minecraft" : ""), r.status() == UpdateResult.Status.UPDATE_UNCONFIRMED ? UpdateDialog.ORANGE : NamedTextColor.WHITE)));
		out(t("Escribe SI para confirmar o 0 para cancelar:", NamedTextColor.GOLD));
	}

	private void updateConfirm(String in) {
		if (!in.equals("si") && !in.equals("sí")) { out(t("Cancelado.", NamedTextColor.GRAY)); state = State.MAIN; printMain(); return; }
		List<UpdateResult> sel = choices;
		out(t("Descargando…", NamedTextColor.GRAY));
		plugin.stageAll(sel, outcomes -> {
			boolean any = false;
			for (Installer.Outcome o : outcomes) {
				out(Component.text(o.ok() ? " ✔ " : " ✖ ", o.ok() ? UpdateDialog.GREEN : UpdateDialog.RED).append(t(o.plugin() + ": " + o.message(), NamedTextColor.WHITE)));
				any |= o.ok();
			}
			if (any && state != null) { state = State.RESTART_ASK; askRestart(); } else if (state != null) { state = State.MAIN; printMain(); }
		});
	}

	private void askRestart() {
		out(t("\n¿Reiniciar ahora para aplicarlas? (los jugadores verán una cuenta atrás de " + plugin.restart().countdownSeconds() + " s)", NamedTextColor.AQUA));
		out(t(" 1) Sí, reiniciar con aviso", NamedTextColor.WHITE));
		out(t(" 0) Más tarde", NamedTextColor.GRAY));
	}

	private void restartAsk(String in) {
		if (in.equals("1")) { plugin.restart().start(); state = State.MAIN; out(t("Cuenta atrás iniciada. Escribe «updatedesk cancel» para cancelarla.", NamedTextColor.GOLD)); }
		else { state = State.MAIN; printMain(); }
	}

	private void restartConfirm(String in) {
		if (in.equals("si") || in.equals("sí")) { plugin.restart().start(); state = State.MAIN; out(t("Cuenta atrás iniciada. Escribe «updatedesk cancel» para cancelarla.", NamedTextColor.GOLD)); }
		else { state = State.MAIN; printMain(); }
	}

	private void ignoreSelect(String in) {
		try {
			int n = Integer.parseInt(in.trim());
			if (n == 0) { state = State.MAIN; printMain(); return; }
			if (n >= 1 && n <= choices.size()) {
				plugin.ignore(choices.get(n - 1).name());
				out(t("✔ " + choices.get(n - 1).name() + " se ignorará desde ahora.", NamedTextColor.GREEN));
				state = State.MAIN; printMain(); return;
			}
		} catch (NumberFormatException ignored) { }
		out(t("Número no válido.", NamedTextColor.RED));
	}
}
