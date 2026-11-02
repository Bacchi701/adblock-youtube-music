// Hook del reproductor, inyectado en document-start solo en https://music.youtube.com.
// Se ejecuta dentro del envoltorio que arma HookScript.kt: `config`, `api` y `createPruner` vienen
// de ahi. Regla de oro: ante cualquier error la pagina recibe lo original y sigue sonando.

var bridge = window[config.bridge] || null;

function post(message) {
  if (!bridge) return;
  try {
    bridge.postMessage(JSON.stringify(message));
  } catch (error) {
    // el puente no esta disponible: la reproduccion no debe verse afectada
  }
}

function text(value) {
  return typeof value === 'string' ? value.trim() : '';
}

// --- Poda de youtubei/v1/player ---
// Se hace aqui, de forma sincrona: V8 parsea y serializa el cuerpo en milisegundos, mientras que
// mandarlo a Kotlin costaba dos copias de cientos de KB por el puente en cada cancion.
var pruner = createPruner(config.adKeys, config.protectedKeys);
var PLAYER_HOSTS = /(^|\.)(youtube\.com|youtube-nocookie\.com|googleapis\.com|googlevideo\.com)$/;
var paths = { fetch: 0, xhr: 0 };

function isPlayerUrl(url) {
  try {
    var parsed = new URL(url, location.href);
    var path = parsed.pathname;
    return (path === '/youtubei/v1/player' || path === '/youtubei/v1/player/') &&
      PLAYER_HOSTS.test(parsed.hostname);
  } catch (error) {
    return false;
  }
}

function requestUrl(input) {
  if (typeof input === 'string') return input;
  if (input && typeof input.url === 'string') return input.url; // Request
  if (input && typeof input.href === 'string') return input.href; // URL
  return '';
}

function reportPruned(removed) {
  post({ t: 'pruned', removed: removed });
}

// fetch: es la via del cliente web actual. YTM guarda su referencia a `fetch` al cargar, por eso
// este envoltorio solo funciona si se instala antes que sus scripts (document-start).
var originalFetch = window.fetch;
if (typeof originalFetch === 'function') {
  window.fetch = function (input, init) {
    var url = requestUrl(input);
    var pending = originalFetch.apply(this, arguments);
    if (!isPlayerUrl(url)) return pending;

    return pending.then(function (response) {
      if (!response || !response.ok) return response;
      return response.clone().text().then(function (body) {
        var result = pruner.text(body);
        paths.fetch++;
        reportPruned(result.removed);
        if (result.removed === 0) return response;
        var headers = new Headers(response.headers);
        // El cuerpo cambio de tamaño y ya no va comprimido.
        headers.delete('content-length');
        headers.delete('content-encoding');
        var pruned = new Response(result.text, {
          status: response.status,
          statusText: response.statusText,
          headers: headers
        });
        try {
          Object.defineProperty(pruned, 'url', { value: response.url });
        } catch (error) {
          // sin url propia: la pagina no la usa para el player
        }
        return pruned;
      }, function () {
        return response;
      });
    });
  };
}

// XMLHttpRequest: ruta heredada. La respuesta se poda al leerla (getter perezoso por instancia),
// asi da igual en que orden registro la pagina sus manejadores de eventos.
var xhrProto = XMLHttpRequest.prototype;
var responseTextGetter = Object.getOwnPropertyDescriptor(xhrProto, 'responseText').get;
var responseGetter = Object.getOwnPropertyDescriptor(xhrProto, 'response').get;
var originalOpen = xhrProto.open;

// Estado de la peticion en curso de cada instancia: una misma XHR puede reutilizarse con otra URL.
var xhrStates = new WeakMap();
var patchedXhrs = new WeakSet();

function prunedXhrText(xhr, state) {
  if (xhr.readyState !== 4 || xhr.status < 200 || xhr.status >= 300) return null;
  if (!state.result) {
    state.result = pruner.text(responseTextGetter.call(xhr));
    paths.xhr++;
    reportPruned(state.result.removed);
  }
  return state.result.text;
}

function patchXhr(xhr) {
  Object.defineProperty(xhr, 'responseText', {
    configurable: true,
    get: function () {
      var state = xhrStates.get(xhr);
      var pruned = state && state.player ? prunedXhrText(xhr, state) : null;
      return pruned !== null ? pruned : responseTextGetter.call(xhr);
    }
  });
  Object.defineProperty(xhr, 'response', {
    configurable: true,
    get: function () {
      var state = xhrStates.get(xhr);
      var type = xhr.responseType;
      if (state && state.player && (type === '' || type === 'text')) {
        var pruned = prunedXhrText(xhr, state);
        if (pruned !== null) return pruned;
      }
      var value = responseGetter.call(xhr);
      if (state && state.player && type === 'json' && !state.objectPruned && xhr.readyState === 4) {
        state.objectPruned = true;
        paths.xhr++;
        reportPruned(pruner.object(value));
      }
      return value;
    }
  });
}

xhrProto.open = function (method, url) {
  var state = { player: isPlayerUrl(String(url)), result: null, objectPruned: false };
  xhrStates.set(this, state);
  if (state.player && !patchedXhrs.has(this)) {
    try {
      patchXhr(this);
      patchedXhrs.add(this);
    } catch (error) {
      // instancia sellada: se ignora y la peticion sigue sin filtrar
    }
  }
  return originalOpen.apply(this, arguments);
};

// --- Sesion multimedia ---
// WebView no implementa navigator.mediaSession. Con este sustituto YouTube Music publica titulo,
// artista, album y caratula, y registra sus propios manejadores de play/pausa/siguiente: los
// mandos de la notificacion los usan en lugar de pulsar botones del DOM.
var handlers = Object.create(null);
var currentMetadata = null;
var playbackState = 'none';
var lastTrackKey = '';
var lastMedia = null;

function readArtwork(list) {
  var result = [];
  if (!list || typeof list.length !== 'number') return result;
  for (var i = 0; i < list.length && result.length < 10; i++) {
    var item = list[i];
    if (item && typeof item.src === 'string') {
      result.push({ src: item.src, sizes: typeof item.sizes === 'string' ? item.sizes : '' });
    }
  }
  return result;
}

function MediaMetadataShim(init) {
  init = init || {};
  this.title = text(init.title);
  this.artist = text(init.artist);
  this.album = text(init.album);
  this.artwork = readArtwork(init.artwork);
}

function textOf(selectors) {
  for (var i = 0; i < selectors.length; i++) {
    var node = document.querySelector(selectors[i]);
    var value = node && (node.textContent || '').trim();
    if (value) return value;
  }
  return '';
}

function publishTrack() {
  var meta = currentMetadata;
  var message = {
    t: 'meta',
    title: meta ? text(meta.title) : '',
    artist: meta ? text(meta.artist) : '',
    album: meta ? text(meta.album) : '',
    artwork: meta ? readArtwork(meta.artwork) : []
  };
  if (!message.title) {
    // Respaldo si la pagina no publico metadatos: la barra del reproductor.
    message.title = textOf(['ytmusic-player-bar .title']);
    // La linea secundaria es "Artista • Album • Año": interesa el primer tramo.
    message.artist = textOf(['ytmusic-player-bar .byline']).split('•')[0].trim();
  }
  if (!message.title) return;
  var key = JSON.stringify(message);
  if (key === lastTrackKey) return;
  lastTrackKey = key;
  post(message);
}

var session = {
  setActionHandler: function (action, handler) {
    if (typeof handler === 'function') handlers[action] = handler;
    else delete handlers[action];
  },
  setPositionState: function () {},
  setMicrophoneActive: function () {},
  setCameraActive: function () {}
};
Object.defineProperty(session, 'metadata', {
  enumerable: true,
  get: function () { return currentMetadata; },
  set: function (value) {
    currentMetadata = value || null;
    publishTrack();
  }
});
Object.defineProperty(session, 'playbackState', {
  enumerable: true,
  get: function () { return playbackState; },
  set: function (value) { playbackState = String(value); }
});

try {
  if (!('mediaSession' in navigator)) {
    Object.defineProperty(Navigator.prototype, 'mediaSession', {
      configurable: true,
      enumerable: true,
      get: function () { return session; }
    });
    if (typeof window.MediaMetadata !== 'function') window.MediaMetadata = MediaMetadataShim;
  } else {
    // Un WebView futuro con la API real: se escuchan sus setters sin sustituirla.
    var proto = Object.getPrototypeOf(navigator.mediaSession);
    var metadataDescriptor = Object.getOwnPropertyDescriptor(proto, 'metadata');
    if (metadataDescriptor && metadataDescriptor.set) {
      Object.defineProperty(proto, 'metadata', {
        configurable: true,
        enumerable: true,
        get: metadataDescriptor.get,
        set: function (value) {
          metadataDescriptor.set.call(this, value);
          currentMetadata = value || null;
          publishTrack();
        }
      });
    }
    var nativeSetActionHandler = proto.setActionHandler;
    proto.setActionHandler = function (action, handler) {
      session.setActionHandler(action, handler);
      return nativeSetActionHandler.apply(this, arguments);
    };
  }
} catch (error) {
  // sin sesion multimedia: queda el respaldo del DOM
}

// --- Estado de reproduccion ---
// Solo eventos (nada de sondeos): el sistema extrapola la posicion entre un aviso y otro.
function activeMedia() {
  var elements = document.querySelectorAll('video, audio');
  for (var i = 0; i < elements.length; i++) {
    if (!elements[i].paused) return elements[i];
  }
  return lastMedia || elements[0] || null;
}

function publishState(media, buffering) {
  post({
    t: 'state',
    playing: !media.paused && !media.ended,
    buffering: buffering,
    position: media.currentTime,
    duration: media.duration
  });
}

var trackRetry = 0;
['play', 'playing', 'pause', 'ended', 'seeked', 'ratechange', 'durationchange', 'loadedmetadata', 'waiting']
  .forEach(function (type) {
    // Los eventos multimedia no burbujean: fase de captura para oir a cualquier elemento.
    document.addEventListener(type, function (event) {
      var media = event.target;
      if (!(media instanceof HTMLMediaElement)) return;
      lastMedia = media;
      publishState(media, type === 'waiting');
      if (type === 'playing' || type === 'loadedmetadata') {
        publishTrack();
        // Sin metadatos de la pagina, el titulo del DOM llega un instante despues del play.
        if (!currentMetadata) {
          clearTimeout(trackRetry);
          trackRetry = setTimeout(publishTrack, 1500);
        }
      }
    }, true);
  });

// --- Mandos de la notificacion y la pantalla de bloqueo ---
function invoke(action, details) {
  var handler = handlers[action];
  if (typeof handler !== 'function') return false;
  try {
    var event = { action: action };
    if (details) {
      for (var key in details) event[key] = details[key];
    }
    handler(event);
    return true;
  } catch (error) {
    return false;
  }
}

function clickFirst(selectors) {
  for (var i = 0; i < selectors.length; i++) {
    var button = document.querySelector(selectors[i]);
    if (button) {
      button.click();
      return true;
    }
  }
  return false;
}

api.play = function () {
  if (invoke('play')) return;
  var media = activeMedia();
  if (media) media.play();
};
api.pause = function () {
  if (invoke('pause')) return;
  var media = activeMedia();
  if (media) media.pause();
};
api.next = function () {
  if (!invoke('nexttrack')) clickFirst(['ytmusic-player-bar .next-button', '.ytp-next-button']);
};
api.previous = function () {
  if (!invoke('previoustrack')) clickFirst(['ytmusic-player-bar .previous-button', '.ytp-prev-button']);
};
api.seek = function (seconds) {
  if (!isFinite(seconds)) return;
  if (invoke('seekto', { seekTime: seconds })) return;
  var media = activeMedia();
  if (media) media.currentTime = seconds;
};
api.isPlaying = function () {
  var media = activeMedia();
  return !!media && !media.paused;
};
if (config.debug) {
  // Solo en la variante depurable: por que via llego cada playerResponse.
  api.debugPaths = function () { return JSON.stringify(paths); };
}
