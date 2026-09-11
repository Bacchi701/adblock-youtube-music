// Hook inyectado en music.youtube.com. Enruta las respuestas de youtubei/v1/player por el
// filtro Kotlin (AdBlockBridge) antes de que las vea el reproductor.
// Regla de oro: ante cualquier error se devuelve la respuesta original sin tocar.
(function () {
  'use strict';

  if (window.__adBlockPlayerHook) return;
  var bridge = window.AdBlockBridge;
  if (!bridge || typeof bridge.filterPlayerResponse !== 'function') return;
  window.__adBlockPlayerHook = true;

  function isPlayerUrl(url) {
    try {
      return typeof url === 'string' && url.length > 0 && bridge.isPlayerRequest(url);
    } catch (error) {
      return false;
    }
  }

  function sanitize(url, text) {
    try {
      var filtered = bridge.filterPlayerResponse(url, text);
      return typeof filtered === 'string' && filtered.length > 0 ? filtered : text;
    } catch (error) {
      return text;
    }
  }

  // --- Estado de reproduccion y metadatos ---
  // Los eventos de <audio>/<video> no burbujean: se escuchan en fase de captura para
  // enterarse de cualquier elemento, incluidos los que la pagina crea despues.
  var lastMedia = null;

  function activeMedia() {
    var elements = document.querySelectorAll('video, audio');
    for (var i = 0; i < elements.length; i++) {
      if (!elements[i].paused) return elements[i];
    }
    return lastMedia || elements[0] || null;
  }

  function notifyState(playing) {
    try {
      bridge.onPlaybackStateChanged(playing);
    } catch (error) {
      // el puente no esta disponible: la reproduccion no debe verse afectada
    }
  }

  function textOf(selectors) {
    for (var i = 0; i < selectors.length; i++) {
      var node = document.querySelector(selectors[i]);
      var text = node && (node.textContent || '').trim();
      if (text) return text;
    }
    return '';
  }

  function notifyMetadata(playing) {
    if (typeof bridge.onMetadataChanged !== 'function') return;
    try {
      var media = activeMedia();
      // navigator.mediaSession se rellena de forma asincrona y puede seguir vacio justo al
      // arrancar: la barra del reproductor es la fuente fiable, y document.title el ultimo
      // recurso (mientras carga vale solo "YouTube Music").
      var meta = (navigator.mediaSession && navigator.mediaSession.metadata) || {};
      var title = meta.title ||
        textOf(['.title.ytmusic-player-bar', 'ytmusic-player-bar .title']) ||
        document.title ||
        '';
      // La linea secundaria es "Artista • Album • Año": interesa el primer tramo.
      var artist = meta.artist ||
        textOf(['.byline.ytmusic-player-bar', 'ytmusic-player-bar .byline']).split('•')[0].trim();

      bridge.onMetadataChanged(
        title,
        artist,
        media ? media.duration : 0,
        media ? media.currentTime : 0,
        playing
      );
    } catch (error) {
      // metadatos incompletos: la reproduccion no debe verse afectada
    }
  }

  function onMediaEvent(playing) {
    return function (event) {
      if (event && event.target) lastMedia = event.target;
      notifyState(playing);
      notifyMetadata(playing);
      if (playing) {
        // La pagina termina de rellenar titulo e interprete un instante despues del play.
        setTimeout(function () { notifyMetadata(true); }, 1500);
        setTimeout(function () { notifyMetadata(true); }, 4000);
      }
    };
  }

  document.addEventListener('play', onMediaEvent(true), true);
  document.addEventListener('playing', onMediaEvent(true), true);
  document.addEventListener('pause', onMediaEvent(false), true);
  document.addEventListener('ended', onMediaEvent(false), true);
  document.addEventListener('loadedmetadata', function (event) {
    if (event && event.target) lastMedia = event.target;
    notifyMetadata(!event.target.paused);
  }, true);

  // Refresco periodico para que la barra de progreso del bloqueo avance.
  setInterval(function () {
    var media = activeMedia();
    if (media && !media.paused) notifyMetadata(true);
  }, 5000);

  // --- Controles invocados desde la MediaSession nativa ---
  // Play/pausa actuan sobre el elemento; saltar de pista usa los botones de la pagina,
  // que es lo unico que mantiene sincronizada su cola de reproduccion.
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

  window.__adBlockPlayerControl = {
    play: function () {
      var media = activeMedia();
      if (media) media.play();
    },
    pause: function () {
      var media = activeMedia();
      if (media) media.pause();
    },
    next: function () {
      clickFirst(['.next-button.ytmusic-player-bar', 'tp-yt-paper-icon-button.next-button', '.ytp-next-button']);
    },
    previous: function () {
      clickFirst(['.previous-button.ytmusic-player-bar', 'tp-yt-paper-icon-button.previous-button', '.ytp-prev-button']);
    },
    seek: function (seconds) {
      var media = activeMedia();
      if (media && isFinite(seconds)) media.currentTime = seconds;
    }
  };

  // --- fetch (ruta que usa el cliente web actual) ---
  var originalFetch = window.fetch;
  if (typeof originalFetch === 'function') {
    window.fetch = function (input, init) {
      var url = typeof input === 'string' ? input : (input && input.url) || '';
      var pending = originalFetch.apply(this, arguments);
      if (!isPlayerUrl(url)) return pending;

      return pending.then(function (response) {
        if (!response || !response.ok) return response;
        return response
          .clone()
          .text()
          .then(function (text) {
            var filtered = sanitize(url, text);
            if (filtered === text) return response;
            return new Response(filtered, {
              status: response.status,
              statusText: response.statusText,
              // Solo se conserva el content-type: el content-length original ya no encaja.
              headers: { 'content-type': response.headers.get('content-type') || 'application/json' }
            });
          })
          .catch(function () {
            return response;
          });
      });
    };
  }

  // --- XMLHttpRequest (ruta heredada) ---
  var originalOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function (method, url) {
    try {
      this.__adBlockUrl = url;
    } catch (error) {
      // instancia sellada: se ignora y la peticion sigue sin filtrar
    }
    return originalOpen.apply(this, arguments);
  };

  var originalSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function () {
    var request = this;
    if (isPlayerUrl(request.__adBlockUrl)) {
      request.addEventListener('readystatechange', function () {
        if (request.readyState !== 4 || request.status < 200 || request.status >= 300) return;
        try {
          var raw = request.responseText;
          var filtered = sanitize(request.__adBlockUrl, raw);
          if (filtered === raw) return;
          Object.defineProperty(request, 'responseText', { get: function () { return filtered; } });
          Object.defineProperty(request, 'response', { get: function () { return filtered; } });
        } catch (error) {
          // responseText no redefinible: se deja la respuesta original
        }
      });
    }
    return originalSend.apply(this, arguments);
  };

  // --- Cierre de la pagina de la cancion ---
  // Al abrirla, YouTube Music pone `visibility: visible` en linea sobre `ytmusic-player-page` y
  // cuenta con el final de la animacion de cierre para devolverla a `hidden`. En WebView ese
  // `animationend` no llega (el de apertura si), asi que su `#main-panel` -fijo y a pantalla
  // completa- se queda sobre el inicio: medio pintado y tragandose todos los toques. Aqui se
  // hace ese mismo cambio en cuanto la pagina esta cerrada y sin animacion viva.
  //
  // Va al final a proposito: si algo aqui fallara, el filtrado de anuncios ya esta instalado.
  var PLAYER_PAGE = 'ytmusic-player-page';
  var CLOSE_POLL_MS = 100;
  var CLOSE_MAX_TRIES = 20; // 2 s de tope por si la animacion nunca termina
  var ATTACH_POLL_MS = 500;
  var ATTACH_MAX_TRIES = 60; // 30 s: si no aparece, no se esta en YouTube Music

  function releaseClosedPlayerPage(page, tries) {
    if (page.hasAttribute('player-page-open')) return;
    if (page.style.visibility !== 'visible') return;

    var animating = (page.getAnimations ? page.getAnimations({ subtree: true }) : [])
      .some(function (animation) { return animation.playState === 'running'; });
    if (animating && tries < CLOSE_MAX_TRIES) {
      setTimeout(function () { releaseClosedPlayerPage(page, tries + 1); }, CLOSE_POLL_MS);
      return;
    }

    // `hidden` es el valor en linea que la propia web le deja mientras esta cerrada: borrarlo
    // no basta, porque la hoja de estilos la deja visible.
    page.style.visibility = 'hidden';
  }

  function watchPlayerPage(page) {
    // Reabrir la cancion vuelve a poner el atributo, de ahi que se compruebe en cada cambio.
    // El aviso llega antes de que el motor recalcule los estilos: se espera un fotograma para
    // que, si hay animacion de cierre, conste antes de mirarla.
    new MutationObserver(function () {
      requestAnimationFrame(function () { releaseClosedPlayerPage(page, 0); });
    }).observe(page, { attributes: true, attributeFilter: ['player-page-open', 'style'] });
    releaseClosedPlayerPage(page, 0);
  }

  // El hook se inyecta con el documento todavia vacio: la pagina la crea Polymer mas tarde.
  function attachPlayerPageWatch(tries) {
    var page = document.querySelector(PLAYER_PAGE);
    if (page) {
      watchPlayerPage(page);
    } else if (tries < ATTACH_MAX_TRIES) {
      setTimeout(function () { attachPlayerPageWatch(tries + 1); }, ATTACH_POLL_MS);
    }
  }

  attachPlayerPageWatch(0);
})();
