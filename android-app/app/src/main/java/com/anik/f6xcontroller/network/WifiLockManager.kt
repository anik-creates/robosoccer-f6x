package com.anik.f6xcontroller.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WifiLockManager(private val context: Context) {

    companion object {
        private const val TAG = "WifiLockManager"
        private const val WAKE_LOCK_TAG = "APEX:CpuWakeLock"
        private const val WIFI_LOCK_TAG = "APEX:LowLatencyWifiLock"
    }

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val powerManager =
        context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var defaultNetworkCallback: ConnectivityManager.NetworkCallback? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    val isLocked: StateFlow<Boolean> get() = isConnected

    private val _statusMessage = MutableStateFlow("SEARCHING BOT...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _activeNetwork = MutableStateFlow<Network?>(null)
    val activeNetwork: StateFlow<Network?> = _activeNetwork.asStateFlow()

    var onNetworkBound: ((Network) -> Unit)? = null

    init {
        // 1. Immediately check if the phone is already connected to the robot Wi-Fi
        checkExistingWifiConnection()

        // 2. Start persistent background Wi-Fi monitoring (WITHOUT requiring Internet)
        startPersistentWifiMonitoring()
    }

    /**
     * Checks all currently active networks on the device.
     * If Wi-Fi is already active (even without internet access), binds immediately.
     */
    fun checkExistingWifiConnection() {
        try {
            for (network in connectivityManager.allNetworks) {
                val caps = connectivityManager.getNetworkCapabilities(network)
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    Log.i(TAG, "Discovered active Wi-Fi network at startup: $network")
                    bindAndLock(network)
                    return
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking existing Wi-Fi: ${e.message}")
        }
    }

    /**
     * Passively monitors active Wi-Fi connections.
     * CRITICAL: Must remove NET_CAPABILITY_INTERNET because robot AP has no internet!
     */
    private fun startPersistentWifiMonitoring() {
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            defaultNetworkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.i(TAG, "Wi-Fi Network Available (No Internet required): $network")
                    bindAndLock(network)
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    super.onCapabilitiesChanged(network, networkCapabilities)
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        bindAndLock(network)
                    }
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    if (_activeNetwork.value == network) {
                        Log.w(TAG, "Robot Wi-Fi lost: $network")
                        unbindAndRelease()
                    }
                }
            }

            defaultNetworkCallback?.let {
                connectivityManager.registerNetworkCallback(request, it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering network callback: ${e.message}")
        }
    }

    private fun bindAndLock(network: Network) {
        try {
            if (_activeNetwork.value == network && _isConnected.value) {
                return // Already bound and locked
            }

            val bound = connectivityManager.bindProcessToNetwork(network)
            Log.i(TAG, "Process successfully bound to Wi-Fi network: $bound ($network)")
            _activeNetwork.value = network
            _isConnected.value = true
            _statusMessage.value = "CONNECTED (ROBOT AP)"

            acquireLocks()

            // Trigger socket recreation on the bound network
            onNetworkBound?.invoke(network)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind and lock network: ${e.message}")
        }
    }

    private fun unbindAndRelease() {
        try {
            connectivityManager.bindProcessToNetwork(null)
            _activeNetwork.value = null
            _isConnected.value = false
            _statusMessage.value = "SEARCHING BOT..."
            releaseLocks()
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding network: ${e.message}")
        }
    }

    /**
     * Request connection specifically to the Robot SoftAP if not already connected.
     */
    fun lockToRobotWifi(ssid: String = "RoboSoccer_S3", passphrase: String = "soccer2026") = connectToRobot(ssid, passphrase)

    fun connectToRobot(ssid: String = "RoboSoccer_S3", passphrase: String = "soccer2026") {
        if (_isConnected.value) {
            Log.i(TAG, "Already connected to robot Wi-Fi.")
            return
        }

        checkExistingWifiConnection()
        if (_isConnected.value) return

        _statusMessage.value = "CONNECTING TO $ssid..."
        Log.i(TAG, "Requesting low-latency persistent connection to SSID: $ssid")

        try {
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(passphrase)
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.i(TAG, "Robot Wi-Fi available via specifier: $network")
                    bindAndLock(network)
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    if (_activeNetwork.value == network) {
                        unbindAndRelease()
                    }
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    Log.w(TAG, "Robot Wi-Fi network specifier unavailable; checking default Wi-Fi")
                    checkExistingWifiConnection()
                }
            }

            networkCallback?.let {
                connectivityManager.requestNetwork(request, it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to robot Wi-Fi: ${e.message}")
            _statusMessage.value = "ERROR: ${e.message}"
        }
    }

    private fun acquireLocks() {
        try {
            if (wifiLock == null) {
                @Suppress("DEPRECATION")
                wifiLock = wifiManager.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                    WIFI_LOCK_TAG
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
                Log.i(TAG, "Acquired WIFI_MODE_FULL_LOW_LATENCY")
            }

            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    WAKE_LOCK_TAG
                ).apply {
                    setReferenceCounted(false)
                    acquire(12 * 60 * 60 * 1000L) // 12 hours
                }
                Log.i(TAG, "Acquired PARTIAL_WAKE_LOCK")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring locks: ${e.message}")
        }
    }

    private fun releaseLocks() {
        try {
            wifiLock?.let {
                if (it.isHeld) it.release()
                wifiLock = null
                Log.i(TAG, "Released WifiLock")
            }
            wakeLock?.let {
                if (it.isHeld) it.release()
                wakeLock = null
                Log.i(TAG, "Released WakeLock")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing locks: ${e.message}")
        }
    }

    fun disconnect() {
        unbindAndRelease()
    }

    fun releaseAll() {
        disconnect()
        try {
            defaultNetworkCallback?.let {
                connectivityManager.unregisterNetworkCallback(it)
                defaultNetworkCallback = null
            }
        } catch (_: Exception) {}
    }
}
