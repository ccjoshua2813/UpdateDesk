package com.updatedesk;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class UpdateDeskPlugin extends JavaPlugin implements Listener {
	private UpdateChecker checker;
	private StagedStore staged;
	private Installer installer;
	private RestartService restart;
	private ConsoleMenu menu;
	private final Map<UUID, Instant> notified = new HashMap<>();

	UpdateChecker checker() { return checker; }
	StagedStore staged() { return staged; }
	RestartService restart() { return restart; }

	record ManualPair(UpdateResult result, ManualJar jar) {}

	/** Carpeta donde se suben a mano los jars de plugins que no tienen fuente automática. */
	java.nio.file.Path manualDir() { return getDataFolder().toPath().resolve("manual"); }

	Map<String, ManualJar> manualFiles() { return ManualScanner.scan(manualDir(), getConfig().getLong("max-download-mb", 100) * 1024 * 1024); }

	/** Los plugins no comprobables que ya tienen su jar nuevo en manual/. */
	List<ManualPair> manualPairs() {
		UpdateChecker.Report rep = checker.lastReport();
		if (rep == null) return List.of();
		Map<String, ManualJar> files = manualFiles();
		List<ManualPair> out = new ArrayList<>();
		for (UpdateResult r : rep.results())
			if ((r.status() == UpdateResult.Status.NOT_FOUND || r.status() == UpdateResult.Status.UPDATE_MANUAL) && !staged.contains(r.name()) && files.containsKey(r.name().toLowerCase())
				&& !ManualScanner.identical(r.jar(), files.get(r.name().toLowerCase())))
				out.add(new ManualPair(r, files.get(r.name().toLowerCase())));
		return out;
	}

	void stageManualAll(List<ManualPair> pairs, Consumer<List<Installer.Outcome>> done) {
		List<Installer.Outcome> outcomes = new ArrayList<>();
		CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
		for (ManualPair p : pairs) chain = chain.thenCompose(v -> installer.stageManual(p.result(), p.jar())).thenAccept(outcomes::add);
		chain.whenComplete((v, err) -> Bukkit.getScheduler().runTask(this, () -> done.accept(outcomes)));
	}

	private void prepareManualFolder() {
		try {
			java.nio.file.Files.createDirectories(manualDir());
			java.nio.file.Path readme = manualDir().resolve("LEEME.txt");
			if (!java.nio.file.Files.exists(readme)) java.nio.file.Files.writeString(readme,
				"Sube aquí el jar NUEVO de un plugin que UpdateDesk no puede actualizar solo (CMILib, EconomyShopGUI, ServerSigns...).\n"
					+ "No hace falta renombrarlo: UpdateDesk lo identifica por su plugin.yml.\n"
					+ "Luego abre /ud → «Plugins manuales» y pulsa «Instalar» (se guarda copia del jar actual y se aplica al reiniciar).\n"
					+ "OJO: estos archivos NO se verifican contra una fuente oficial; descárgalos solo de la página del autor.\n");
		} catch (java.io.IOException ignored) { }
	}

	@Override
	public void onEnable() {
		saveDefaultConfig();
		prepareManualFolder();
		checker = new UpdateChecker(this);
		staged = new StagedStore(getDataFolder());
		installer = new Installer(this, staged);
		restart = new RestartService(this);
		menu = new ConsoleMenu(this);
		forgetApplied();
		getServer().getPluginManager().registerEvents(menu, this);
		getServer().getPluginManager().registerEvents(this, this);

		getServer().getCommandMap().register("updatedesk", new Command("updatedesk", "Gestor de actualizaciones de plugins", "/updatedesk [check|list|update <plugin|todos>|reload|restart|cancel]", List.of("ud", "actualizaciones")) {
			{ setPermission("updatedesk.admin"); }

			@Override
			public boolean execute(CommandSender sender, String label, String[] args) {
				if (!testPermission(sender)) return true;
				handle(sender, args);
				return true;
			}

			@Override
			public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
				if (args.length == 1) return List.of("check", "list", "update", "manual", "reload", "restart", "cancel").stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
				if (args.length == 2 && args[0].equalsIgnoreCase("update")) {
					List<String> names = new ArrayList<>(checker.updatable(staged).stream().map(UpdateResult::name).toList());
					names.add("todos");
					return names.stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
				}
				return List.of();
			}
		});

		// Revisión silenciosa en segundo plano: primera a los 2 minutos de arrancar y luego cada N horas
		long hours = Math.max(1, getConfig().getLong("check-interval-hours", 12));
		getServer().getScheduler().runTaskTimerAsynchronously(this, this::backgroundCheck, 20L * 120, 20L * 3600 * hours);
	}

	@Override
	public void onDisable() {
		if (restart != null) restart.cancel();
	}

	/** Las actualizaciones preparadas que ya se aplicaron (el jar instalado coincide con el nuevo) dejan de figurar como pendientes. */
	private void forgetApplied() {
		for (InstalledPlugin p : PluginScanner.scan(java.util.Set.of(), this)) {
			var e = staged.all().get(p.name());
			if (e != null && e.newSha1().equalsIgnoreCase(p.sha1())) staged.remove(p.name());
		}
	}

	private void backgroundCheck() {
		checker.check().thenAccept(rep -> {
			long n = checker.updatable(staged).size();
			if (n > 0) getLogger().info(n + " plugin(s) con actualización disponible. Escribe «updatedesk» para ver el menú.");
		}).exceptionally(err -> null);   // en silencio si no hay conexión
	}

	// ----- ayudantes para las pantallas -----

	/** Usa el último resultado si es reciente; si no, consulta Modrinth y entrega el informe en el hilo principal. */
	void withReport(Consumer<UpdateChecker.Report> then) {
		if (checker.isFresh()) { then.accept(checker.lastReport()); return; }
		Bukkit.getConsoleSender().sendMessage(Component.text("UpdateDesk: consultando Modrinth…", NamedTextColor.GRAY));
		checker.check().whenComplete((rep, err) -> Bukkit.getScheduler().runTask(this, () -> {
			if (err != null) Bukkit.getConsoleSender().sendMessage(Component.text("UpdateDesk: no se pudo comprobar (" + rootMessage(err) + ").", NamedTextColor.RED));
			else then.accept(rep);
		}));
	}

	void stageAll(List<UpdateResult> list, Consumer<List<Installer.Outcome>> done) {
		List<Installer.Outcome> outcomes = new ArrayList<>();
		CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
		for (UpdateResult r : list) chain = chain.thenCompose(v -> installer.stage(r)).thenAccept(outcomes::add);
		chain.whenComplete((v, err) -> Bukkit.getScheduler().runTask(this, () -> done.accept(outcomes)));
	}

	void ignore(String pluginName) {
		List<String> ig = new ArrayList<>(getConfig().getStringList("ignore"));
		if (!ig.contains(pluginName)) ig.add(pluginName);
		getConfig().set("ignore", ig);
		saveConfig();
		checker.invalidate();
	}

	private void showDialog(Player p) {
		UpdateChecker.Report rep = checker.lastReport();
		if (rep == null) return;
		p.showDialog(UpdateDialog.build(rep, staged, checker.updatable(staged), new UpdateDialog.Actions() {
			@Override public void updateOne(Player pl, UpdateResult r) {
				Runnable go = () -> stageAll(List.of(r), outs -> { report(pl, outs); showDialog(pl); });
				if (r.status() == UpdateResult.Status.UPDATE_UNCONFIRMED)
					pl.showDialog(UpdateDialog.confirm(r.name() + " " + r.latestVersion() + ": su autor no declara compatibilidad con Minecraft " + Bukkit.getMinecraftVersion()
						+ ". Se guardará una copia del jar actual por si hay que volver atrás. ¿Continuar?", p2 -> go.run()));
				else go.run();
			}
			@Override public void updateAll(Player pl, boolean restartAfter) {
				List<UpdateResult> list = checker.updatable(staged).stream().filter(x -> x.status() == UpdateResult.Status.UPDATE_AVAILABLE).toList();
				if (!restartAfter) { stageAll(list, outs -> { report(pl, outs); showDialog(pl); }); return; }
				pl.showDialog(UpdateDialog.confirm("Se prepararán " + list.size() + " actualizaciones y el servidor se reiniciará en " + restart.countdownSeconds() + " s. ¿Continuar?",
					p2 -> stageAll(list, outs -> { report(p2, outs); if (outs.stream().anyMatch(Installer.Outcome::ok)) restart.start(); })));
			}
			@Override public void restartNow(Player pl) {
				pl.showDialog(UpdateDialog.confirm("El servidor se reiniciará en " + restart.countdownSeconds() + " s para aplicar las actualizaciones. ¿Continuar?", p2 -> restart.start()));
			}
			@Override public void refresh(Player pl) { run(pl, true); }
			@Override public void openManual(Player pl) { showManual(pl); }
		}));
	}

	private void showManual(Player p) {
		UpdateChecker.Report rep = checker.lastReport();
		if (rep == null) return;
		p.showDialog(UpdateDialog.manual(rep, staged, manualFiles(), new UpdateDialog.ManualActions() {
			@Override public void install(Player pl, UpdateResult r, ManualJar m) {
				pl.showDialog(UpdateDialog.confirmRed("Instalación manual: " + r.name(),
					"⚠ Este archivo NO está verificado contra una fuente oficial.\n\n" + r.name() + ": " + r.installedVersion() + " → " + m.version()
						+ "\nArchivo: " + m.path().getFileName() + "\nSHA-256: " + m.sha256().substring(0, 16) + "…\n\nSolo instálalo si lo descargaste de la página oficial del autor. Se guardará copia del jar actual.",
					p2 -> stageManualAll(List.of(new ManualPair(r, m)), outs -> { report(p2, outs); showManual(p2); })));
			}
			@Override public void installAll(Player pl, boolean restartAfter) {
				List<ManualPair> pairs = manualPairs();
				if (pairs.isEmpty()) { showManual(pl); return; }
				String names = pairs.stream().map(x -> x.result().name() + " " + x.jar().version()).collect(Collectors.joining(", "));
				pl.showDialog(UpdateDialog.confirmRed("Instalación manual",
					"⚠ Estos archivos NO están verificados contra una fuente oficial:\n" + names + (restartAfter ? "\n\nDespués el servidor se reiniciará en " + restart.countdownSeconds() + " s." : "")
						+ "\n\nSe guardará copia de cada jar actual.",
					p2 -> stageManualAll(pairs, outs -> { report(p2, outs); if (restartAfter && outs.stream().anyMatch(Installer.Outcome::ok)) restart.start(); else showManual(p2); })));
			}
			@Override public void back(Player pl) { showDialog(pl); }
		}));
	}

	private static void report(Player p, List<Installer.Outcome> outs) {
		for (Installer.Outcome o : outs)
			p.sendMessage(Component.text(o.ok() ? "✔ " : "✖ ", o.ok() ? UpdateDialog.GREEN : UpdateDialog.RED).append(Component.text(o.plugin() + ": " + o.message(), NamedTextColor.WHITE)));
	}

	/** Comprueba (o reutiliza el último resultado) y abre el diálogo para un jugador. */
	private void run(Player p, boolean force) {
		if (!force && checker.isFresh()) { showDialog(p); return; }
		p.sendMessage(Component.text("UpdateDesk: consultando Modrinth…", NamedTextColor.GRAY));
		checker.check().whenComplete((rep, err) -> Bukkit.getScheduler().runTask(this, () -> {
			if (err != null) p.sendMessage(Component.text("UpdateDesk: no se pudo comprobar (" + rootMessage(err) + ").", NamedTextColor.RED));
			else showDialog(p);
		}));
	}

	// ----- comandos -----

	private void handle(CommandSender sender, String[] args) {
		String sub = args.length > 0 ? args[0].toLowerCase() : "";
		switch (sub) {
			case "" -> { if (sender instanceof Player p) run(p, false); else menu.open(); }
			case "check" -> { checker.invalidate(); if (sender instanceof Player p) run(p, true); else withConsoleList(); }
			case "list" -> withConsoleList();
			case "update" -> {
				if (args.length < 2) { sender.sendMessage(Component.text("Uso: /updatedesk update <plugin|todos>", NamedTextColor.RED)); return; }
				withReport(rep -> {
					List<UpdateResult> list = checker.updatable(staged).stream().filter(r -> args[1].equalsIgnoreCase("todos") ? r.status() == UpdateResult.Status.UPDATE_AVAILABLE : r.name().equalsIgnoreCase(args[1])).toList();
					if (list.isEmpty()) { sender.sendMessage(Component.text("No hay nada que actualizar con ese nombre.", NamedTextColor.GRAY)); return; }
					stageAll(list, outs -> outs.forEach(o -> sender.sendMessage(Component.text(o.ok() ? "✔ " : "✖ ", o.ok() ? UpdateDialog.GREEN : UpdateDialog.RED).append(Component.text(o.plugin() + ": " + o.message(), NamedTextColor.WHITE)))));
				});
			}
			case "manual" -> { if (sender instanceof Player p) { if (checker.lastReport() == null) run(p, true); else showManual(p); } else menu.openManual(); }
			case "reload" -> { reloadConfig(); checker.invalidate(); sender.sendMessage(Component.text("UpdateDesk: configuración recargada.", NamedTextColor.GREEN)); }
			case "restart" -> { restart.start(); sender.sendMessage(Component.text("Cuenta atrás iniciada. «updatedesk cancel» la cancela.", NamedTextColor.GOLD)); }
			case "cancel" -> sender.sendMessage(Component.text(restart.cancel() ? "Reinicio cancelado." : "No hay ningún reinicio en curso.", NamedTextColor.GRAY));
			default -> sender.sendMessage(Component.text("Uso: /updatedesk [check|list|update <plugin|todos>|reload|restart|cancel]", NamedTextColor.RED));
		}
	}

	private void withConsoleList() {
		withReport(rep -> {
			ConsoleCommandSender c = Bukkit.getConsoleSender();
			c.sendMessage(UpdateDialog.summary(rep, staged));
			rep.results().stream().sorted((a, b) -> Integer.compare(a.status().ordinal(), b.status().ordinal())).forEach(r -> c.sendMessage(UpdateDialog.line(r, staged)));
		});
	}

	// ----- aviso discreto a operadores -----

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		Player p = e.getPlayer();
		if (!getConfig().getBoolean("notify-on-join", true) || !p.hasPermission("updatedesk.admin")) return;
		Instant last = notified.get(p.getUniqueId());
		long cooldown = getConfig().getLong("notify-cooldown-hours", 12);
		if (last != null && last.plusSeconds(cooldown * 3600).isAfter(Instant.now())) return;
		Bukkit.getScheduler().runTaskLater(this, () -> {
			if (!p.isOnline()) return;
			int n = (int) checker.updatable(staged).stream().filter(x -> x.status() == UpdateResult.Status.UPDATE_AVAILABLE).count(), pending = staged.all().size();
			if (n == 0 && pending == 0) return;
			notified.put(p.getUniqueId(), Instant.now());
			StringBuilder sb = new StringBuilder("UpdateDesk · ");
			if (n > 0) sb.append(n).append(n == 1 ? " plugin con actualización" : " plugins con actualización");
			if (n > 0 && pending > 0) sb.append(" · ");
			if (pending > 0) sb.append(pending).append(" pendiente(s) de reiniciar");
			p.sendMessage(Component.text(sb + ". ", UpdateDialog.GOLD).append(Component.text("[Ver]", UpdateDialog.AQUA).clickEvent(ClickEvent.runCommand("/updatedesk"))));
		}, 80L);
	}

	private static String rootMessage(Throwable t) {
		while (t.getCause() != null) t = t.getCause();
		return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
	}
}
