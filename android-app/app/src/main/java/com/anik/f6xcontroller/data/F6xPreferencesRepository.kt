package com.anik.f6xcontroller.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "f6x_settings")

class F6xPreferencesRepository(private val context: Context) {

    private object Keys {
        val STICK_MODE = intPreferencesKey("stick_mode")
        val MIXING_MODE = stringPreferencesKey("mixing_mode")

        val WIFI_SSID = stringPreferencesKey("wifi_ssid")
        val WIFI_PASS = stringPreferencesKey("wifi_pass")
        val ROBOT_IP = stringPreferencesKey("robot_ip")
        val UDP_PORT = intPreferencesKey("udp_port")

        // Joystick placements
        val LEFT_STICK_X = floatPreferencesKey("left_stick_x")
        val LEFT_STICK_Y = floatPreferencesKey("left_stick_y")
        val LEFT_STICK_SCALE = floatPreferencesKey("left_stick_scale")

        val RIGHT_STICK_X = floatPreferencesKey("right_stick_x")
        val RIGHT_STICK_Y = floatPreferencesKey("right_stick_y")
        val RIGHT_STICK_SCALE = floatPreferencesKey("right_stick_scale")

        // Serialized Aux Widgets
        val AUX_WIDGETS_JSON = stringPreferencesKey("aux_widgets_data")

        // Tank Drive Settings
        val MAX_SPEED_PERCENT = intPreferencesKey("max_speed_percent")
        val EXPO_PERCENT = intPreferencesKey("expo_percent")
        val STEERING_SENSITIVITY = intPreferencesKey("steering_sensitivity")
        val SNAP_TO_AXES = booleanPreferencesKey("snap_to_axes")
        val SNAP_THRESHOLD = floatPreferencesKey("snap_threshold")
        val TANK_DEADZONE = floatPreferencesKey("tank_deadzone")
        val INVERT_STEERING = booleanPreferencesKey("invert_steering")
        val INVERT_THROTTLE = booleanPreferencesKey("invert_throttle")
        val LEFT_STICK_SNAP_CENTER = booleanPreferencesKey("left_stick_snap_center")
        val GITHUB_REPO = stringPreferencesKey("github_repo")
    }

    // Default 10 Channels setup
    fun getDefaultChannelConfigs(): Map<Int, ChannelConfig> {
        val names = mapOf(
            1 to "CH1 (Aileron / Roll)",
            2 to "CH2 (Elevator / Pitch)",
            3 to "CH3 (Throttle)",
            4 to "CH4 (Rudder / Yaw)",
            5 to "CH5 (Aux 1 / Kicker)",
            6 to "CH6 (Aux 2 / Dribbler)",
            7 to "CH7 (Aux 3)",
            8 to "CH8 (Aux 4)",
            9 to "CH9 (Aux 5)",
            10 to "CH10 (Aux 6)"
        )
        return (1..10).associateWith { ch ->
            ChannelConfig(
                channel = ch,
                name = names[ch] ?: "CH$ch",
                reverse = false,
                subtrim = 0,
                deadband = if (ch in 1..4) 0.05f else 0.0f,
                epaLow = 100,
                epaHigh = 100,
                dualRate = 100,
                expo = 0,
                autoCenter = ch != 3 // Throttle defaults to hold if desired, or toggleable
            )
        }
    }

    val stickModeFlow: Flow<StickMode> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val id = prefs[Keys.STICK_MODE] ?: StickMode.MODE_2.id
            StickMode.entries.find { it.id == id } ?: StickMode.MODE_2
        }

    suspend fun saveStickMode(mode: StickMode) {
        context.dataStore.edit { it[Keys.STICK_MODE] = mode.id }
    }

    val mixingModeFlow: Flow<MixingMode> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val name = prefs[Keys.MIXING_MODE] ?: MixingMode.ARCADE.name
            runCatching { MixingMode.valueOf(name) }.getOrDefault(MixingMode.ARCADE)
        }

    suspend fun saveMixingMode(mode: MixingMode) {
        context.dataStore.edit { it[Keys.MIXING_MODE] = mode.name }
    }

    val connectionSettingsFlow: Flow<ConnectionSettings> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            ConnectionSettings(
                ssid = prefs[Keys.WIFI_SSID] ?: "RoboSoccer_S3",
                passphrase = prefs[Keys.WIFI_PASS] ?: "soccer2026",
                ip = prefs[Keys.ROBOT_IP] ?: "192.168.4.1",
                port = prefs[Keys.UDP_PORT] ?: 4210
            )
        }

    suspend fun saveConnectionSettings(settings: ConnectionSettings) {
        context.dataStore.edit {
            it[Keys.WIFI_SSID] = settings.ssid
            it[Keys.WIFI_PASS] = settings.passphrase
            it[Keys.ROBOT_IP] = settings.ip
            it[Keys.UDP_PORT] = settings.port
        }
    }

    // Joystick Layout State
    data class JoystickPositions(
        val leftX: Float = 0.16f,
        val leftY: Float = 0.60f,
        val leftScale: Float = 1.0f,
        val rightX: Float = 0.84f,
        val rightY: Float = 0.60f,
        val rightScale: Float = 1.0f
    )

    val joystickPositionsFlow: Flow<JoystickPositions> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            JoystickPositions(
                leftX = prefs[Keys.LEFT_STICK_X] ?: 0.16f,
                leftY = prefs[Keys.LEFT_STICK_Y] ?: 0.60f,
                leftScale = prefs[Keys.LEFT_STICK_SCALE] ?: 1.0f,
                rightX = prefs[Keys.RIGHT_STICK_X] ?: 0.84f,
                rightY = prefs[Keys.RIGHT_STICK_Y] ?: 0.60f,
                rightScale = prefs[Keys.RIGHT_STICK_SCALE] ?: 1.0f
            )
        }

    suspend fun saveLeftJoystick(x: Float, y: Float, scale: Float) {
        context.dataStore.edit {
            it[Keys.LEFT_STICK_X] = x
            it[Keys.LEFT_STICK_Y] = y
            it[Keys.LEFT_STICK_SCALE] = scale
        }
    }

    suspend fun saveRightJoystick(x: Float, y: Float, scale: Float) {
        context.dataStore.edit {
            it[Keys.RIGHT_STICK_X] = x
            it[Keys.RIGHT_STICK_Y] = y
            it[Keys.RIGHT_STICK_SCALE] = scale
        }
    }

    // Per-Channel Config Keys
    private fun chKey(ch: Int, param: String) = stringPreferencesKey("ch_${ch}_$param")

    fun channelConfigsFlow(): Flow<Map<Int, ChannelConfig>> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val defaults = getDefaultChannelConfigs()
            defaults.mapValues { (ch, def) ->
                val rev = prefs[booleanPreferencesKey("ch_${ch}_rev")] ?: def.reverse
                val subtrim = prefs[intPreferencesKey("ch_${ch}_subtrim")] ?: def.subtrim
                val deadband = prefs[floatPreferencesKey("ch_${ch}_deadband")] ?: def.deadband
                val epaLow = prefs[intPreferencesKey("ch_${ch}_epa_low")] ?: def.epaLow
                val epaHigh = prefs[intPreferencesKey("ch_${ch}_epa_high")] ?: def.epaHigh
                val dualRate = prefs[intPreferencesKey("ch_${ch}_dual_rate")] ?: def.dualRate
                val expo = prefs[intPreferencesKey("ch_${ch}_expo")] ?: def.expo
                val autoCenter = prefs[booleanPreferencesKey("ch_${ch}_autocenter")] ?: def.autoCenter

                def.copy(
                    reverse = rev,
                    subtrim = subtrim,
                    deadband = deadband,
                    epaLow = epaLow,
                    epaHigh = epaHigh,
                    dualRate = dualRate,
                    expo = expo,
                    autoCenter = autoCenter
                )
            }
        }

    suspend fun saveChannelConfig(config: ChannelConfig) {
        val ch = config.channel
        context.dataStore.edit { prefs ->
            prefs[booleanPreferencesKey("ch_${ch}_rev")] = config.reverse
            prefs[intPreferencesKey("ch_${ch}_subtrim")] = config.subtrim
            prefs[floatPreferencesKey("ch_${ch}_deadband")] = config.deadband
            prefs[intPreferencesKey("ch_${ch}_epa_low")] = config.epaLow
            prefs[intPreferencesKey("ch_${ch}_epa_high")] = config.epaHigh
            prefs[intPreferencesKey("ch_${ch}_dual_rate")] = config.dualRate
            prefs[intPreferencesKey("ch_${ch}_expo")] = config.expo
            prefs[booleanPreferencesKey("ch_${ch}_autocenter")] = config.autoCenter
        }
    }

    // Aux Widgets Layout Persistence (delimited encoding: id|channel|type|x|y|scale;...)
    val auxWidgetsFlow: Flow<List<WidgetLayout>> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val raw = prefs[Keys.AUX_WIDGETS_JSON] ?: ""
            if (raw.isBlank() || raw.contains("AUX_5|5|MOMENTARY_BUTTON")) {
                // By user request: Clean start with NO initial C5 / C6 aux widgets
                emptyList()
            } else {
                raw.split(";").mapNotNull { item ->
                    val tokens = item.split("|")
                    if (tokens.size >= 6) {
                        val id = tokens[0]
                        val ch = tokens[1].toIntOrNull() ?: 5
                        val type = runCatching { WidgetType.valueOf(tokens[2]) }.getOrDefault(WidgetType.SWITCH_2POS)
                        val x = tokens[3].toFloatOrNull() ?: 0.5f
                        val y = tokens[4].toFloatOrNull() ?: 0.5f
                        val scale = tokens[5].toFloatOrNull() ?: 1.0f
                        WidgetLayout(id, ch, type, x, y, scale)
                    } else null
                }
            }
        }

    suspend fun saveAuxWidgets(widgets: List<WidgetLayout>) {
        val encoded = widgets.joinToString(";") {
            "${it.id}|${it.channel}|${it.type.name}|${it.normalizedX}|${it.normalizedY}|${it.scale}"
        }
        context.dataStore.edit { it[Keys.AUX_WIDGETS_JSON] = encoded }
    }

    // Tank Drive Settings Persistence
    val tankSettingsFlow: Flow<TankControlSettings> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            TankControlSettings(
                maxSpeedPercent = prefs[Keys.MAX_SPEED_PERCENT] ?: 100,
                expoPercent = prefs[Keys.EXPO_PERCENT] ?: 0,
                deadzone = prefs[Keys.TANK_DEADZONE] ?: 0.15f,
                snapToAxes = prefs[Keys.SNAP_TO_AXES] ?: true,
                snapThreshold = prefs[Keys.SNAP_THRESHOLD] ?: 0.15f,
                steeringSensitivity = prefs[Keys.STEERING_SENSITIVITY] ?: 100,
                invertSteering = prefs[Keys.INVERT_STEERING] ?: false,
                invertThrottle = prefs[Keys.INVERT_THROTTLE] ?: false,
                leftStickSnapCenter = prefs[Keys.LEFT_STICK_SNAP_CENTER] ?: true,
                githubRepo = prefs[Keys.GITHUB_REPO] ?: "anik-creates/robosoccer-f6x"
            )
        }

    suspend fun saveTankSettings(settings: TankControlSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MAX_SPEED_PERCENT] = settings.maxSpeedPercent
            prefs[Keys.EXPO_PERCENT] = settings.expoPercent
            prefs[Keys.TANK_DEADZONE] = settings.deadzone
            prefs[Keys.SNAP_TO_AXES] = settings.snapToAxes
            prefs[Keys.SNAP_THRESHOLD] = settings.snapThreshold
            prefs[Keys.STEERING_SENSITIVITY] = settings.steeringSensitivity
            prefs[Keys.INVERT_STEERING] = settings.invertSteering
            prefs[Keys.INVERT_THROTTLE] = settings.invertThrottle
            prefs[Keys.LEFT_STICK_SNAP_CENTER] = settings.leftStickSnapCenter
            prefs[Keys.GITHUB_REPO] = settings.githubRepo
        }
    }
}
