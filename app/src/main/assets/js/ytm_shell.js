// Integracion con la barra inferior nativa. Mismo envoltorio que player_hook.js (`config`, `api`,
// `post`). Todo lo que depende del DOM de YouTube Music falla sin consecuencias: si un selector
// deja de existir, la web sigue funcionando como siempre.

// --- Navegacion de la barra ---
// La web navega con "endpoints" de su menu lateral. Se reutiliza el endpoint real de cada entrada
// (identificado por browseId, que no depende del idioma) y se despacha como lo haria la propia
// web: sin recargar la pagina, asi que la musica no se corta.
function guideEntry(browseId) {
  var entries = document.querySelectorAll('ytmusic-guide-entry-renderer');
  for (var i = 0; i < entries.length; i++) {
    var data = entries[i].data;
    var endpoint = data && data.navigationEndpoint;
    if (endpoint && endpoint.browseEndpoint && endpoint.browseEndpoint.browseId === browseId) {
      return { element: entries[i], endpoint: endpoint };
    }
  }
  return null;
}

api.navigate = function (browseId) {
  var entry = guideEntry(browseId);
  if (!entry) return false;
  var app = document.querySelector('ytmusic-app');
  if (app) {
    app.dispatchEvent(new CustomEvent('yt-navigate', {
      bubbles: true,
      composed: true,
      detail: { endpoint: entry.endpoint }
    }));
    return true;
  }
  var clickable = entry.element.querySelector('tp-yt-paper-item') || entry.element;
  clickable.click();
  return true;
};

// Con el cuadro cerrado, el boton de la lupa lo abre; abierto, ese mismo boton es "Atras".
function searchButton(box) {
  return box.querySelector('.search-button button') || box.querySelector('.search-button');
}

api.openSearch = function () {
  var box = document.querySelector('ytmusic-search-box');
  if (!box) return false;
  if (!box.hasAttribute('opened')) {
    var button = searchButton(box);
    if (!button) return false;
    button.click();
  }
  var input = box.querySelector('input');
  if (input) input.focus();
  return true;
};

api.closeSearch = function () {
  var box = document.querySelector('ytmusic-search-box');
  if (!box || !box.hasAttribute('opened')) return false;
  var button = searchButton(box);
  if (!button) return false;
  button.click();
  return true;
};

api.collapsePlayer = function () {
  var button = document.querySelector('ytmusic-player-page #collapse-button button') ||
    document.querySelector('ytmusic-player-page #collapse-button');
  if (!button) return false;
  button.click();
  return true;
};

// --- Elementos que sobran con la barra nativa ---
// El menu lateral lo sustituye la barra y "Abrir aplicacion" llevaria a la app oficial. Solo se
// oculta el boton del menu, no el menu: la navegacion de la barra usa sus entradas.
var HIDDEN = 'ytmusic-nav-bar #guide-button,ytmusic-nav-bar a.app-install-link{display:none!important}';

function injectStyle() {
  var root = document.head || document.documentElement;
  if (!root) return false;
  var style = document.createElement('style');
  style.textContent = HIDDEN;
  root.appendChild(style);
  return true;
}

if (!injectStyle()) {
  document.addEventListener('DOMContentLoaded', injectStyle, { once: true });
}

// --- Estado que la app refleja ---
// Pagina de la cancion abierta (la barra nativa se esconde, como en la app oficial) y cuadro de
// busqueda abierto (atras lo cierra). Un MutationObserver por atributo: sin sondeos continuos.
var WATCH_POLL_MS = 250;
var WATCH_MAX_TRIES = 120; // 30 s: si no aparece, no se esta en YouTube Music

function watchAttribute(selector, attribute, type) {
  var tries = 0;
  (function attach() {
    var element = document.querySelector(selector);
    if (!element) {
      if (++tries < WATCH_MAX_TRIES) setTimeout(attach, WATCH_POLL_MS);
      return;
    }
    var last = null;
    function report() {
      var open = element.hasAttribute(attribute);
      if (open === last) return;
      last = open;
      post({ t: type, open: open });
    }
    new MutationObserver(report).observe(element, { attributes: true, attributeFilter: [attribute] });
    report();
  })();
}

watchAttribute('ytmusic-app-layout', 'player-page-open', 'page');
watchAttribute('ytmusic-search-box', 'opened', 'search');
