package com.anik.f6xcontroller.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anik.f6xcontroller.data.MixingMode
import com.anik.f6xcontroller.data.TankControlSettings
import com.anik.f6xcontroller.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun TankTuningDialog(
    tankSettings: TankControlSettings,
    mixingMode: MixingMode,
    onUpdateTankSettings: (TankControlSettings) -> Unit,
    onSelectMixingMode: (MixingMode) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ModalScrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(580.dp)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(PureBlack)
                .border(1.dp, PillBorder, RoundedCornerShape(16.dp))
                .padding(20.dp)
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(PureWhite)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "TANK BOT TUNING & DYNAMICS",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp,
                        color = PureWhite
                    )
                }

                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(PillSurface)
                        .border(1.dp, PillBorderMuted, RoundedCornerShape(15.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕ CLOSE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = PureWhite,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Section 1: Drive Mode (Single-Stick vs Twin-Stick)
            Text(
                text = "DRIVE MODE SELECTION",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = PureWhite
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Pair(MixingMode.ARCADE, "SINGLE-STICK ARCADE\n(RIGHT STICK CH1+CH2)"),
                    Pair(MixingMode.DUAL_TANK, "TWIN-STICK TANK\n(LEFT + RIGHT TRACKS)")
                ).forEach { (mode, label) ->
                    val isSel = mode == mixingMode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) PureWhite else PillSurface)
                            .border(1.dp, if (isSel) PureWhite else PillBorderMuted, RoundedCornerShape(10.dp))
                            .clickable { onSelectMixingMode(mode) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            color = if (isSel) PureBlack else TextSecondary,
                            maxLines = 2
                        )
                    }
                }
            }

            // Section 2: Speed Limiter / Max Motor Output Governor
            TuningSlider(
                title = "SPEED LIMITER / MAX MOTOR SPEED",
                description = "Caps maximum motor PWM output to BTS7960 drivers (ideal for precision aim or training)",
                value = tankSettings.maxSpeedPercent.toFloat(),
                range = 20f..100f,
                suffix = "%"
            ) {
                onUpdateTankSettings(tankSettings.copy(maxSpeedPercent = it.roundToInt()))
            }

            // Section 3: Expo (Exponential Response Curve)
            TuningSlider(
                title = "EXPO (EXPONENTIAL SENSITIVITY CURVE)",
                description = "Positive values soften center stick for micro-aiming, negative values make response aggressive",
                value = tankSettings.expoPercent.toFloat(),
                range = -100f..100f,
                suffix = "%"
            ) {
                onUpdateTankSettings(tankSettings.copy(expoPercent = it.roundToInt()))
            }

            // Live Expo Curve Graph Preview
            ExpoCurveMiniPreview(expoPercent = tankSettings.expoPercent)

            // Section 4: Deadzone
            TuningSlider(
                title = "STICK DEADBAND (DEADZONE)",
                description = "Radius around center that outputs zero speed to ignore stick play and vibrations",
                value = tankSettings.deadzone * 100f,
                range = 0f..30f,
                suffix = "%"
            ) {
                onUpdateTankSettings(tankSettings.copy(deadzone = it / 100f))
            }

            // Section 5: Cardinal Axis Snapping & Threshold
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AXIS SNAPPING (STRAIGHT ASSIST):",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = PureWhite
                    )
                    Text(
                        text = "Snaps right joystick to pure straight forward/reverse or pure spot-turn axis",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = TextMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (tankSettings.snapToAxes) PureWhite else PillSurface)
                        .border(1.dp, if (tankSettings.snapToAxes) PureWhite else PillBorderMuted, RoundedCornerShape(15.dp))
                        .clickable {
                            onUpdateTankSettings(tankSettings.copy(snapToAxes = !tankSettings.snapToAxes))
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (tankSettings.snapToAxes) "ENABLED" else "DISABLED",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = if (tankSettings.snapToAxes) PureBlack else TextSecondary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            if (tankSettings.snapToAxes) {
                TuningSlider(
                    title = "SNAPPING DISTANCE / THRESHOLD",
                    description = "When pushed closer than this threshold to an axis, stick automatically latches to it",
                    value = tankSettings.snapThreshold * 100f,
                    range = 5f..35f,
                    suffix = "%"
                ) {
                    onUpdateTankSettings(tankSettings.copy(snapThreshold = it / 100f))
                }
            }

            // Section 6: Steering Sensitivity (Dual Rate)
            TuningSlider(
                title = "STEERING SENSITIVITY (DUAL RATE)",
                description = "Reduces turn speed rate relative to forward speed for high-speed stability",
                value = tankSettings.steeringSensitivity.toFloat(),
                range = 20f..100f,
                suffix = "%"
            ) {
                onUpdateTankSettings(tankSettings.copy(steeringSensitivity = it.roundToInt()))
            }

            // Section 7: Inversion & Center Snap Toggles
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PillSurface)
                    .border(1.dp, PillBorderMuted, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Left Stick Center Snap Toggle
                ToggleRow(
                    label = "LEFT STICK CENTER SNAP:",
                    subLabel = "Spring-return to center vs Hold position",
                    isActive = tankSettings.leftStickSnapCenter,
                    activeText = "AUTO-CENTER",
                    inactiveText = "HOLD POSITION"
                ) {
                    onUpdateTankSettings(tankSettings.copy(leftStickSnapCenter = !tankSettings.leftStickSnapCenter))
                }

                // Invert Steering Toggle
                ToggleRow(
                    label = "INVERT STEERING (LEFT / RIGHT):",
                    subLabel = "Reverse turn direction for inverted BTS7960 motor polarity",
                    isActive = tankSettings.invertSteering,
                    activeText = "INVERTED",
                    inactiveText = "NORMAL"
                ) {
                    onUpdateTankSettings(tankSettings.copy(invertSteering = !tankSettings.invertSteering))
                }

                // Invert Throttle Toggle
                ToggleRow(
                    label = "INVERT THROTTLE (FWD / REV):",
                    subLabel = "Reverse forward/backward motor polarity",
                    isActive = tankSettings.invertThrottle,
                    activeText = "INVERTED",
                    inactiveText = "NORMAL"
                ) {
                    onUpdateTankSettings(tankSettings.copy(invertThrottle = !tankSettings.invertThrottle))
                }
            }
        }
    }
}

@Composable
private fun TuningSlider(
    title: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = PureWhite
            )
            val vInt = value.roundToInt()
            val sign = if (vInt > 0 && range.start < 0) "+" else ""
            Text(
                text = "$sign$vInt$suffix",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = PureWhite
            )
        }
        Text(
            text = description,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(2.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = PureWhite,
                activeTrackColor = PureWhite,
                inactiveTrackColor = PillBorderMuted
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    subLabel: String,
    isActive: Boolean,
    activeText: String,
    inactiveText: String,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = PureWhite
            )
            Text(
                text = subLabel,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = TextMuted
            )
        }
        Box(
            modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isActive) PureWhite else PureBlack)
                .border(1.dp, PillBorderMuted, RoundedCornerShape(14.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isActive) activeText else inactiveText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = if (isActive) PureBlack else TextSecondary,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun ExpoCurveMiniPreview(expoPercent: Int) {
    val expo = (expoPercent / 100.0f).coerceIn(-1.0f, 1.0f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PillSurface)
            .border(1.dp, PillBorderMuted, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val midY = h / 2f

            // Baseline
            drawLine(
                color = Color(0x33FFFFFF),
                start = Offset(0f, midY),
                end = Offset(w, midY),
                strokeWidth = 1.dp.toPx()
            )

            // Curve Path
            val path = Path()
            val steps = 60
            for (i in 0..steps) {
                val input = (i.toFloat() / steps.toFloat()) * 2.0f - 1.0f // -1.0 to +1.0
                val output = ((1.0f - expo) * input + expo * (input * input * input)).coerceIn(-1f, 1f)
                val px = (i.toFloat() / steps.toFloat()) * w
                val py = midY - (output * (h / 2.2f))
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, color = PureWhite, style = Stroke(width = 2.dp.toPx()))
        }
    }
}
