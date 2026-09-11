package com.adblocker.ytmusic.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.adblocker.ytmusic.di.ServiceLocator
import com.adblocker.ytmusic.domain.model.FilterStats
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * VPN local (sin ROOT) que solo captura el trafico DNS del dispositivo.
 * No hay servidor remoto: el tunel empieza y termina en este proceso.
 */
class AdBlockVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob())
    private var tunnelJob: Job? = null
    private var tunInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopFiltering()
                return START_NOT_STICKY
            }
            else -> startFiltering()
        }
        return START_STICKY
    }

    private fun startFiltering() {
        if (tunnelJob?.isActive == true) return

        FilterNotifications.ensureChannel(this)
        startInForeground()

        tunnelJob = scope.launch {
            runCatching {
                val evaluateDomain = ServiceLocator.evaluateDomainUseCase(applicationContext)
                evaluateDomain.prepare()
                val descriptor = establishTunnel() ?: error("VpnService.establish devolvio null")
                tunInterface = descriptor
                _isRunning.value = true

                FileInputStream(descriptor.fileDescriptor).use { input ->
                    FileOutputStream(descriptor.fileDescriptor).use { output ->
                        DnsFilterTunnel(
                            tunInput = input,
                            tunOutput = output,
                            evaluateDomain = evaluateDomain,
                            upstreamDns = resolveUpstreamDns(),
                            protectSocket = { socket -> protect(socket) },
                            onStats = { _stats.value = it },
                        ).run()
                    }
                }
            }.onFailure { error ->
                Log.e(TAG, "El tunel de filtrado se detuvo", error)
                stopFiltering()
            }
        }
    }

    private fun startInForeground() {
        val notification = FilterNotifications.build(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                FilterNotifications.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(FilterNotifications.NOTIFICATION_ID, notification)
        }
    }

    private fun establishTunnel(): ParcelFileDescriptor? = Builder()
        .setSession(getString(com.adblocker.ytmusic.R.string.app_name))
        .setMtu(MTU)
        .addAddress(TUN_ADDRESS, TUN_PREFIX)
        .addDnsServer(VIRTUAL_DNS)
        // Solo se enruta el DNS virtual: el resto del trafico no entra al tunel.
        .addRoute(VIRTUAL_DNS, HOST_PREFIX)
        .setBlocking(true)
        .establish()

    /** Usa el resolutor del sistema; nunca un DNS de terceros elegido por la app. */
    private fun resolveUpstreamDns(): InetAddress {
        val manager = getSystemService(ConnectivityManager::class.java)
        val network = manager?.activeNetwork
        val systemDns = network
            ?.let { manager.getLinkProperties(it) }
            ?.dnsServers
            ?.firstOrNull { it.address.size == 4 }
        return systemDns ?: InetAddress.getByName(FALLBACK_DNS)
    }

    private fun stopFiltering() {
        tunnelJob?.cancel()
        tunnelJob = null
        runCatching { tunInterface?.close() }
        tunInterface = null
        _isRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        stopFiltering()
        super.onRevoke()
    }

    override fun onDestroy() {
        scope.cancel()
        _isRunning.value = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AdBlockVpnService"
        private const val MTU = 1500
        private const val TUN_ADDRESS = "10.215.173.1"
        private const val TUN_PREFIX = 32
        private const val VIRTUAL_DNS = "10.215.173.2"
        private const val HOST_PREFIX = 32
        private const val FALLBACK_DNS = "9.9.9.9"

        const val ACTION_START = "com.adblocker.ytmusic.action.START"
        const val ACTION_STOP = "com.adblocker.ytmusic.action.STOP"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _stats = MutableStateFlow(FilterStats())
        val stats: StateFlow<FilterStats> = _stats.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, AdBlockVpnService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AdBlockVpnService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
