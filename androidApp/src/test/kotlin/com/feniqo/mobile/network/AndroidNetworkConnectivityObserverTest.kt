package com.feniqo.mobile.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.feniqo.mobile.domain.network.NetworkStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidNetworkConnectivityObserverTest {

    private lateinit var fakeDelegate: FakeConnectivityManagerDelegate
    private lateinit var observer: AndroidNetworkConnectivityObserver

    private class FakeConnectivityManagerDelegate : ConnectivityManagerDelegate {
        var activeNetworkOverride: Network? = null
        val capabilitiesMap = mutableMapOf<Network, NetworkCapabilities>()
        val registeredCallbacks = mutableSetOf<ConnectivityManager.NetworkCallback>()
        var throwSecurityExceptionOnAccess = false
        var throwSecurityExceptionOnRegister = false

        override val activeNetwork: Network?
            get() {
                if (throwSecurityExceptionOnAccess) throw SecurityException("Permission denied")
                return activeNetworkOverride
            }

        override fun getNetworkCapabilities(network: Network): NetworkCapabilities? {
            if (throwSecurityExceptionOnAccess) throw SecurityException("Permission denied")
            return capabilitiesMap[network]
        }

        override fun registerDefaultNetworkCallback(callback: ConnectivityManager.NetworkCallback) {
            if (throwSecurityExceptionOnRegister) throw SecurityException("Permission denied")
            registeredCallbacks.add(callback)
        }

        override fun unregisterNetworkCallback(callback: ConnectivityManager.NetworkCallback) {
            registeredCallbacks.remove(callback)
        }

        fun notifyCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            registeredCallbacks.toList().forEach { it.onCapabilitiesChanged(network, capabilities) }
        }

        fun notifyLost(network: Network) {
            registeredCallbacks.toList().forEach { it.onLost(network) }
        }

        fun notifyUnavailable() {
            registeredCallbacks.toList().forEach { it.onUnavailable() }
        }
    }

    @Before
    fun setUp() {
        fakeDelegate = FakeConnectivityManagerDelegate()
        observer = AndroidNetworkConnectivityObserver(fakeDelegate)
    }

    @Test
    fun test1_noActiveNetworkReturnsDisconnected() {
        // 1. Aktif ağ yoksa currentNetworkStatus() -> DISCONNECTED
        fakeDelegate.activeNetworkOverride = null
        assertEquals(NetworkStatus.DISCONNECTED, observer.currentNetworkStatus())
    }

    @Test
    fun test2_onlyInternetCapabilityReturnsDisconnected() {
        // 2. Yalnız INTERNET capability varsa fakat VALIDATED yoksa -> DISCONNECTED
        val network = ShadowNetwork.newInstance(1)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        fakeDelegate.activeNetworkOverride = network
        fakeDelegate.capabilitiesMap[network] = capabilities

        assertEquals(NetworkStatus.DISCONNECTED, observer.currentNetworkStatus())
    }

    @Test
    fun test3_internetAndValidatedCapabilitiesReturnsConnected() {
        // 3. INTERNET + VALIDATED varsa -> CONNECTED
        val network = ShadowNetwork.newInstance(1)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        Shadows.shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        fakeDelegate.activeNetworkOverride = network
        fakeDelegate.capabilitiesMap[network] = capabilities

        assertEquals(NetworkStatus.CONNECTED, observer.currentNetworkStatus())
    }

    @Test
    fun test4_collectorStartsEmitsCurrentStatusImmediately() = runTest {
        // 4. Collector başladığında mevcut durum ilk değer olarak hemen yayınlanır.
        fakeDelegate.activeNetworkOverride = null
        val initialStatus = observer.observeNetworkStatus().first()
        assertEquals(NetworkStatus.DISCONNECTED, initialStatus)
    }

    @Test
    fun test5_observeEmitsConnectedWhenNetworkValidated() = runTest {
        // 5. Ağ doğrulanınca akış CONNECTED yayınlar.
        fakeDelegate.activeNetworkOverride = null
        val emittedValues = mutableListOf<NetworkStatus>()

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observer.observeNetworkStatus().collect { emittedValues.add(it) }
        }

        assertEquals(listOf(NetworkStatus.DISCONNECTED), emittedValues)

        // Doğrulanmış ağ oluştur ve callback tetikle
        val network = ShadowNetwork.newInstance(1)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        Shadows.shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        fakeDelegate.activeNetworkOverride = network
        fakeDelegate.capabilitiesMap[network] = capabilities

        fakeDelegate.notifyCapabilitiesChanged(network, capabilities)

        assertEquals(listOf(NetworkStatus.DISCONNECTED, NetworkStatus.CONNECTED), emittedValues)
        job.cancel()
    }

    @Test
    fun test6_observeEmitsDisconnectedWhenValidatedNetworkLost() = runTest {
        // 6. Aktif doğrulanmış ağ kaybolunca ve alternatif ağ yoksa DISCONNECTED yayınlar.
        val network = ShadowNetwork.newInstance(1)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        Shadows.shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        fakeDelegate.activeNetworkOverride = network
        fakeDelegate.capabilitiesMap[network] = capabilities

        val emittedValues = mutableListOf<NetworkStatus>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observer.observeNetworkStatus().collect { emittedValues.add(it) }
        }

        assertEquals(listOf(NetworkStatus.CONNECTED), emittedValues)

        // Aktif ağ kayboldu
        fakeDelegate.activeNetworkOverride = null
        fakeDelegate.capabilitiesMap.remove(network)
        fakeDelegate.notifyLost(network)

        assertEquals(listOf(NetworkStatus.CONNECTED, NetworkStatus.DISCONNECTED), emittedValues)
        job.cancel()
    }

    @Test
    fun test7_doesNotEmitDisconnectedIfAnotherValidatedNetworkExists() = runTest {
        // 7. Bir ağ kaybolduğunda başka doğrulanmış aktif ağ varsa yanlışlıkla DISCONNECTED yayınlanmaz.
        val network1 = ShadowNetwork.newInstance(1)
        val network2 = ShadowNetwork.newInstance(2)

        val capabilities1 = ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(capabilities1).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        Shadows.shadowOf(capabilities1).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        val capabilities2 = ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(capabilities2).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        Shadows.shadowOf(capabilities2).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        fakeDelegate.capabilitiesMap[network1] = capabilities1
        fakeDelegate.capabilitiesMap[network2] = capabilities2
        fakeDelegate.activeNetworkOverride = network2 // network2 aktif

        val emittedValues = mutableListOf<NetworkStatus>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observer.observeNetworkStatus().collect { emittedValues.add(it) }
        }

        assertEquals(listOf(NetworkStatus.CONNECTED), emittedValues)

        // network1 kayboldu ama network2 hala aktif ve doğrulanmış
        fakeDelegate.capabilitiesMap.remove(network1)
        fakeDelegate.notifyLost(network1)

        // distinctUntilChanged nedeniyle yeni duplicate yayın yapılmaz, akış CONNECTED kalır
        assertEquals(listOf(NetworkStatus.CONNECTED), emittedValues)
        assertEquals(NetworkStatus.CONNECTED, observer.currentNetworkStatus())
        job.cancel()
    }

    @Test
    fun test8_distinctUntilChangedPreventsDuplicates() = runTest {
        // 8. Aynı durum art arda oluştuğunda distinctUntilChanged() nedeniyle duplicate değer yayınlanmaz.
        fakeDelegate.activeNetworkOverride = null
        val emittedValues = mutableListOf<NetworkStatus>()

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observer.observeNetworkStatus().collect { emittedValues.add(it) }
        }

        assertEquals(listOf(NetworkStatus.DISCONNECTED), emittedValues)

        // Çoklu callback olayları tetikle
        fakeDelegate.notifyUnavailable()
        fakeDelegate.notifyUnavailable()
        fakeDelegate.notifyLost(ShadowNetwork.newInstance(99))

        assertEquals(listOf(NetworkStatus.DISCONNECTED), emittedValues)
        job.cancel()
    }

    @Test
    fun test9_callbackUnregisteredWhenFlowCancelled() = runTest {
        // 9. Flow collector iptal edildiğinde callback unregister edilir.
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observer.observeNetworkStatus().collect {}
        }

        assertEquals(1, fakeDelegate.registeredCallbacks.size)

        job.cancel()

        assertEquals(0, fakeDelegate.registeredCallbacks.size)
    }

    @Test
    fun test10_securityExceptionReturnsUnknownWithoutCrashing() = runTest {
        // 10. Erişim sırasında SecurityException oluşursa durum UNKNOWN olur ve test çökmez.
        fakeDelegate.throwSecurityExceptionOnAccess = true

        assertEquals(NetworkStatus.UNKNOWN, observer.currentNetworkStatus())

        val emittedValues = mutableListOf<NetworkStatus>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observer.observeNetworkStatus().collect { emittedValues.add(it) }
        }

        assertEquals(listOf(NetworkStatus.UNKNOWN), emittedValues)
        job.cancel()
    }

    @Test
    fun test11_observeEmitsUnknownIfRegistrationThrowsSecurityExceptionEvenWhenInitiallyConnected() = runTest {
        // 11. İlk durum CONNECTED olsa bile callback registration SecurityException verirse akışın ardından UNKNOWN yayınlar.
        val network = ShadowNetwork.newInstance(1)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        Shadows.shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        fakeDelegate.activeNetworkOverride = network
        fakeDelegate.capabilitiesMap[network] = capabilities
        fakeDelegate.throwSecurityExceptionOnRegister = true

        val emittedValues = mutableListOf<NetworkStatus>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observer.observeNetworkStatus().collect { emittedValues.add(it) }
        }

        assertEquals(listOf(NetworkStatus.CONNECTED, NetworkStatus.UNKNOWN), emittedValues)
        job.cancel()
    }

    @Test
    fun test12_unregisterNotCalledWhenRegistrationFails() = runTest {
        // 12. Registration başarısız olduğunda cancellation sırasında unregister çağrılmaz.
        fakeDelegate.throwSecurityExceptionOnRegister = true
        var unregisterCalled = false

        val trackingDelegate = object : ConnectivityManagerDelegate {
            override val activeNetwork: Network? get() = null
            override fun getNetworkCapabilities(network: Network): NetworkCapabilities? = null
            override fun registerDefaultNetworkCallback(callback: ConnectivityManager.NetworkCallback) {
                throw SecurityException("Registration failed")
            }
            override fun unregisterNetworkCallback(callback: ConnectivityManager.NetworkCallback) {
                unregisterCalled = true
            }
        }

        val testObserver = AndroidNetworkConnectivityObserver(trackingDelegate)

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            testObserver.observeNetworkStatus().collect {}
        }

        job.cancel()

        assertFalse("Unregister, registration başarısız olduğunda çağrılmamalıdır", unregisterCalled)
    }
}
