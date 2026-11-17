package com.anik.f6xcontroller.data

/**
 * Supported RC Stick Modes (Mode 1 to Mode 4).
 * Default is Mode 2 (Left: Throttle/Yaw, Right: Pitch/Roll).
 */
enum class StickMode(val id: Int, val label: String) {
    MODE_1(1, "Mode 1 (Right: Throttle/Roll)"),
    MODE_2(2, "Mode 2 (Left: Throttle/Yaw)"),
    MODE_3(3, "Mode 3 (Right: Pitch/Yaw)"),
    MODE_4(4, "Mode 4 (Left: Pitch/Roll)")
}

/**
 * Robot Motor Mixing Mode (Dedicated for Tank-Drive Soccer Bots).
 */
enum class MixingMode(val label: String) {
    ARCADE("Single-Stick Arcade (Right Stick CH1+CH2)"),
    DUAL_TANK("Twin-Stick Tank (Left Track + Right Track)")
}

/**
 * Tank Drive Control, Snapping, Expo & Speed Limiter Settings.
 */
data class TankControlSettings(
    val maxSpeedPercent: Int = 100,          // 20% to 100% (Speed Limiter / Governor)
    val expoPercent: Int = 0,                // -100% to +100% Exponential response curve
    val deadzone: Float = 0.15f,             // 0% to 30% center deadband
    val snapToAxes: Boolean = true,          // Snap X or Y to axis when close to center line
    val snapThreshold: Float = 0.15f,        // 5% to 35% threshold for axis snapping
    val steeringSensitivity: Int = 100,      // 20% to 100% Steering Sensitivity (Dual Rate)
    val invertSteering: Boolean = false,     // Invert Left/Right steering
    val invertThrottle: Boolean = false,     // Invert Forward/Reverse throttle
    val leftStickSnapCenter: Boolean = true, // Snap left stick to center (0,0) when released
    val githubRepo: String = "anik-creates/robosoccer-f6x" // Target GitHub repo for updates
)

/**
 * Auxiliary Widget Types dynamically spawnable on CH5–CH10.
 */
enum class WidgetType(val label: String) {
    SWITCH_2POS("2-Position Switch"),
    SWITCH_3POS("3-Position Switch"),
    ROTARY_SLIDER("Rotary Slider"),
    MOMENTARY_BUTTON("Momentary Button")
}

/**
 * Per-channel FlySky FS-i6X DSP settings.
 */
data class ChannelConfig(
    val channel: Int,
    val name: String,
    val reverse: Boolean = false,
    val subtrim: Int = 0,         // -120 to +120
    val deadband: Float = 0.05f,  // 0.0 to 0.20 (0% to 20%)
    val epaLow: Int = 100,        // 0% to 120%
    val epaHigh: Int = 100,       // 0% to 120%
    val dualRate: Int = 100,      // 0% to 100%
    val expo: Int = 0,            // -100% to +100%
    val autoCenter: Boolean = true // Per-axis spring return vs hold
)

/**
 * Dynamic Widget Layout positioning & scale.
 */
data class WidgetLayout(
    val id: String,
    val channel: Int,
    val type: WidgetType,
    val normalizedX: Float,       // 0.0f (left) to 1.0f (right)
    val normalizedY: Float,       // 0.0f (top) to 1.0f (bottom)
    val scale: Float = 1.0f       // 0.5f (50%) to 1.6f (160%)
)

/**
 * Wi-Fi AP & UDP Connection Settings.
 */
data class ConnectionSettings(
    val ssid: String = "RoboSoccer_S3",
    val passphrase: String = "soccer2026",
    val ip: String = "192.168.4.1",
    val port: Int = 4210
)

/**
 * Live Telemetry Packet Stats from Native Engine.
 */
data class TelemetryStats(
    val txCount: Long = 0,
    val ackCount: Long = 0,
    val lastRttMs: Float = 0.0f,
    val leftPwm: Short = 0,
    val rightPwm: Short = 0,
    val auxPacked: Int = 0
)
