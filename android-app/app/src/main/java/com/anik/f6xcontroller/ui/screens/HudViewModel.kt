package com.anik.f6xcontroller.ui.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anik.f6xcontroller.data.*
import com.anik.f6xcontroller.dsp.FsI6xMixer
import com.anik.f6xcontroller.dsp.ProcessedChannels
import com.anik.f6xcontroller.dsp.StickInputs
import com.anik.f6xcontroller.network.AppUpdateManager
import com.anik.f6xcontroller.network.UpdateInfo
import com.anik.f6xcontroller.network.WifiLockManager
import com.anik.f6xcontroller.transport.NativeUdpBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    // Telemetry from C++ Engine
    private val _telemetry = MutableStateFlow(TelemetryStats())
    val telemetry: StateFlow<TelemetryStats> = _telemetry.asStateFlow()

    // Processed channels for live curve cursor tracking & monitor
    private val _processedChannels = MutableStateFlow(ProcessedChannels())
    val processedChannels: StateFlow<ProcessedChannels> = _processedChannels.asStateFlow()

    init {
        // 1. Initialize native C++ UDP Engine
        NativeUdpBridge.init(connectionSettings.value.ip, connectionSettings.value.port)
        NativeUdpBridge.start()

        // 2. Automatically bind & lock to robot Wi-Fi AP (no manual step required)
        viewModelScope.launch {
            delay(500) // Brief delay for network stack initialization
            val s = connectionSettings.value
            wifiLockManager.lockToRobotWifi(s.ssid, s.passphrase)
        }

        // 3. Check for GitHub updates from aniksarker/robosoccer-f6x on launch
        checkForUpdates()

        // 4. High-speed 100 Hz DSP & JNI update loop (10 ms)
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val sticks = _rawSticks.value
                val mix = mixingMode.value
                val configs = channelConfigs.value
                val auxMap = _auxValues.value
                val tank = tankSettings.value

                // A. Map physical sticks to logical RC channels (CH1..CH4) with 15% snap & deadzone
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
                // Primary: Right Stick Controls: CH2 (Throttle Forward/Reverse) + CH1 (Steering Left/Right)
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

                // E. Pass directly to C++ NDK Engine via JNI
                NativeUdpBridge.updateControls(leftPwm, rightPwm, auxPacked)

                // F. Update telemetry state
                _telemetry.value = TelemetryStats(
                    txCount = NativeUdpBridge.getTxCount(),
                    ackCount = NativeUdpBridge.getAckCount(),
                    lastRttMs = NativeUdpBridge.getLastRttMs(),
                    leftPwm = leftPwm,
                    rightPwm = rightPwm,
                    auxPacked = auxPacked
                )

                delay(10) // 100 Hz deterministic rate
            }
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
                    is com.anik.f6xcontroller.network.CheckUpdateResult.UpdateAvailable -> {
                        updateInfo.value = result.info
                        updateStatusText.value = "New release available: ${result.info.tagName}!"
                    }
                    is com.anik.f6xcontroller.network.CheckUpdateResult.UpToDate -> {
                        updateInfo.value = null
                        updateStatusText.value = "App is already up-to-date (v${result.currentVersion})."
                    }
                    is com.anik.f6xcontroller.network.CheckUpdateResult.Error -> {
                        updateInfo.value = null
                        updateStatusText.value = result.message
                        updateError.value = result.message
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
            updateProgress.value = 0f
            AppUpdateManager.downloadAndInstallApk(
                context = context,
                apkUrl = info.apkDownloadUrl,
                onProgress = { p -> updateProgress.value = p },
                onError = { err ->
                    updateError.value = err
                    updateProgress.value = null
                }
            )
        }
    }

    fun updateLeftStick(x: Float, y: Float) {
        _rawSticks.update { it.copy(leftX = x, leftY = y) }
    }

    fun updateRightStick(x: Float, y: Float) {
        _rawSticks.update { it.copy(rightX = x, rightY = y) }
    }

    fun updateAuxChannel(channel: Int, value: Float) {
        _auxValues.update { current ->
            current.toMutableMap().apply { put(channel, value.coerceIn(0f, 1f)) }
        }
    }

    fun emergencyStop() {
        _rawSticks.value = StickInputs(0f, 0f, 0f, 0f)
        _auxValues.value = mapOf(5 to 0f, 6 to 0f, 7 to 0f, 8 to 0f, 9 to 0f, 10 to 0f)
        NativeUdpBridge.updateControls(0, 0, 0)
    }

    fun toggleAutoCenterLeft() {
        val configs = channelConfigs.value
        val ch3 = configs[3] ?: return
        viewModelScope.launch {
            repository.saveChannelConfig(ch3.copy(autoCenter = !ch3.autoCenter))
        }
    }

    fun toggleAutoCenterRight() {
        val configs = channelConfigs.value
        val ch2 = configs[2] ?: return
        viewModelScope.launch {
            repository.saveChannelConfig(ch2.copy(autoCenter = !ch2.autoCenter))
        }
    }

    // Layout editing methods
    fun toggleEditMode() {
        val next = !isEditMode.value
        isEditMode.value = next
        if (!next) {
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
                    val pos = joystickPositions.value
                    repository.saveLeftJoystick(pos.leftX, pos.leftY, scale)
                }
                "STICK_RIGHT" -> {
                    val pos = joystickPositions.value
                    repository.saveRightJoystick(pos.rightX, pos.rightY, scale)
                }
                else -> {
                    val updated = auxWidgets.value.map {
                        if (it.id == selId) it.copy(scale = scale) else it
                    }
                    repository.saveAuxWidgets(updated)
                }
            }
        }
    }

    fun updateWidgetPosition(id: String, normX: Float, normY: Float) {
        viewModelScope.launch {
            when (id) {
                "STICK_LEFT" -> {
                    val pos = joystickPositions.value
                    repository.saveLeftJoystick(normX.coerceIn(0.05f, 0.95f), normY.coerceIn(0.15f, 0.95f), pos.leftScale)
                }
                "STICK_RIGHT" -> {
                    val pos = joystickPositions.value
                    repository.saveRightJoystick(normX.coerceIn(0.05f, 0.95f), normY.coerceIn(0.15f, 0.95f), pos.rightScale)
                }
                else -> {
                    val updated = auxWidgets.value.map {
                        if (it.id == id) it.copy(
                            normalizedX = normX.coerceIn(0.05f, 0.95f),
                            normalizedY = normY.coerceIn(0.15f, 0.95f)
                        ) else it
                    }
                    repository.saveAuxWidgets(updated)
                }
            }
        }
    }

    fun addAuxWidget(channel: Int, type: WidgetType) {
        val current = auxWidgets.value
        val newId = "AUX_${channel}_${System.currentTimeMillis() % 1000}"
        // Default position in central region
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

    // Settings adjustments
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
        wifiLockManager.disconnect()
    }
}
