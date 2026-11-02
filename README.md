# Bloqueador YT Music (Android, sin ROOT)

Abre la app y estás directamente en YouTube Music, sin anuncios, con una barra inferior como la de la
app oficial. Todo se procesa en el teléfono: sin telemetría, sin servidores propios y sin SDKs de
terceros (solo bibliotecas AndroidX de Google).

## Cómo se usa

- **Abrir la app** carga `music.youtube.com` con el bloqueo ya activo. No hay pasos previos.
- **Barra inferior:** Principal · Explorar · Buscar · Biblioteca · Bloqueador. La web de YouTube Music
  no tiene "Samples" (es exclusivo de la app oficial), así que en su lugar va Explorar. Donde la app
  oficial pone "Actualizar" va el botón del bloqueador.
- **Mini-reproductor** justo encima de la barra; al abrir una canción la barra se esconde.
- **Atrás** funciona como en la app oficial: cierra la canción o la búsqueda, retrocede dentro de la
  sección, vuelve a Principal y, en la raíz, manda la app al fondo sin cortar la música.
- **Notificación y pantalla de bloqueo** con título, artista, carátula y play/pausa/siguiente/anterior.
  Si se desconectan los auriculares, la música se pausa.
- **Bloqueador:** estadísticas y un interruptor opcional (apagado por defecto) para bloquear rastreadores
  en todo el teléfono con una VPN local.

## Estructura

```
app/src/main/
├── assets/js/
│   ├── json_prune.js      poda de anuncios del playerResponse (corre en la página)
│   ├── player_hook.js     fetch/XHR, sustituto de navigator.mediaSession, mandos multimedia
│   └── ytm_shell.js       navegación de la barra, búsqueda, CSS y estado de la página
└── java/com/adblocker/ytmusic/
    ├── core/json/         MiniJson: parser y serializador JSON propio (sin librerías)
    ├── domain/            Kotlin puro, probado con JUnit
    │   ├── navigation/    pestañas (YtmTab), orden de atrás (BackNavigator, TabHistory)
    │   ├── player/        filtros del reproductor, telemetría, carátula, metadatos
    │   ├── web/           NavigationPolicy y UrlParts: qué se carga dentro y qué no
    │   └── filter/, usecase/, model/, repository/, network/
    ├── data/
    │   ├── web/           WebView: fábrica, clientes, interceptor, puente y script inyectado
    │   ├── media/         descarga segura de la carátula
    │   ├── blocklist/, dns/, net/
    ├── service/           PlaybackService (MediaSession) y el VpnService opcional
    ├── ui/
    │   ├── MainActivity   única pantalla
    │   ├── shell/         YtmShell, YtmBottomBar, ShellViewModel, PlayerWebController
    │   └── blocker/       hoja "Bloqueador"
    └── di/ServiceLocator  inyección manual (prohibidas las librerías de DI)
```

## Cómo se quitan los anuncios

Los anuncios de YouTube Music viajan en el mismo servidor que la música, así que se quitan en el
reproductor integrado (un WebView propio), con tres capas:

1. **`shouldInterceptRequest`** (y el cliente de service workers, que no pasa por el `WebViewClient`)
   corta peticiones publicitarias (`/pagead/`, `/api/stats/ads`, `/ptracking`, hosts de
   `doubleclick.net`...), los dominios de las listas locales y la medición de la web
   (`log_event`, `qoe`, `csi_204`, `play.google.com/log`), que se contesta en local.
   **No** se corta `/youtubei/v1/att/get` ni `jnn-pa.googleapis.com`: son la verificación anti-bots de
   YouTube y cortarlos rompe la reproducción. Tampoco `watchtime`/`playback`, que alimentan el historial.
2. **Hook en document-start** (`WebViewCompat.addDocumentStartJavaScript`, solo para
   `https://music.youtube.com`): envuelve `fetch` antes de que la web guarde su referencia y, para
   `youtubei/v1/player`, poda el cuerpo dentro de la propia página con `json_prune.js`.
3. **Poda** de `adPlacements`, `adSlots`, `playerAds`, `adBreakHeartbeatParams` y `adParams` en
   cualquier nivel, excepto dentro de las claves protegidas. Las claves salen de Kotlin
   (`PlayerResponseFilter`), que es también la implementación de referencia:
   `JsonPruneScriptTest` pasa las mismas muestras por las dos y exige el mismo JSON.

Invariantes que evitan romper la reproducción, cubiertos por pruebas:

- `streamingData`, `videoDetails`, `playabilityStatus`, `playerConfig`, `captions`,
  `storyboards` y `microformat` no se recorren ni se modifican.
- Si el cuerpo no es JSON válido (error HTTP, respuesta parcial), se devuelve intacto.
- Si no había nada que quitar, se devuelve la cadena original sin reserializar.
- Ante cualquier fallo, la página recibe la respuesta original.

## Seguridad y privacidad

- **Puente limitado por origen:** `addWebMessageListener` solo expone `NativeShell` a documentos de
  `https://music.youtube.com` (ni iframes ni otros sitios). Los mensajes son asíncronos y se validan
  con límites de tamaño y forma (`BridgeMessage`). Sin `addJavascriptInterface`.
- **Navegación confinada** (`NavigationPolicy`): dentro solo `https` hacia YouTube Music y el inicio
  de sesión de Google; otros enlaces se abren en el navegador; `intent:`, `javascript:`, `data:`,
  `file:`... se bloquean aunque el host parezca de YouTube. Las ventanas nuevas no se abren dentro.
- **Sin permisos web:** cámara, micrófono, ubicación y pantalla completa denegados; sin acceso a
  archivos ni a content providers; contenido mixto prohibido.
- **Sin telemetría:** Safe Browsing y métricas del WebView desactivados (`MetricsOptOut`), Attribution
  Reporting desactivado, cookies de terceros bloqueadas, la consola de la página no llega a logcat en
  release. El filtro DNS ya no recurre a un resolutor externo si el sistema no tiene DNS IPv4.
- **Carátula:** solo `https` hacia los CDN de imágenes de Google, sin redirecciones, máximo 1 MB.
- **Robustez:** si el sistema mata el renderer del WebView, la app lo recrea en vez de cerrarse.
- **Release** firmada con clave propia y sin depuración: nadie puede inspeccionar la sesión de
  YouTube con DevTools por USB (la variante debug sí lo permite y por eso se instala aparte).

## Rendimiento

- **Pantalla negra al elegir canción:** el WebView se creaba sin `layoutParams` y Chromium veía
  0 px de alto en las media queries. YouTube Music creía estar en horizontal y abría el reproductor
  en su modo pantalla completa, con la carátula colapsada y los controles ocultos. Con
  `MATCH_PARENT` se ve la página de reproducción normal.
- **Nada bloquea la página:** antes cada `fetch` hacía una llamada síncrona a Kotlin y el cuerpo del
  reproductor (cientos de KB) cruzaba el puente y se reparseaba. Ahora la URL se reconoce en JS y la
  poda la hace V8.
- **Sin sondeos:** el estado de reproducción llega por eventos; la notificación solo se republica si
  cambia la canción o el play/pausa (antes, cada 5 s), y el sistema extrapola la posición.
- **El WebView no se redimensiona en cada fotograma:** la barra aparece y desaparece de golpe y el
  teclado se aplica con su tamaño final.
- **Release con R8** (la variante debug no se optimiza ni se compila de antemano).

## Filtro DNS para todo el teléfono (opcional)

`AdBlockVpnService` enruta **solo** el DNS virtual `10.215.173.2/32`; el resto del tráfico no entra
al túnel. Si el dominio consultado está en las listas, se responde en el dispositivo
(`A -> 0.0.0.0`, `AAAA -> NODATA`, resto `-> NXDOMAIN`); si no, se reenvía al DNS del sistema.

Con el **DNS privado en modo estricto** (DoT con hostname fijado) las consultas van cifradas por el
puerto 853 fuera del túnel y el filtro no las ve. La app avisa en lugar de romper la conexión: el
modo estricto no admite reserva en texto claro. YouTube Music no se ve afectado.

## Compilar e instalar

Requiere JDK 17–21 (Gradle 8.13 no arranca con JDK 25).

```bash
gradlew.bat test
```

```bash
gradlew.bat assembleDebug
```

La variante debug se instala como `com.adblocker.ytmusic.debug` ("YT Music (debug)") y convive con la
release sin tocar su sesión.

```bash
gradlew.bat assembleRelease
```

La release se firma con una clave propia que **no** está en el repositorio. Su ruta y contraseñas se
leen de `local.properties` (ignorado por git):

```properties
release.storeFile=C:/Users/<usuario>/.android/bloqueador-release.jks
release.storePassword=...
release.keyAlias=bloqueador
release.keyPassword=...
```

Guarda una copia de la clave y de la contraseña: sin ellas no se puede actualizar la app instalada
(habría que desinstalarla y volver a iniciar sesión). Sin esas propiedades la release sale sin firmar.

## Alcance real

Esto bloquea los anuncios **dentro de esta app**. La app oficial de YouTube Music no se puede
interceptar: su tráfico va por TLS y no confía en CAs de usuario, así que haría falta root o
parchear el APK. El filtro DNS opcional sí aplica a todo el dispositivo, pero no puede separar los
anuncios de YouTube de su música.
