package com.feniqo.mobile.domain.network

import kotlinx.coroutines.flow.Flow

/**
 * Cihazın ağ bağlantı durumunu temsil eder.
 */
enum class NetworkStatus {
    UNKNOWN,
    CONNECTED,
    DISCONNECTED,
}

/**
 * Ağ bağlantısı gözlemcisi sözleşmesi.
 */
interface NetworkConnectivityObserver {
    fun observeNetworkStatus(): Flow<NetworkStatus>
    fun currentNetworkStatus(): NetworkStatus
}
