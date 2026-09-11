package com.adblocker.ytmusic.di

import android.content.Context
import com.adblocker.ytmusic.data.blocklist.AssetBlocklistDataSource
import com.adblocker.ytmusic.data.blocklist.AssetBlocklistRepository
import com.adblocker.ytmusic.data.net.ConnectivityPrivateDnsMonitor
import com.adblocker.ytmusic.domain.player.PlaybackCoordinator
import com.adblocker.ytmusic.domain.repository.BlocklistRepository
import com.adblocker.ytmusic.domain.repository.PrivateDnsMonitor
import com.adblocker.ytmusic.domain.usecase.EvaluateDomainUseCase
import com.adblocker.ytmusic.domain.usecase.FilterPlayerResponseUseCase

/**
 * Inyeccion de dependencias manual: el proyecto prohibe librerias de terceros,
 * asi que no se usa Hilt/Koin.
 */
object ServiceLocator {

    @Volatile
    private var repository: BlocklistRepository? = null

    @Volatile
    private var evaluateDomain: EvaluateDomainUseCase? = null

    @Volatile
    private var filterPlayerResponse: FilterPlayerResponseUseCase? = null

    @Volatile
    private var privateDnsMonitor: PrivateDnsMonitor? = null

    @Volatile
    private var playbackCoordinator: PlaybackCoordinator? = null

    fun blocklistRepository(context: Context): BlocklistRepository =
        repository ?: synchronized(this) {
            repository ?: AssetBlocklistRepository(
                AssetBlocklistDataSource(context.applicationContext.assets),
            ).also { repository = it }
        }

    fun evaluateDomainUseCase(context: Context): EvaluateDomainUseCase =
        evaluateDomain ?: synchronized(this) {
            evaluateDomain ?: EvaluateDomainUseCase(blocklistRepository(context))
                .also { evaluateDomain = it }
        }

    fun privateDnsMonitor(context: Context): PrivateDnsMonitor =
        privateDnsMonitor ?: synchronized(this) {
            privateDnsMonitor ?: ConnectivityPrivateDnsMonitor(context.applicationContext)
                .also { privateDnsMonitor = it }
        }

    /** Compartido por el reproductor y el servicio de la MediaSession. */
    fun playbackCoordinator(): PlaybackCoordinator =
        playbackCoordinator ?: synchronized(this) {
            playbackCoordinator ?: PlaybackCoordinator().also { playbackCoordinator = it }
        }

    /** Compartido por el WebView y la UI para que las estadisticas sean las mismas. */
    fun filterPlayerResponseUseCase(): FilterPlayerResponseUseCase =
        filterPlayerResponse ?: synchronized(this) {
            filterPlayerResponse ?: FilterPlayerResponseUseCase().also { filterPlayerResponse = it }
        }
}
