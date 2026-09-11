# Bloqueador de anuncios (Android, sin ROOT)

Filtrado DNS local mediante `VpnService`. Sin telemetria, sin servidores, sin SDKs de terceros.

## Estructura

```
app/src/main/java/com/adblocker/ytmusic/
├── core/json/         MiniJson: parser y serializador JSON propio (sin librerias)
├── domain/            # Kotlin puro: modelos, repositorios (interfaz) y casos de uso
│   ├── filter/DomainBlocklist.kt        coincidencia por sufijo + excepciones @@
│   ├── player/PlayerResponseFilter.kt   saneado de youtubei/v1/player
│   ├── player/PlayerRequestMatcher.kt   clasificacion de URLs del reproductor
│   └── usecase/                         EvaluateDomain + FilterPlayerResponse
├── data/
│   ├── blocklist/     lectura y parseo de assets (hosts / dominio / @@excepcion)
│   ├── dns/           parser de preguntas DNS y constructor de respuestas de bloqueo
│   ├── net/           codec IPv4 + UDP (cabeceras y checksums)
│   └── web/           WebView filtrado + puente JS (AdBlockBridge)
├── service/           VpnService, bucle del tunel y notificacion en primer plano
├── ui/                Compose + ViewModel (MVVM), incluye ui/player
└── di/ServiceLocator  inyeccion manual (prohibidas las librerias de DI)
```

Reglas en `app/src/main/assets/blocklists/*.txt`, empaquetadas en el APK.

## Como funciona

1. `Builder` enruta **solo** el DNS virtual `10.215.173.2/32`; el resto del trafico no entra al tunel.
2. Cada paquete se desempaqueta (`Ipv4UdpCodec`) y se lee la pregunta DNS (`DnsMessage`).
3. Si el dominio esta bloqueado se responde en el dispositivo: `A -> 0.0.0.0`, `AAAA -> NODATA`,
   resto `-> NXDOMAIN`. Si no, se reenvia al DNS del sistema por un socket `protect()`.

## DNS privado (DoT)

El filtro solo ve consultas en UDP/53. `PrivateDnsEvaluator` clasifica el estado del sistema
leyendo `LinkProperties` de las redes fisicas (las VPN se ignoran: la del propio tunel
falsearia el diagnostico):

| Modo | `privateDnsServerName` | Efecto |
|---|---|---|
| Desactivado | `null`, DoT inactivo | El filtro ve todo |
| Automatico | `null`, DoT activo | El sondeo DoT contra el DNS virtual no responde y el sistema cae a texto claro: el filtro sigue viendo todo |
| **Estricto** | hostname fijado | Las consultas van por TLS al puerto 853 fuera del tunel: **el filtro no las ve** |

En modo estricto la app **avisa en lugar de romper**: no existe forma de recuperar la visibilidad.
Cortar el puerto 853 dejaria el dispositivo sin resolucion, porque el modo estricto no admite
reserva en texto claro (Android muestra "Private DNS server cannot be accessed"). La tarjeta de
estado pasa a "Filtro DNS sin efecto" con punto ambar y ofrece un acceso directo a los ajustes.
El reproductor integrado no se ve afectado: filtra en la capa HTTP, no en DNS.

## Pantalla principal

`MainActivity` -> `FilterScreen` (Compose) muestra en un solo estado las dos capas:

- Estado del filtro DNS, con arranque/parada y el dialogo de consentimiento de VPN del sistema.
- Tarjeta del reproductor integrado, con acceso a `PlayerActivity` y sus contadores.
- Estadisticas DNS (inspeccionadas, bloqueadas, ratio) y listas locales cargadas.

`FilterViewModel` no depende de Android: recibe `BlocklistRepository`, `FilterController`
(puerto sobre el `VpnService`) y `FilterPlayerResponseUseCase`, asi que su estado se prueba
con JUnit usando dobles.

## Comandos

```bash
gradlew.bat test
```

```bash
gradlew.bat assembleDebug
```

## Modulo 2: filtrado de `youtubei/v1/player`

Los anuncios de YouTube Music viajan en el mismo host que la musica, asi que el DNS no los ve.
Se filtran en el cuerpo de la respuesta del reproductor, dentro del WebView propio
(`PlayerActivity` -> `music.youtube.com`), con tres capas:

1. **`shouldInterceptRequest`** corta peticiones puramente publicitarias (`/pagead/`,
   `/api/stats/ads`, `/ptracking`, hosts de `doubleclick.net`...) y los dominios de la lista.
2. **Hook JS** (`assets/js/player_hook.js`) envuelve `fetch` y `XMLHttpRequest`; solo para
   `youtubei/v1/player` pasa el cuerpo por `AdBlockBridge` y devuelve la version saneada.
   El cuerpo va en un POST, por lo que este es el unico punto donde se puede reescribir.
3. **`PlayerResponseFilter`** (Kotlin puro) elimina `adPlacements`, `adSlots`, `playerAds`,
   `adBreakHeartbeatParams` y `adParams` en cualquier nivel del JSON.

Invariantes que evitan romper la reproduccion, cubiertos por pruebas:

- `streamingData`, `videoDetails`, `playabilityStatus`, `playerConfig`, `captions`,
  `storyboards` y `microformat` no se recorren ni se modifican: salen byte a byte como llegaron.
- Si el cuerpo no es JSON valido (error HTTP, respuesta parcial), se devuelve intacto.
- Si no habia nada que quitar, se devuelve la cadena original sin reserializar.
- El puente JS nunca lanza: ante cualquier fallo la pagina recibe la respuesta original.

### Alcance real

Esto filtra el reproductor **dentro de esta app**. La app oficial de YouTube Music no se puede
interceptar: su trafico va por TLS y no confia en CAs de usuario, asi que haria falta root o
parchear el APK. El filtro DNS (modulo 1) si aplica a todo el dispositivo.
