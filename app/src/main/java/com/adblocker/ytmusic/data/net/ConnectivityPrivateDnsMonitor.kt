package com.adblocker.ytmusic.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.adblocker.ytmusic.domain.model.PrivateDnsStatus
import com.adblocker.ytmusic.domain.network.NetworkDnsInfo
import com.adblocker.ytmusic.domain.network.PrivateDnsEvaluator
import com.adblocker.ytmusic.domain.repository.PrivateDnsMonitor
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lee el modo de DNS privado de las redes del sistema via `ConnectivityManager`.
 * No consulta nada externo: `LinkProperties` es informacion local del dispositivo.
 */
class ConnectivityPrivateDnsMonitor(context: Context) : PrivateDnsMonitor {

    private val connectivity = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    private val known = ConcurrentHashMap<Network, NetworkDnsInfo>()

    private val _status = MutableStateFlow<PrivateDnsStatus>(PrivateDnsStatus.Off)
    override val status: StateFlow<PrivateDnsStatus> = _status.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            known[network] = (known[network] ?: NetworkDnsInfo()).copy(
                privateDnsActive = linkProperties.isPrivateDnsActive,
                privateDnsServerName = linkProperties.privateDnsServerName,
            )
            recompute()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities,
        ) {
            known[network] = (known[network] ?: NetworkDnsInfo()).copy(
                isVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            )
            recompute()
        }

        override fun onLost(network: Network) {
            known.remove(network)
            recompute()
        }
    }

    init {
        runCatching {
            connectivity?.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback,
            )
        }
        refresh()
    }

    override fun refresh() = recompute()

    private fun recompute() {
        _status.value = PrivateDnsEvaluator.evaluate(known.values.toList())
    }

    fun close() {
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
    }
}
