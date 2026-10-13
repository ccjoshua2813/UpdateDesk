# UpdateDesk

> 🤖 **Proyecto desarrollado con asistencia de IA.** El código fue escrito en gran parte por IA, bajo la dirección de
> [ccjoshua2813](https://github.com/ccjoshua2813), que diseñó las funciones, tomó las decisiones y probó cada versión
> en el juego. Los commits lo indican con la línea `Assisted-by`.

Gestor de **actualizaciones de plugins** para servidores **Paper 26.2**, con una interfaz nativa de Minecraft (diálogos con botones) y un menú de texto con números para la consola. Pensado para administradores que quieren ver de un vistazo qué plugins van atrasados y actualizarlos **sin descargar nada a ciegas**.

## Qué hace

- **Identifica cada plugin por el hash de su jar** en [Modrinth](https://modrinth.com) y le busca la última versión estable compatible.
- **Fuentes oficiales extra** por nombre de plugin (`sources:` en `config.yml`): GeyserMC, GitHub Releases (con SHA-256), builds de desarrollo de Jenkins (solo informa) y la última versión de SpigotMC (endpoint oficial, solo informa).
- Muestra el estado de cada plugin: ▲ actualización · ⚠ sin confirmar para tu versión de Minecraft · ● al día · ✖ sin versiones · ? no comprobable · ⟳ pendiente de reiniciar.
- **Actualiza con seguridad:** descarga solo desde servidores verificados, comprueba el **hash oficial** y que el jar sea del mismo plugin, guarda **copia del jar anterior** y lo deja en la carpeta `update/` de Paper para aplicarlo al reiniciar. **Nada se instala sin tu confirmación.**
- **Plugins manuales** (SpigotMC, CurseForge, de pago): abre su página oficial y permite instalar el jar que subas a `plugins/UpdateDesk/manual/`, con confirmación en rojo.
- **Reinicio con aviso:** cuenta atrás para los jugadores, cancelable.
- **Silencioso:** revisa en segundo plano y, como mucho, avisa una vez a los operadores al entrar.

## Uso

| Dónde | Cómo |
|---|---|
| Juego (operadores) | `/ud` (alias `/updatedesk`, `/actualizaciones`): diálogo con botones |
| Consola | `updatedesk`: menú con números (1 ver · 2 actualizar · 3 ignorar · 4 reiniciar · 5 manuales) |
| Comandos | `list`, `check`, `update <plugin\|todos>`, `manual`, `reload`, `restart`, `cancel` |

Permiso: `updatedesk.admin` (por defecto, solo operadores).

## Instalación

1. Descarga `UpdateDesk-x.y.z.jar` de las [Releases](../../releases) y ponlo en `plugins/`.
2. Arranca el servidor: se genera `plugins/UpdateDesk/config.yml`.
3. Antes de actualizar plugins importantes, **haz una copia de seguridad** de tu servidor.

Para actualizar UpdateDesk con servidor apagado, borra el jar viejo (deja la carpeta `plugins/UpdateDesk/`) y sube el nuevo.

## Requisitos y límites

- Paper 26.2 y Java 25.
- Los diálogos requieren un cliente de Java reciente. Para jugadores de Bedrock (Geyser) no se ha probado: usa la consola.
- Los plugins que solo están en SpigotMC o CurseForge **no se pueden descargar automáticamente** (esas webs lo bloquean): se actualizan a mano desde la pantalla de plugins manuales.
- Las builds de desarrollo (por ejemplo las de EssentialsX) no tienen hash oficial: solo se informan y se instalan a mano.
- Las expresiones regulares de `sources:` deben ir entre **comillas simples** en el YAML.

## Numeración

Las versiones empiezan en **0.0.1** y siguen como 0.0.x mientras se prueba. El historial anterior a la primera publicación se resume en el [CHANGELOG](CHANGELOG.md).

## Transparencia sobre el uso de IA

El código fue escrito en gran parte por IA, bajo la dirección de ccjoshua2813, que diseñó las funciones, tomó las decisiones y probó cada versión en el juego. Los commits lo indican con la línea `Assisted-by`.

## Licencia

[MIT](LICENSE).
