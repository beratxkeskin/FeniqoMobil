package com.feniqo.mobile.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.feniqo.mobile.domain.network.NetworkConnectivityObserver
import com.feniqo.mobile.domain.network.NetworkStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android ConnectivityManager platform çağrılarını sarmalayan internal yardımcı arayüz.
 * Birim ve Robolectric testlerinde platform bağımlılıklarını güvenle simüle etmeyi sağlar.
 */
internal interface ConnectivityManagerDelegate {
    val activeNetwork: Network?
    fun getNetworkCapabilities(network: Network): NetworkCapabilities?
    fun registerDefaultNetworkCallback(callback: ConnectivityManager.NetworkCallback)
    fun unregisterNetworkCallback(callback: ConnectivityManager.NetworkCallback)
}

internal class DefaultConnectivityManagerDelegate(
    private val connectivityManager: ConnectivityManager,
) : ConnectivityManagerDelegate {
    override val activeNetwork: Network?
        get() = connectivityManager.activeNetwork

    override fun getNetworkCapabilities(network: Network): NetworkCapabilities? {
        return connectivityManager.getNetworkCapabilities(network)
    }

    override fun registerDefaultNetworkCallback(callback: ConnectivityManager.NetworkCallback) {
        connectivityManager.registerDefaultNetworkCallback(callback)
    }

    override fun unregisterNetworkCallback(callback: ConnectivityManager.NetworkCallback) {
        connectivityManager.unregisterNetworkCallback(callback)
    }
}

/**
 * Android işletim sistemi ConnectivityManager API'sini kullanarak
 * ağ durumunu gözlemleyen ve anlık durum sunan üretim uygulaması.
 */
@Singleton
class AndroidNetworkConnectivityObserver internal constructor(
    private val delegate: ConnectivityManagerDelegate,
) : NetworkConnectivityObserver {

    @Inject
    constructor(connectivityManager: ConnectivityManager) : this(
        delegate = DefaultConnectivityManagerDelegate(connectivityManager),
    )

    override fun currentNetworkStatus(): NetworkStatus {
        return try {
            val activeNetwork = delegate.activeNetwork ?: return NetworkStatus.DISCONNECTED
            val capabilities = delegate.getNetworkCapabilities(activeNetwork)
                ?: return NetworkStatus.DISCONNECTED

            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            if (hasInternet && isValidated) {
                NetworkStatus.CONNECTED
            } else {
                NetworkStatus.DISCONNECTED
            }
        } catch (e: SecurityException) {
            NetworkStatus.UNKNOWN
        } catch (e: Exception) {
            NetworkStatus.UNKNOWN
        }
    }

    override fun observeNetworkStatus(): Flow<NetworkStatus> = callbackFlow {
        // Collector başladığında callback beklemeden mevcut durumu ilk değer olarak yayınla
        trySend(currentNetworkStatus())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(currentNetworkStatus())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                trySend(currentNetworkStatus())
            }

            override fun onLost(network: Network) {
                // Doğrudan DISCONNECTED göndermek yerine aktif ağ durumunu yeniden hesapla
                trySend(currentNetworkStatus())
            }

            override fun onUnavailable() {
                trySend(currentNetworkStatus())
            }
        }

        var isRegistered = false
        try {
            delegate.registerDefaultNetworkCallback(callback)
            isRegistered = true
        } catch (e: SecurityException) {
            trySend(NetworkStatus.UNKNOWN)
        } catch (e: RuntimeException) {
            trySend(NetworkStatus.UNKNOWN)
        } catch (e: Exception) {
            trySend(NetworkStatus.UNKNOWN)
        }

        awaitClose {
            if (isRegistered) {
                try {
                    delegate.unregisterNetworkCallback(callback)
                } catch (e: Exception) {
                    // Kapatma sırasında oluşabilecek güvenli istisnaları yönet
                }
            }
        }
    }.distinctUntilChanged().conflate()
}
