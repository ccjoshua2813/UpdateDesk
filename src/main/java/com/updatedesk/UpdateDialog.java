package com.updatedesk;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Pantallas nativas (diálogos de Minecraft) con el estado de los plugins y los botones para actualizar. */
final class UpdateDialog {
	static final TextColor GREEN = TextColor.color(0x5CD65C), GOLD = TextColor.color(0xF2C94C), RED = TextColor.color(0xE5645C), ORANGE = TextColor.color(0xF2994A), GRAY = NamedTextColor.GRAY, AQUA = TextColor.color(0x6CC6E8);
	private static final ClickCallback.Options OPTS = ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).lifetime(Duration.ofMinutes(10)).build();

	/** Lo que hacen los botones. */
	interface Actions {
		void updateOne(Player p, UpdateResult r);
		void updateAll(Player p, boolean restartAfter);
		void restartNow(Player p);
		void refresh(Player p);
		void openManual(Player p);
	}

	/** Lo que hacen los botones de la pantalla de instalación manual. */
	interface ManualActions {
		void install(Player p, UpdateResult r, ManualJar m);
		void installAll(Player p, boolean restartAfter);
		void back(Player p);
	}

	private UpdateDialog() {}

	static Component line(UpdateResult r) { return line(r, null); }

	static Component line(UpdateResult r, StagedStore staged) {
		if (staged != null && staged.contains(r.name())) {
			var e = staged.all().get(r.name());
			return Component.text("⟳ ", AQUA).append(Component.text(r.name(), NamedTextColor.WHITE))
				.append(Component.text("  " + e.from() + " → " + e.to() + " · se aplica al reiniciar", AQUA));
		}
		Component dot, text;
		switch (r.status()) {
			case UPDATE_AVAILABLE -> {
				dot = Component.text("▲ ", GOLD);
				String kind = "release".equals(r.versionType()) ? "" : " (" + r.versionType() + ")";
				text = Component.text(r.name(), NamedTextColor.WHITE).append(Component.text("  " + r.installedVersion() + " → ", GRAY))
					.append(Component.text(r.latestVersion() + kind, GOLD));
			}
			case UPDATE_UNCONFIRMED -> {
				dot = Component.text("⚠ ", ORANGE);
				text = Component.text(r.name(), NamedTextColor.WHITE).append(Component.text("  " + r.installedVersion() + " → ", GRAY))
					.append(Component.text(r.latestVersion(), ORANGE)).append(Component.text(" · sin confirmar para este Minecraft", GRAY));
			}
			case UPDATE_MANUAL -> {
				dot = Component.text("▲ ", GOLD);
				text = Component.text(r.name(), NamedTextColor.WHITE).append(Component.text("  " + r.installedVersion() + " → ", GRAY))
					.append(Component.text(r.latestVersion(), GOLD)).append(Component.text(" · " + (r.note() != null ? r.note() : "descarga manual"), GRAY));
			}
			case UP_TO_DATE -> {
				dot = Component.text("● ", GREEN);
				text = Component.text(r.name(), NamedTextColor.WHITE).append(Component.text("  " + r.installedVersion(), GRAY));
			}
			case NO_COMPATIBLE -> {
				dot = Component.text("✖ ", RED);
				text = Component.text(r.name(), NamedTextColor.WHITE).append(Component.text("  " + r.installedVersion() + " · sin versiones para este tipo de servidor", GRAY));
			}
			default -> {
				dot = Component.text("? ", GRAY);
				text = Component.text(r.name(), NamedTextColor.GRAY).append(Component.text("  " + r.installedVersion() + " · " + (r.note() != null ? r.note() : "no está en Modrinth"), NamedTextColor.DARK_GRAY));
			}
		}
		return dot.append(text);
	}

	static Component summary(UpdateChecker.Report rep, StagedStore staged) {
		long pending = rep.results().stream().filter(r -> staged.contains(r.name())).count();
		long confirmed = rep.results().stream().filter(r -> r.status() == UpdateResult.Status.UPDATE_AVAILABLE && !staged.contains(r.name())).count();
		long unconfirmed = rep.results().stream().filter(r -> r.status() == UpdateResult.Status.UPDATE_UNCONFIRMED && !staged.contains(r.name())).count();
		long manual = rep.count(UpdateResult.Status.UPDATE_MANUAL);
		var b = Component.text()
			.append(Component.text(rep.results().size() + " plugins revisados\n", NamedTextColor.WHITE))
			.append(Component.text("▲ " + (confirmed + manual) + " con actualización   ", GOLD))
			.append(Component.text("⚠ " + unconfirmed + " sin confirmar   ", ORANGE))
			.append(Component.text("● " + rep.count(UpdateResult.Status.UP_TO_DATE) + " al día\n", GREEN))
			.append(Component.text("✖ " + rep.count(UpdateResult.Status.NO_COMPATIBLE) + " sin versiones   ", RED))
			.append(Component.text("? " + rep.count(UpdateResult.Status.NOT_FOUND) + " no comprobables", GRAY));
		if (pending > 0) b.append(Component.text("\n⟳ " + pending + " pendientes de reiniciar", AQUA));
		return b.build();
	}

	/** Ancho del botón según su texto, para que los nombres largos no se corten. */
	static int widthFor(String label) { return Math.max(150, Math.min(200, 14 + shorten(label).length() * 8)); }

	/** Recorta etiquetas largas (la versión completa queda en la descripción emergente del botón). */
	static String shorten(String label) { return label.length() <= 26 ? label : label.substring(0, 25) + "…"; }

	private static ActionButton button(String label, int width, String tooltip, Consumer<Player> run) { return button(label, NamedTextColor.WHITE, width, tooltip, run); }

	private static ActionButton button(String label, TextColor color, int width, String tooltip, Consumer<Player> run) {
		return ActionButton.create(Component.text(shorten(label), color), tooltip == null ? null : Component.text(tooltip), width,
			DialogAction.customClick((view, audience) -> { if (audience instanceof Player p) run.accept(p); }, OPTS));
	}

	static Dialog build(UpdateChecker.Report rep, StagedStore staged, List<UpdateResult> updatable, Actions a) {
		List<DialogBody> body = new ArrayList<>();
		body.add(DialogBody.plainMessage(summary(rep, staged), 320));
		rep.results().stream().sorted((x, y) -> Integer.compare(x.status().ordinal(), y.status().ordinal()))
			.forEach(r -> body.add(DialogBody.plainMessage(line(r, staged), 320)));
		body.add(DialogBody.plainMessage(Component.text("Solo desde fuentes oficiales (Modrinth, GeyserMC, GitHub), con hash verificado y copia del jar anterior.", NamedTextColor.DARK_GRAY).decorate(TextDecoration.ITALIC), 320));

		List<ActionButton> actions = new ArrayList<>();
		for (UpdateResult r : updatable.stream().limit(10).toList()) {
			boolean unc = r.status() == UpdateResult.Status.UPDATE_UNCONFIRMED;
			actions.add(button((unc ? "⚠ " : "") + "Actualizar " + r.name(), widthFor("⚠ Actualizar " + r.name()), r.installedVersion() + " → " + r.latestVersion() + (unc ? " (sin confirmar para este Minecraft)" : ""), p -> a.updateOne(p, r)));
		}
		long confirmed = updatable.stream().filter(r -> r.status() == UpdateResult.Status.UPDATE_AVAILABLE).count();
		if (confirmed > 1) actions.add(button("Actualizar todo (" + confirmed + ")", 150, "Prepara las actualizaciones confirmadas (no incluye las ⚠)", p -> a.updateAll(p, false)));
		if (confirmed > 0) actions.add(button("Actualizar y reiniciar", 150, "Prepara las confirmadas y reinicia con cuenta atrás", p -> a.updateAll(p, true)));
		if (!staged.isEmpty()) actions.add(button("Reiniciar ahora", 150, "Aplica lo ya preparado", a::restartNow));
		long manual = rep.results().stream().filter(r -> (r.status() == UpdateResult.Status.NOT_FOUND || r.status() == UpdateResult.Status.UPDATE_MANUAL) && !staged.contains(r.name())).count();
		if (manual > 0) actions.add(button("⚠ Plugins manuales (" + manual + ")", RED, 190, "Se actualizan a mano: abre su página y sube el jar a la carpeta manual/", a::openManual));
		actions.add(button("Comprobar de nuevo", 150, "Vuelve a consultar", a::refresh));
		ActionButton close = ActionButton.create(Component.text("Cerrar"), null, 100, null);

		return Dialog.create(b -> b.empty()
			.base(DialogBase.builder(Component.text("UpdateDesk · Plugins")).body(body).canCloseWithEscape(true).build())
			.type(DialogType.multiAction(actions, close, 2)));
	}

	/** Pantalla de confirmación Sí/No antes de una acción que reinicia el servidor. */
	static Dialog confirm(String message, Consumer<Player> onYes) {
		ActionButton yes = button("Sí, continuar", 120, null, onYes);
		ActionButton no = ActionButton.create(Component.text("Cancelar"), null, 120, null);
		return Dialog.create(b -> b.empty()
			.base(DialogBase.builder(Component.text("Confirmar")).body(List.of(DialogBody.plainMessage(Component.text(message, NamedTextColor.WHITE), 300))).canCloseWithEscape(true).build())
			.type(DialogType.confirmation(yes, no)));
	}

	/** Confirmación con el mensaje en rojo, para acciones sin fuente verificada. */
	static Dialog confirmRed(String title, String message, Consumer<Player> onYes) {
		ActionButton yes = button("Sí, instalar", RED, 130, "Instala este archivo bajo tu responsabilidad", onYes);
		ActionButton no = ActionButton.create(Component.text("Cancelar"), null, 120, null);
		return Dialog.create(b -> b.empty()
			.base(DialogBase.builder(Component.text(title, RED)).body(List.of(DialogBody.plainMessage(Component.text(message, RED), 320))).canCloseWithEscape(true).build())
			.type(DialogType.confirmation(yes, no)));
	}

	/** Pantalla de plugins que no se pueden actualizar solos: enlace a su página y botón para instalar el jar subido a manual/. */
	static Dialog manual(UpdateChecker.Report rep, StagedStore staged, Map<String, ManualJar> files, ManualActions a) {
		List<UpdateResult> list = rep.results().stream()
			.filter(r -> (r.status() == UpdateResult.Status.NOT_FOUND || r.status() == UpdateResult.Status.UPDATE_MANUAL) && !staged.contains(r.name())).toList();
		List<DialogBody> body = new ArrayList<>();
		body.add(DialogBody.plainMessage(Component.text("⚠ Instalación manual\n", RED).append(Component.text(
			"Estos plugins no se pueden actualizar automáticamente (su fuente no permite descargas verificadas).\n"
				+ "1) Pulsa «Abrir página» y descarga el jar nuevo desde ahí.\n"
				+ "2) Súbelo a la carpeta plugins/UpdateDesk/manual/ del servidor (desde el panel).\n"
				+ "3) Vuelve aquí y pulsa «Instalar». Al instalar o instalar y reiniciar se guarda copia del jar actual.\n"
				+ "Los archivos ya instalados se mueven a manual/aplicados/.", NamedTextColor.WHITE)), 320));
		body.add(DialogBody.plainMessage(Component.text("ℹ Estable o build: la versión estable tiene hash oficial y es más segura, pero puede ir con retraso y entrar en modo compatibilidad con un Minecraft nuevo. "
			+ "Las builds de desarrollo suelen traer el soporte nuevo, pero no tienen hash oficial: por eso se instalan a mano y con confirmación.", NamedTextColor.GRAY), 320));
		List<ActionButton> actions = new ArrayList<>();
		int ready = 0;
		for (UpdateResult r : list) {
			ManualJar m = files.get(r.name().toLowerCase());
			boolean same = m != null && ManualScanner.identical(r.jar(), m);
			Component state = m == null ? Component.text("  sin archivo nuevo en manual/", NamedTextColor.DARK_GRAY)
				: same ? Component.text("  el archivo de manual/ es idéntico al instalado (nada que instalar)", NamedTextColor.DARK_GRAY)
				: Component.text("  archivo listo: " + m.version(), RED);
			body.add(DialogBody.plainMessage(Component.text("? ", GRAY).append(Component.text(r.name(), NamedTextColor.WHITE)).append(Component.text("  " + r.installedVersion(), GRAY)).append(r.note() != null && r.status() == UpdateResult.Status.UPDATE_MANUAL ? Component.text(" → " + r.latestVersion(), GOLD) : Component.empty()).append(state), 320));
			if (r.note() != null && r.status() == UpdateResult.Status.UPDATE_MANUAL) body.add(DialogBody.plainMessage(Component.text("   " + r.note(), NamedTextColor.DARK_GRAY), 320));
			if (r.url() != null && r.url().startsWith("http"))
				actions.add(ActionButton.create(Component.text(shorten("Abrir página: " + r.name()), NamedTextColor.WHITE), Component.text(r.url()), widthFor("Abrir página: " + r.name()), DialogAction.staticAction(ClickEvent.openUrl(r.url()))));
			if (m != null && !same) {
				ready++;
				actions.add(button("Instalar " + r.name() + " " + m.version(), RED, widthFor("Instalar " + r.name() + " " + m.version()), r.installedVersion() + " → " + m.version() + " · archivo subido por ti, sin verificar contra una fuente oficial", p -> a.install(p, r, m)));
			}
		}
		if (ready > 1) actions.add(button("Instalar todo lo subido (" + ready + ")", RED, 200, "Prepara todos los archivos de manual/", p -> a.installAll(p, false)));
		if (ready > 0) actions.add(button("Instalar y reiniciar", RED, 190, "Prepara lo subido y reinicia con cuenta atrás", p -> a.installAll(p, true)));
		actions.add(button("Volver", 120, null, a::back));
		ActionButton close = ActionButton.create(Component.text("Cerrar"), null, 100, null);
		return Dialog.create(b -> b.empty()
			.base(DialogBase.builder(Component.text("UpdateDesk · Plugins manuales", RED)).body(body).canCloseWithEscape(true).build())
			.type(DialogType.multiAction(actions, close, 2)));
	}
}
