package com.adblocker.ytmusic.domain.navigation

/** Lo que el boton atras sabe de la pagina en el momento de pulsarlo. */
data class BackState(
    val playerPageOpen: Boolean,
    val canGoBack: Boolean,
    val historyIndex: Int,
    val tabRootIndex: Int,
    val selectedTab: YtmTab,
    val searchOpen: Boolean = false,
)

enum class BackAction {
    /** Retroceder en el historial de la web (tambien cierra la pagina de la cancion). */
    GO_BACK,

    /** La pagina de la cancion esta abierta pero no hay historial al que volver. */
    COLLAPSE_PLAYER,

    /** El cuadro de busqueda esta abierto: abrirlo no crea historial, asi que se cierra a mano. */
    CLOSE_SEARCH,

    /** Fuera de Principal y sin historial propio de la seccion: se vuelve a Principal. */
    GO_HOME,

    /** Raiz de la app: se manda al fondo sin cerrarla, como la app oficial; la musica sigue. */
    SEND_TO_BACKGROUND,
}

/**
 * Orden de atras de la app oficial: cancion abierta -> busqueda abierta -> historial de la
 * seccion -> Principal -> segundo plano. Se decide en Kotlin con el estado reflejado de la
 * pagina: el gesto de atras no puede quedarse esperando una respuesta de JavaScript.
 */
object BackNavigator {

    fun decide(state: BackState): BackAction = when {
        state.playerPageOpen -> if (state.canGoBack) BackAction.GO_BACK else BackAction.COLLAPSE_PLAYER
        state.searchOpen -> BackAction.CLOSE_SEARCH
        state.canGoBack && state.historyIndex > state.tabRootIndex -> BackAction.GO_BACK
        state.selectedTab != YtmTab.HOME -> BackAction.GO_HOME
        else -> BackAction.SEND_TO_BACKGROUND
    }
}
