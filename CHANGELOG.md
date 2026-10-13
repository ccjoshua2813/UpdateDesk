# UpdateDesk — historial de versiones

Gestor de actualizaciones de plugins para Paper 26.2 con interfaz nativa (diálogos de Minecraft). Versiones 0.0.x mientras se prueba; la numeración empieza en 0.0.1 con la primera publicación.

## 0.0.1
Primera publicación en GitHub. Reúne todo lo desarrollado y probado hasta ahora (antes con numeración interna 0.1.0–0.4.1, ver más abajo):
- Identificación de plugins por el hash de su jar en **Modrinth**, y fuentes oficiales por nombre de plugin en `sources:`: **GeyserMC**, **GitHub Releases** (con SHA-256), builds de **Jenkins** (solo informan) y la última versión de **SpigotMC** (endpoint oficial, solo informa).
- Estados ▲ actualización · ⚠ sin confirmar · ● al día · ✖ sin versiones · ? no comprobable · ⟳ pendiente de reiniciar. Canal estable por defecto (`allow-prerelease`, `assume-compatible`).
- Actualizar con seguridad: solo desde servidores verificados, hash oficial, comprobación del nombre del plugin dentro del jar, copia del jar anterior y aplicación al reiniciar por la carpeta `update/` de Paper. Nada se instala sin confirmación.
- **Plugins manuales**: pantalla con enlace a su página oficial y carpeta `plugins/UpdateDesk/manual/` para instalar un jar subido a mano (confirmación en rojo, sin verificación oficial).
- Menú numerado en la consola (`updatedesk`), diálogo con botones en el juego (`/ud`), reinicio con cuenta atrás cancelable, revisión silenciosa en segundo plano y un único aviso a operadores al entrar.
- Fuentes de GitHub de los plugins propios (`UpdateDesk` y `QuickMenu`) en la configuración por defecto.

## Historial interno (numeración anterior, antes de publicar)
## 0.4.1
- Los botones de la pantalla de plugins manuales ya no se salen por el borde en pantallas pequeñas: ancho máximo menor y etiquetas largas recortadas (la versión completa queda en la descripción emergente).
- Si el jar de `manual/` es idéntico (mismo SHA-256) al instalado, se marca como «nada que instalar» y no se ofrece (tampoco entra en «Instalar todo»).
- (Configuración por defecto) RemmyChat apunta a las releases de `NoximityGaming/RemmyChat` en GitHub (el proyecto está archivado y sus enlaces de Hangar y Modrinth están rotos) y se sigue por GitHub con hash SHA-256.
- Configuración por defecto: RemmyChat apunta a las releases de `NoximityGaming/RemmyChat` en GitHub (el proyecto está archivado y sus enlaces de Hangar y Modrinth están rotos) y se sigue por GitHub con hash SHA-256.

## 0.4.0
- **Plugins manuales:** para los que no tienen fuente automática (CMILib, EconomyShopGUI, ServerSigns…). Nueva pantalla «⚠ Plugins manuales» (botón en el diálogo, `/ud manual`, opción 5 del menú de consola): leyenda en rojo con los pasos, botón **Abrir página** (enlace clicable a su página oficial) y botón **Instalar** para el jar que subas a `plugins/UpdateDesk/manual/`.
- Los jars de `manual/` se identifican por su `plugin.yml` (no hace falta renombrarlos). Instalar pide una **confirmación en rojo** con nombre, versiones y SHA-256, avisando de que el archivo no está verificado contra una fuente oficial; después comprueba que sea del mismo plugin, guarda copia del jar actual, lo deja en `plugins/update/` con el nombre correcto y mueve el archivo a `manual/aplicados/`. También hay «Instalar todo lo subido» e «Instalar y reiniciar».
- **Enlaces de los plugins:** sección `links:` del `config.yml`; si falta, se usa el sitio web declarado en el `plugin.yml`. UpdateDesk no inventa URLs.
- **Versiones de desarrollo:** una versión dev/SNAPSHOT instalada que ya iguala o supera a la publicada ya no se ofrece como actualización (se compara el número de compilación, p. ej. b141, o el número de versión). Cubierto con pruebas (`VersionCompare`).
- **Botones sin cortar:** el ancho se ajusta al texto.
- **Builds de desarrollo (Jenkins):** nueva fuente `jenkins:URL-del-job:regex` (solo `ci.ender.zone`). Como Jenkins no publica hash, **solo informa**: el plugin aparece en «Plugins manuales» con la build disponible, un texto que explica estable frente a build y la última estable (si hay otra fuente), y se instala subiendo el jar a `manual/`. Un plugin puede tener varias fuentes (lista en `sources:`); EssentialsX viene con jenkins + github por defecto.
- **Leyenda estable/build** en la pantalla de plugins manuales y en la opción 5 de la consola: la estable tiene hash oficial pero puede ir en modo compatibilidad con un Minecraft nuevo; la build suele traer el soporte nuevo pero sin hash oficial.
- **Enlaces verificados** en `links:` (README de Zrips, wiki oficial de EconomyShopGUI, páginas de Bukkit y Hangar): CMILib, Jobs, EconomyShopGUI, ServerSigns y RemmyChat. SinglePlayerSleep queda sin enlace verificado (se usa el sitio que declare el plugin).
- `VersionCompare.newer` (versión y numeración dev+N; la estable gana a su dev), con pruebas.
- Las expresiones regulares de la configuración por defecto van entre comillas simples: con comillas dobles una barra invertida deja el YAML inválido.

## 0.3.1
- El pie del diálogo ya no dice «Solo desde Modrinth»: ahora indica las fuentes oficiales (Modrinth, GeyserMC, GitHub).

## 0.3.0
- **Estado «⚠ sin confirmar»:** si la última versión de un plugin está en Modrinth pero su autor no declara tu versión de Minecraft, ya no se marca como incompatible: se ofrece con un aviso y una confirmación extra (se guarda copia del jar actual). `todos` solo incluye las confirmadas. La lista `assume-compatible` trata como confirmados los plugins que sabes que funcionan.
- **Canal estable por defecto:** solo se ofrecen versiones `release`; los plugins listados en `allow-prerelease` también aceptan beta/alpha. La decisión se toma con todas las versiones del proyecto, no con una consulta de «última versión».
- **Fuentes oficiales nuevas** (sección `sources:` por nombre de plugin): **GeyserMC** (`geysermc:geyser`, `geysermc:floodgate`, hash SHA-256 oficial) y **GitHub Releases** (`github:propietario/repo:regex`, solo si GitHub publica el SHA-256 del archivo). Se compara el hash del jar instalado con el del último publicado.
- El instalador permite descargar solo desde los servidores de cada fuente (Modrinth, GeyserMC, GitHub) y comprueba el hash oficial con el algoritmo de esa fuente, el nombre del plugin dentro del jar y la copia de seguridad, igual que antes.
- Estado «descarga manual» para versiones nuevas sin hash oficial; los mensajes indican el motivo cuando una fuente falla.
- Comando `updatedesk reload` para recargar `config.yml` sin reiniciar.

## 0.2.0
- **Actualizar de verdad, con seguridad:** descarga solo desde `cdn.modrinth.com` (HTTPS, sin redirecciones a otros sitios), comprueba el hash SHA-512 oficial, comprueba que el jar sea del mismo plugin (nombre dentro de `plugin.yml`), guarda una copia del jar actual en `plugins/UpdateDesk/backups/<plugin>/` (se conservan `backups-keep`) y deja el nuevo en `plugins/update/` con el nombre del jar actual para que Paper lo aplique al reiniciar. Nada se instala sin que un administrador lo confirme.
- **Menú numerado en la consola:** `updatedesk` abre un menú (1 ver actualizaciones, 2 actualizar, 3 ignorar, 4 reiniciar); escribes un número y Enter. Pide confirmación (`SI`) antes de descargar y ofrece reiniciar. Solo captura números y respuestas del menú; cualquier otro comando pasa al servidor. Se cierra solo tras 2 minutos sin actividad.
- **Diálogo en el juego con botones:** Actualizar <plugin>, Actualizar todo, Actualizar y reiniciar (con confirmación), Reiniciar ahora y Comprobar de nuevo. Los plugins ya preparados aparecen como «⟳ se aplica al reiniciar».
- **Reinicio con aviso:** cuenta atrás visible para los jugadores (`restart.countdown-seconds`), cancelable con `updatedesk cancel`. Apaga el servidor (el hosting debe volver a iniciarlo) o ejecuta el comando de `restart.command`.
- **Modo silencioso:** revisión periódica en segundo plano (`check-interval-hours`) que solo escribe una línea en la consola si hay algo; al entrar un administrador, un único aviso breve con enlace `[Ver]` (`notify-on-join`, con enfriamiento).
- Comandos directos: `updatedesk list`, `updatedesk update <plugin|todos>`, `updatedesk restart`, `updatedesk cancel`.
- Las preparadas que ya se aplicaron se dan de baja solas al arrancar (se compara el hash del jar instalado).

## 0.1.0
- `/updatedesk` (alias `/ud`, `/actualizaciones`; permiso `updatedesk.admin`, por defecto solo operadores). Para jugadores abre un diálogo nativo; en la consola imprime el informe.
- Identifica cada plugin instalado por el SHA-1 de su jar en Modrinth y busca la última versión compatible con el Minecraft y el cargador del servidor (paper, spigot, bukkit).
- Estados: ▲ actualización disponible · ● al día · ✖ sin versión compatible · ? no comprobable (no está en Modrinth).
- Solo informa: **no descarga ni instala nada**. Resultado en caché `cache-minutes` y lista `ignore` en `config.yml`.
