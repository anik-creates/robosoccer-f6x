package com.anik.f6xcontroller.ui.screens

import android.app.Application
import android.content.Context
import android.net.Network
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anik.f6xcontroller.data.*
import com.anik.f6xcontroller.dsp.FsI6xMixer
import com.anik.f6xcontroller.dsp.ProcessedChannels
import com.anik.f6xcontroller.dsp.StickInputs
import com.anik.f6xcontroller.network.AppUpdateManager
import com.anik.f6xcontroller.network.CheckUpdateResult
import com.anik.f6xcontroller.network.UpdateInfo
import com.anik.f6xcontroller.network.WifiLockManager
import com.anik.f6xcontroller.transport.NativeUdpBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HudViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = F6xPreferencesRepository(application)
    val wifiLockManager = WifiLockManager(application)

    // Raw stick inputs from touch
    private val _rawSticks = MutableStateFlow(StickInputs())
    val rawSticks: StateFlow<StickInputs> = _rawSticks.asStateFlow()

    // Auxiliary channel states (CH5..CH10: 0.0f to 1.0f)
    private val _auxValues = MutableStateFlow<Map<Int, Float>>(
        mapOf(5 to 0f, 6 to 0f, 7 to 0f, 8 to 0f, 9 to 0f, 10 to 0f)
    )
    val auxValues: StateFlow<Map<Int, Float>> = _auxValues.asStateFlow()

    // Preferences Flows
    val stickMode: StateFlow<StickMode> = repository.stickModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, StickMode.MODE_2)

    val mixingMode: StateFlow<MixingMode> = repository.mixingModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, MixingMode.ARCADE)

    val connectionSettings: StateFlow<ConnectionSettings> = repository.connectionSettingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionSettings())

    val channelConfigs: StateFlow<Map<Int, ChannelConfig>> = repository.channelConfigsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.getDefaultChannelConfigs())

    val joystickPositions: StateFlow<F6xPreferencesRepository.JoystickPositions> = repository.joystickPositionsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, F6xPreferencesRepository.JoystickPositions())

    val auxWidgets: StateFlow<List<WidgetLayout>> = repository.auxWidgetsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val tankSettings: StateFlow<TankControlSettings> = repository.tankSettingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, TankControlSettings())

    // UI Overlay states
    val isEditMode = MutableStateFlow(false)
    val selectedWidgetId = MutableStateFlow<String?>(null)
    val isMenuOpen = MutableStateFlow(false)
    val isAddChannelOpen = MutableStateFlow(false)
    val isChannelMonitorOpen = MutableStateFlow(false)
    val isQuickSetupOpen = MutableStateFlow(false)
    val isTuningOpen = MutableStateFlow(false)
    val isUpdateDialogOpen = MutableStateFlow(false)

    // GitHub In-App Auto-Update States
    val isCheckingUpdate = MutableStateFlow(false)
    val updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateProgress = MutableStateFlow<Float?>(null)
    val updateError = MutableStateFlow<String?>(null)
    val updateStatusText = MutableStateFlow<String?>("Tap Check to query latest GitHub release")

    // Telemetry from UDP Engine
    private val _telemetry = MutableStateFlow(TelemetryStats())
    val telemetry: StateFlow<TelemetryStats> = _telemetry.asStateFlow()

    // Processed channels for live curve cursor tracking & monitor
    private val _processedChannels = MutableStateFlow(ProcessedChannels())
    val processedChannels: StateFlow<ProcessedChannels> = _processedChannels.asStateFlow()

    // Java DatagramSocket explicitly bound to the Wi-Fi network (Android kernel bypass guarantee)
    @Volatile
    private var boundDatagramSocket: DatagramSocket? = null
    @Volatile
    private var subnetDatagramSocket: DatagramSocket? = null
    @Volatile
    private var fallbackDatagramSocket: DatagramSocket? = null
    private var javaTxSeq: Short = 0
    private var javaTxCount: Long = 0L
    private var javaAckCount: Long = 0L
    private var lastRttMs: Float = 0f
    private val txTimestamps = java.util.concurrent.ConcurrentHashMap<Short, Long>()

    private fun getSubnetDatagramSocket(): DatagramSocket? {
        try {
            if (subnetDatagramSocket != null && !subnetDatagramSocket!!.isClosed) {
                return subnetDatagramSocket
            }
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (iface in interfaces) {
                for (addr in iface.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: ""
                        if (host.startsWith("192.168.4.")) {
                            Log.i("HudViewModel", "Found robot AP subnet IP: $host. Binding socket directly to wlan0...")
                            val s = DatagramSocket(InetSocketAddress(addr, 0)).apply {
                                broadcast = true
                                soTimeout = 10
                            }
                            subnetDatagramSocket = s
                            startReceiveLoop(s)
                            return s
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("HudViewModel", "Subnet socket error: ${e.message}")
        }
        return null
    }

    init {
        // Fallback unbound socket with broadcast enabled for zero-delay transmission
        try {
            fallbackDatagramSocket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 10
            }
        } catch (e: Exception) {
            Log.e("HudViewModel", "Failed to create fallback socket: ${e.message}")
        }

        // Continuous Wi-Fi watchdog: runs every 200 ms to auto-lock process to robot Wi-Fi as soon as connected
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                wifiLockManager.checkExistingWifiConnection()
                delay(200)
            }
        }

        // 1. Initialize native C++ UDP Engine
        NativeUdpBridge.init(connectionSettings.value.ip, connectionSettings.value.port)

        // 2. Wire up Wi-Fi network binding callback: when phone connects to robot AP,
        // restart native socket and bind Java DatagramSocket to the Wi-Fi network
        wifiLockManager.onNetworkBound = { network ->
            Log.i("HudViewModel", "Wi-Fi network bound ($network). Re-binding sockets...")
            bindJavaDatagramSocket(network)
        }

        // Check if Wi-Fi network is already active at startup
        wifiLockManager.activeNetwork.value?.let { network ->
            bindJavaDatagramSocket(network)
        }

        // 3. Check for GitHub updates from anik-creates/robosoccer-f6x on launch
        checkForUpdates()

        // 4. High-speed 100 Hz DSP & Dual UDP update loop (10 ms)
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val sticks = _rawSticks.value
                val mix = mixingMode.value
                val configs = channelConfigs.value
                val auxMap = _auxValues.value
                val tank = tankSettings.value
                val conn = connectionSettings.value

                // A. Map physical sticks to logical RC channels (CH1..CH4) with non-restricting snap & deadzone
                val mapped = FsI6xMixer.mapSticksToChannels(sticks, tank)

                // B. Run each channel through FlySky FS-i6X DSP pipeline
                val c1 = configs[1] ?: ChannelConfig(1, "CH1")
                val c2 = configs[2] ?: ChannelConfig(2, "CH2")
                val c3 = configs[3] ?: ChannelConfig(3, "CH3")
                val c4 = configs[4] ?: ChannelConfig(4, "CH4")

                val ch1Out = FsI6xMixer.processChannel(mapped.ch1Aileron, c1)
                val ch2Out = FsI6xMixer.processChannel(mapped.ch2Elevator, c2)
                val ch3Out = FsI6xMixer.processChannel(mapped.ch3Throttle, c3)
                val ch4Out = FsI6xMixer.processChannel(mapped.ch4Rudder, c4)

                _processedChannels.value = ProcessedChannels(ch1Out, ch2Out, ch3Out, ch4Out)

                // C. Motor mixing for 4WD Tank Soccerbot:
                val throttle = ch2Out
                val steering = ch1Out

                val (leftPwm, rightPwm) = FsI6xMixer.mixMotors(
                    mixingMode = mix,
                    throttle = throttle,
                    steering = steering,
                    leftStickY = sticks.leftY,
                    rightStickY = sticks.rightY,
                    tankSettings = tank
                )

                // D. Pack Aux Channels (CH5..CH10)
                val auxPacked = FsI6xMixer.packAux(auxMap)

                // E. Path 1: Update native NDK state
                NativeUdpBridge.updateControls(leftPwm, rightPwm, auxPacked)

                // F. Path 2: Direct Java DatagramSocket transmission (bound to Wi-Fi network)
                sendViaJavaSocket(conn.ip, conn.port, leftPwm, rightPwm, auxPacked)

                // G. Update telemetry state (accurate live stats from network-bound socket)
                _telemetry.value = TelemetryStats(
                    txCount = javaTxCount,
                    ackCount = javaAckCount,
                    lastRttMs = lastRttMs,
                    leftPwm = leftPwm,
                    rightPwm = rightPwm,
                    auxPacked = auxPacked
                )

                delay(10) // 100 Hz deterministic rate
            }
        }
    }

    private fun bindJavaDatagramSocket(network: Network) {
        try {
            boundDatagramSocket?.close()
            val socket = DatagramSocket()
            network.bindSocket(socket)
            socket.soTimeout = 10
            boundDatagramSocket = socket
            Log.i("HudViewModel", "Java DatagramSocket successfully bound to network: $network")
            startReceiveLoop(socket)
        } catch (e: Exception) {
            Log.e("HudViewModel", "Failed to bind Java DatagramSocket: ${e.message}")
        }
    }

    private fun startReceiveLoop(socket: DatagramSocket) {
        viewModelScope.launch(Dispatchers.IO) {
            val rxBuf = ByteArray(32)
            val rxPacket = DatagramPacket(rxBuf, rxBuf.size)
            while (isActive && !socket.isClosed) {
                try {
                    socket.receive(rxPacket)
                    if (rxPacket.length >= 4) {
                        val magic = (rxBuf[0].toInt() and 0xFF) or ((rxBuf[1].toInt() and 0xFF) shl 8)
                        if (magic == 0x55AA) {
                            val ackSeq = ((rxBuf[2].toInt() and 0xFF) or ((rxBuf[3].toInt() and 0xFF) shl 8)).toShort()
                            val sendTime = txTimestamps.remove(ackSeq)
                            if (sendTime != null) {
                                val rtt = (System.currentTimeMillis() - sendTime).toFloat()
                                if (rtt in 0f..500f) {
                                    lastRttMs = rtt
                                }
                            }
                            javaAckCount++
                            wifiLockManager.checkExistingWifiConnection()
                        }
                    }
                } catch (_: Exception) {
                    // SocketTimeoutException expected on non-blocking receive
                }
            }
        }
    }

    private fun sendViaJavaSocket(ip: String, port: Int, left: Short, right: Short, aux: Int) {
        try {
            javaTxSeq = (javaTxSeq + 1).toShort()
            val buf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            buf.putShort(0xAA55.toShort()) // Magic
            buf.putShort(javaTxSeq)        // Seq
            buf.putShort(left)             // Left PWM
            buf.putShort(right)            // Right PWM
            buf.putShort(aux.toShort())    // Aux packed

            // CRC-16 over first 10 bytes
            val bytes = buf.array()
            var crc = 0xFFFF
            for (i in 0 until 10) {
                crc = crc xor (bytes[i].toInt() and 0xFF)
                for (b in 0 until 8) {
                    crc = if ((crc and 0x0001) != 0) {
                        (crc ushr 1) xor 0xA001
                    } else {
                        crc ushr 1
                    }
                }
            }
            buf.putShort(crc.toShort())

            val targetIp = if (ip.isBlank()) "192.168.4.1" else ip
            val targetAddr = InetAddress.getByName(targetIp)
            val packet = DatagramPacket(bytes, 12, targetAddr, port)

            // Select single best socket to prevent duplicate packet flooding and WiFi contention:
            val activeSocket = when {
                boundDatagramSocket != null && !boundDatagramSocket!!.isClosed -> boundDatagramSocket
                getSubnetDatagramSocket() != null -> getSubnetDatagramSocket()
                else -> fallbackDatagramSocket
            }

            activeSocket?.let { s ->
                try {
                    s.send(packet)
                    javaTxCount++
                    txTimestamps[javaTxSeq] = System.currentTimeMillis()

                    // Broadcast only once every 128 packets (~1.28s) for background discovery
                    if ((javaTxSeq.toInt() and 0x7F) == 0) {
                        val bcastAddr = InetAddress.getByName("192.168.4.255")
                        val bcastPacket = DatagramPacket(bytes, 12, bcastAddr, port)
                        s.send(bcastPacket)
                    }
                } catch (e: Exception) {
                    Log.w("HudViewModel", "Socket send error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("HudViewModel", "sendViaJavaSocket error: ${e.message}")
        }
    }

    fun checkForUpdates(customRepo: String? = null) {
        val repo = customRepo ?: tankSettings.value.githubRepo
        viewModelScope.launch {
            isCheckingUpdate.value = true
            updateStatusText.value = "Connecting to GitHub ($repo)..."
            updateError.value = null
            try {
                when (val result = AppUpdateManager.checkLatestRelease(repo)) {
                    is CheckUpdateResult.UpdateAvailable -> {
                        updateInfo.value = result.info
                        updateStatusText.value = "Update Available: ${result.info.tagName}"
                    }
                    is CheckUpdateResult.UpToDate -> {
                        updateInfo.value = null
                        updateStatusText.value = "App is up to date (${result.currentVersion})"
                    }
                    is CheckUpdateResult.Error -> {
                        updateInfo.value = null
                        updateError.value = result.message
                        updateStatusText.value = "Check failed: ${result.message}"
                    }
                }
            } catch (e: Exception) {
                updateError.value = e.message
                updateStatusText.value = "Error: ${e.message}"
            } finally {
                isCheckingUpdate.value = false
            }
        }
    }

    fun downloadAndInstallUpdate(context: Context) {
        val info = updateInfo.value ?: return
        viewModelScope.launch {
            updateProgress.value = 0.0f
            updateStatusText.value = "Downloading ${info.tagName}..."
            try {
                AppUpdateManager.downloadAndInstallApk(
                    context = context,
                    apkUrl = info.apkDownloadUrl,
                    onProgress = { progress ->
                        updateProgress.value = progress
                        updateStatusText.value = "Downloading: ${(progress * 100).toInt()}%"
                    },
                    onError = { err ->
                        updateError.value = err
                        updateStatusText.value = "Download failed: $err"
                    }
                )
                updateStatusText.value = "Download complete. Starting installer..."
            } catch (e: Exception) {
                updateError.value = e.message
                updateStatusText.value = "Download failed: ${e.message}"
            } finally {
                updateProgress.value = null
            }
        }
    }

    // Stick touch handling
    fun updateLeftStick(x: Float, y: Float) {
        _rawSticks.value = _rawSticks.value.copy(leftX = x, leftY = y)
    }

    fun updateRightStick(x: Float, y: Float) {
        _rawSticks.value = _rawSticks.value.copy(rightX = x, rightY = y)
    }

    // Aux control handling
    fun updateAuxChannel(channel: Int, value: Float) {
        val current = _auxValues.value.toMutableMap()
        current[channel] = value
        _auxValues.value = current
    }

    // Emergency stop / Brake
    fun emergencyStop() {
        updateRightStick(0f, 0f)
        updateLeftStick(0f, 0f)
        NativeUdpBridge.updateControls(0, 0, 0)
    }

    // Edit HUD operations
    fun toggleEditMode() {
        isEditMode.value = !isEditMode.value
        if (!isEditMode.value) {
            selectedWidgetId.value = null
        }
    }

    fun selectWidget(id: String) {
        selectedWidgetId.value = id
    }

    fun updateWidgetScale(scale: Float) {
        val selId = selectedWidgetId.value ?: return
        viewModelScope.launch {
            when (selId) {
                "STICK_LEFT" -> {
                    val current = joystickPositions.value
                    repository.saveLeftJoystick(current.leftX, current.leftY, scale)
                }
                "STICK_RIGHT" -> {
                    val current = joystickPositions.value
                    repository.saveRightJoystick(current.rightX, current.rightY, scale)
                }
                else -> {
                    val widgets = auxWidgets.value.map { w ->
                        if (w.id == selId) w.copy(scale = scale) else w
                    }
                    repository.saveAuxWidgets(widgets)
                }
            }
        }
    }

    fun updateWidgetPosition(id: String, normX: Float, normY: Float) {
        viewModelScope.launch {
            when (id) {
                "STICK_LEFT" -> {
                    val current = joystickPositions.value
                    repository.saveLeftJoystick(normX, normY, current.leftScale)
                }
                "STICK_RIGHT" -> {
                    val current = joystickPositions.value
                    repository.saveRightJoystick(normX, normY, current.rightScale)
                }
                else -> {
                    val widgets = auxWidgets.value.map { w ->
                        if (w.id == id) w.copy(normalizedX = normX, normalizedY = normY) else w
                    }
                    repository.saveAuxWidgets(widgets)
                }
            }
        }
    }

    fun addAuxWidget(channel: Int, type: WidgetType) {
        val current = auxWidgets.value
        val newId = "AUX_${channel}_${System.currentTimeMillis() % 1000}"
        val newWidget = WidgetLayout(
            id = newId,
            channel = channel,
            type = type,
            normalizedX = 0.5f,
            normalizedY = 0.55f,
            scale = 1.0f
        )
        viewModelScope.launch {
            repository.saveAuxWidgets(current + newWidget)
        }
        isAddChannelOpen.value = false
    }

    fun deleteSelectedWidget() {
        val selId = selectedWidgetId.value ?: return
        if (selId.startsWith("AUX_")) {
            viewModelScope.launch {
                val updated = auxWidgets.value.filterNot { it.id == selId }
                repository.saveAuxWidgets(updated)
                selectedWidgetId.value = null
            }
        }
    }

    fun updateTankSettings(settings: TankControlSettings) {
        viewModelScope.launch { repository.saveTankSettings(settings) }
    }

    fun setStickMode(mode: StickMode) {
        viewModelScope.launch { repository.saveStickMode(mode) }
    }

    fun setMixingMode(mode: MixingMode) {
        viewModelScope.launch { repository.saveMixingMode(mode) }
    }

    fun updateConnectionSettings(settings: ConnectionSettings) {
        viewModelScope.launch {
            repository.saveConnectionSettings(settings)
            NativeUdpBridge.setTarget(settings.ip, settings.port)
        }
    }

    fun updateChannelConfig(config: ChannelConfig) {
        viewModelScope.launch {
            repository.saveChannelConfig(config)
        }
    }

    fun lockWifi() {
        val s = connectionSettings.value
        wifiLockManager.lockToRobotWifi(s.ssid, s.passphrase)
    }

    fun unlockWifi() {
        wifiLockManager.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        NativeUdpBridge.stop()
        boundDatagramSocket?.close()
        wifiLockManager.disconnect()
    }
}
