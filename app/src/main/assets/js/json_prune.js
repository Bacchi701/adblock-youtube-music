// Poda de las claves de anuncios del playerResponse dentro de la pagina.
// Replica PlayerResponseFilter (Kotlin), que es la referencia: JsonPruneScriptTest pasa las mismas
// muestras por ambas y exige el mismo JSON. No usa nada del navegador para poder probarse en Node.
//
// Invariantes (las mismas que en Kotlin):
//  1. Las claves protegidas (streamingData, videoDetails...) ni se borran ni se recorren.
//  2. Si el cuerpo no es JSON valido, sale intacto.
//  3. Si no habia nada que quitar, sale la cadena original sin reserializar.
function createPruner(adKeys, protectedKeys) {
  var ad = new Set(adKeys);
  var keep = new Set(protectedKeys);
  // `"adSlots"` con comillas: asi aparece la clave en el cuerpo sin formatear.
  var tokens = adKeys.map(function (key) { return '"' + key + '"'; });

  function mayContainAds(text) {
    for (var i = 0; i < tokens.length; i++) {
      if (text.indexOf(tokens[i]) !== -1) return true;
    }
    return false;
  }

  // Quita en el sitio las claves de anuncios y devuelve cuantas quito.
  function strip(value) {
    var removed = 0;
    if (Array.isArray(value)) {
      for (var i = 0; i < value.length; i++) {
        var item = value[i];
        if (item !== null && typeof item === 'object') removed += strip(item);
      }
      return removed;
    }
    var keys = Object.keys(value);
    for (var j = 0; j < keys.length; j++) {
      if (ad.has(keys[j])) {
        delete value[keys[j]];
        removed++;
      }
    }
    keys = Object.keys(value);
    for (var k = 0; k < keys.length; k++) {
      // Subarbol intocable: ni se elimina ni se recorre.
      if (keep.has(keys[k])) continue;
      var child = value[keys[k]];
      if (child !== null && typeof child === 'object') removed += strip(child);
    }
    return removed;
  }

  return {
    // Texto -> { text, removed }. Nunca lanza: ante cualquier problema devuelve el original.
    text: function (text) {
      if (typeof text !== 'string' || !mayContainAds(text)) return { text: text, removed: 0 };
      try {
        var root = JSON.parse(text);
        if (root === null || typeof root !== 'object') return { text: text, removed: 0 };
        var removed = strip(root);
        return removed === 0 ? { text: text, removed: 0 } : { text: JSON.stringify(root), removed: removed };
      } catch (error) {
        // JSON invalido o tan anidado que agota la pila: mejor un anuncio que un fallo.
        return { text: text, removed: 0 };
      }
    },
    // Objeto ya parseado (XHR con responseType 'json'): se poda en el sitio.
    object: function (value) {
      if (value === null || typeof value !== 'object') return 0;
      try {
        return strip(value);
      } catch (error) {
        return 0;
      }
    }
  };
}
