package com.anik.f6xcontroller.dsp

import com.anik.f6xcontroller.data.ChannelConfig
import com.anik.f6xcontroller.data.MixingMode
import com.anik.f6xcontroller.data.StickMode
import com.anik.f6xcontroller.data.TankControlSettings
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

data class StickInputs(
    val leftX: Float = 0f,
    val leftY: Float = 0f,
    val rightX: Float = 0f,
    val rightY: Float = 0f
)

data class ProcessedChannels(
    val ch1Aileron: Float = 0f,   // CH1: Right Stick X (Steering / Turn)
    val ch2Elevator: Float = 0f,  // CH2: Right Stick Y (Throttle / Forward-Reverse)
    val ch3Throttle: Float = 0f,  // CH3: Left Stick Y (Left Track / Aux)
    val ch4Rudder: Float = 0f     // CH4: Left Stick X (Yaw / Trim)
)

object FsI6xMixer {

    /**
     * Applies Cardinal Axis Snapping & Deadzone filter:
     * - Strict Deadzone: radial (hypot <= dz) ensuring the stick outputs EXACTLY 0.0f
     *   within the deadzone with zero drift or motor creep.
     * - Smooth Control-Assist Axis Snapping:
     *   * S-curve cubic Hermite blending: smoothly attenuates lateral drift when driving forward/backward
     *     and longitudinal drift when turning.
     *   * Never restricts or traps the user: transitioning across axes is 100% fluid and continuous.
     */
    fun applySnappingAndDeadzone(
        x: Float,
        y: Float,
        settings: TankControlSettings
    ): Pair<Float, Float> {
        val dz = settings.deadzone.coerceIn(0.0f, 0.40f)
        val snapThresh = settings.snapThreshold.coerceIn(0.02f, 0.40f)

        // 1. Center radial deadzone check: strictly (0, 0) inside circle
        val dist = hypot(x, y)
        if (dist <= dz) {
            return Pair(0f, 0f)
        }

        // Rescale radial magnitude smoothly from 0.0f upward outside deadzone (no step jump)
        val scaledMag = ((dist - dz) / (1.0f - dz)).coerceIn(0f, 1f)
        val normX = (x / dist) * scaledMag
        val normY = (y / dist) * scaledMag

        var finalX = normX
        var finalY = normY

        // 2. Control assist (snapping without restriction or trapping):
        if (settings.snapToAxes) {
            // When driving predominantly forward/reverse (|normY| >= snapThresh):
            // Smoothly guide lateral drift towards 0 with an S-curve transition
            if (abs(normY) >= snapThresh) {
                val ratioX = (abs(normX) / snapThresh).coerceIn(0f, 1f)
                val smoothFactor = ratioX * ratioX * (3.0f - 2.0f * ratioX) // Smooth Hermite S-curve
                finalX = normX * smoothFactor
            }
            // When turning/spinning (|normX| >= snapThresh):
            // Smoothly guide longitudinal drift towards 0 with an S-curve transition
            if (abs(normX) >= snapThresh) {
                val ratioY = (abs(normY) / snapThresh).coerceIn(0f, 1f)
                val smoothFactor = ratioY * ratioY * (3.0f - 2.0f * ratioY) // Smooth Hermite S-curve
                finalY = normY * smoothFactor
            }
        }

        return Pair(finalX.coerceIn(-1f, 1f), finalY.coerceIn(-1f, 1f))
    }

    /**
     * Map physical screen joystick coordinates to CH1–CH4 for Tank Soccerbots:
     * Right Joystick: CH1 (X - Steering), CH2 (Y - Throttle)
     * Left Joystick:  CH4 (X - Yaw Trim), CH3 (Y - Direct Track / Aux)
     */
    fun mapSticksToChannels(
        sticks: StickInputs,
        tankSettings: TankControlSettings
    ): ProcessedChannels {
        // Apply Axis Snapping & Deadzone to Right Driving Stick
        val (rightX, rightY) = applySnappingAndDeadzone(
            sticks.rightX,
            sticks.rightY,
            tankSettings
        )

        // Left stick uses deadzone
        val leftDz = tankSettings.deadzone.coerceIn(0.0f, 0.40f)
        val leftX = if (abs(sticks.leftX) <= leftDz) 0f else sticks.leftX
        val leftY = if (abs(sticks.leftY) <= leftDz) 0f else sticks.leftY

        return ProcessedChannels(
            ch1Aileron = rightX,  // CH1: Right Stick X (Steering)
            ch2Elevator = rightY, // CH2: Right Stick Y (Throttle / Forward-Reverse)
            ch3Throttle = leftY,  // CH3: Left Stick Y
            ch4Rudder = leftX     // CH4: Left Stick X
        )
    }

    /**
     * Complete FlySky FS-i6X Per-Channel DSP Pipeline:
     * Raw -> Deadband -> Reverse -> Subtrim -> D/R & Expo -> EPA.
     */
    fun processChannel(rawInput: Float, config: ChannelConfig): Float {
        var x = rawInput.coerceIn(-1.0f, 1.0f)

        // 1. Deadband filter
        val db = config.deadband.coerceIn(0.0f, 0.4f)
        if (abs(x) <= db) {
            x = 0.0f
        } else {
            val sign = if (x > 0f) 1.0f else -1.0f
            x = sign * ((abs(x) - db) / (1.0f - db))
        }

        // 2. Channel Reverse (REV/NOR)
        if (config.reverse) {
            x = -x
        }

        // 3. Subtrim (-120 to +120 -> scaled to ±0.25 max offset)
        if (config.subtrim != 0) {
            val subtrimOffset = (config.subtrim / 120.0f) * 0.25f
            x = (x + subtrimOffset).coerceIn(-1.0f, 1.0f)
        }

        // 4. Dual Rate (0..100%) and Exponential Curve (-100%..+100%)
        // Formula: y = DR * ((1 - expo) * x + expo * x^3)
        val dr = (config.dualRate / 100.0f).coerceIn(0.0f, 1.0f)
        val expo = (config.expo / 100.0f).coerceIn(-1.0f, 1.0f)
        var y = dr * ((1.0f - expo) * x + expo * (x * x * x))

        // 5. Asymmetric End-Point Adjustment (EPA Low / EPA High: 0% to 120%)
        y = if (y < 0f) {
            y * (config.epaLow / 100.0f)
        } else {
            y * (config.epaHigh / 100.0f)
        }

        return y.coerceIn(-1.0f, 1.0f)
    }

    /**
     * Compute FlySky Exponential curve point for graph preview.
     */
    fun computeCurvePoint(input: Float, dualRate: Int, expo: Int): Float {
        val x = input.coerceIn(-1.0f, 1.0f)
        val dr = (dualRate / 100.0f).coerceIn(0.0f, 1.0f)
        val ex = (expo / 100.0f).coerceIn(-1.0f, 1.0f)
        return (dr * ((1.0f - ex) * x + ex * (x * x * x))).coerceIn(-1.0f, 1.0f)
    }

    /**
     * 4WD Tank Soccerbot Motor Mixing:
     * - Forward (Throttle > 0): Both Left & Right motors drive forward
     * - Reverse (Throttle < 0): Both Left & Right motors drive reverse
     * - Steer Left (Steering < 0): Left motor reverse, Right motor forward (spin left)
     * - Steer Right (Steering > 0): Right motor reverse, Left motor forward (spin right)
     * Produces final Left and Right motor signed PWM (-255 to +255).
     */
    fun mixMotors(
        mixingMode: MixingMode,
        throttle: Float,
        steering: Float,
        leftStickY: Float,
        rightStickY: Float,
        tankSettings: TankControlSettings = TankControlSettings()
    ): Pair<Short, Short> {
        // Guaranteed zero output when stick is at center / resting in deadzone
        if (throttle == 0f && steering == 0f && mixingMode == MixingMode.ARCADE) {
            return Pair(0.toShort(), 0.toShort())
        }

        val speedLimit = (tankSettings.maxSpeedPercent / 100.0f).coerceIn(0.1f, 1.0f)
        val steerSens = (tankSettings.steeringSensitivity / 100.0f).coerceIn(0.2f, 1.0f)
        val leftScale = (tankSettings.leftMotorTrimPercent / 100.0f).coerceIn(0.5f, 1.5f)
        val rightScale = (tankSettings.rightMotorTrimPercent / 100.0f).coerceIn(0.5f, 1.5f)

        var effThrottle = if (tankSettings.invertThrottle) -throttle else throttle
        // Straight-line tracking bias (trim) active when throttle is non-zero
        val bias = if (throttle != 0f) (tankSettings.steeringTrimOffset / 100.0f) else 0f
        var effSteering = ((if (tankSettings.invertSteering) -steering else steering) + bias) * steerSens

        // Apply Tank Global Exponential Curve if configured
        if (tankSettings.expoPercent != 0) {
            val expo = (tankSettings.expoPercent / 100.0f).coerceIn(-1.0f, 1.0f)
            effThrottle = ((1.0f - expo) * effThrottle + expo * (effThrottle * effThrottle * effThrottle)).coerceIn(-1.0f, 1.0f)
            effSteering = ((1.0f - expo) * effSteering + expo * (effSteering * effSteering * effSteering)).coerceIn(-1.0f, 1.0f)
        }

        val minPwm = tankSettings.minMotorPwm.coerceIn(0, 60)

        return when (mixingMode) {
            MixingMode.ARCADE -> {
                // Arcade Tank Differential Mixing:
                // Left Motor  = Throttle + Steering
                // Right Motor = Throttle - Steering
                val rawLeft = effThrottle + effSteering
                val rawRight = effThrottle - effSteering

                // Smooth magnitude normalization so blended turns don't clip
                val maxMag = maxOf(abs(rawLeft), abs(rawRight), 1.0f)
                var leftVal = (rawLeft / maxMag) * speedLimit * leftScale
                var rightVal = (rawRight / maxMag) * speedLimit * rightScale

                // Stiction / Deadband compensation (minMotorPwm)
                if (minPwm > 0) {
                    if (abs(leftVal) > 0.01f) {
                        val sign = if (leftVal > 0) 1f else -1f
                        leftVal = sign * ((minPwm / 255.0f) + abs(leftVal) * ((255 - minPwm) / 255.0f))
                    }
                    if (abs(rightVal) > 0.01f) {
                        val sign = if (rightVal > 0) 1f else -1f
                        rightVal = sign * ((minPwm / 255.0f) + abs(rightVal) * ((255 - minPwm) / 255.0f))
                    }
                }

                val leftPwm = (leftVal * 255.0f).roundToInt().coerceIn(-255, 255).toShort()
                val rightPwm = (rightVal * 255.0f).roundToInt().coerceIn(-255, 255).toShort()
                Pair(leftPwm, rightPwm)
            }
            MixingMode.DUAL_TANK -> {
                // Direct Twin-Stick Tank: Left Stick Y = Left Motor, Right Stick Y = Right Motor
                val leftInput = (if (tankSettings.invertThrottle) -leftStickY else leftStickY) * leftScale
                val rightInput = (if (tankSettings.invertThrottle) -rightStickY else rightStickY) * rightScale

                var leftVal = leftInput * speedLimit
                var rightVal = rightInput * speedLimit

                if (minPwm > 0) {
                    if (abs(leftVal) > 0.01f) {
                        val sign = if (leftVal > 0) 1f else -1f
                        leftVal = sign * ((minPwm / 255.0f) + abs(leftVal) * ((255 - minPwm) / 255.0f))
                    }
                    if (abs(rightVal) > 0.01f) {
                        val sign = if (rightVal > 0) 1f else -1f
                        rightVal = sign * ((minPwm / 255.0f) + abs(rightVal) * ((255 - minPwm) / 255.0f))
                    }
                }

                val leftPwm = (leftVal * 255.0f).roundToInt().coerceIn(-255, 255).toShort()
                val rightPwm = (rightVal * 255.0f).roundToInt().coerceIn(-255, 255).toShort()
                Pair(leftPwm, rightPwm)
            }
        }
    }

    /**
     * Pack CH5–CH10 auxiliary values (normalized 0.0 to 1.0) into 16-bit payload.
     */
    fun packAux(auxMap: Map<Int, Float>): Int {
        var packed = 0

        val ch5Val = ((auxMap[5] ?: 0f).coerceIn(0f, 1f) * 7f).roundToInt() and 0x07
        packed = packed or ch5Val

        val ch6Val = ((auxMap[6] ?: 0f).coerceIn(0f, 1f) * 7f).roundToInt() and 0x07
        packed = packed or (ch6Val shl 3)

        val ch7Val = ((auxMap[7] ?: 0f).coerceIn(0f, 1f) * 7f).roundToInt() and 0x07
        packed = packed or (ch7Val shl 6)

        val ch8Val = ((auxMap[8] ?: 0f).coerceIn(0f, 1f) * 7f).roundToInt() and 0x07
        packed = packed or (ch8Val shl 9)

        val ch9Val = ((auxMap[9] ?: 0f).coerceIn(0f, 1f) * 3f).roundToInt() and 0x03
        packed = packed or (ch9Val shl 12)

        val ch10Val = ((auxMap[10] ?: 0f).coerceIn(0f, 1f) * 3f).roundToInt() and 0x03
        packed = packed or (ch10Val shl 14)

        return packed and 0xFFFF
    }
}
