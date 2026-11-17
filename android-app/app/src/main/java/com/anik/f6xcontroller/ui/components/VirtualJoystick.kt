package com.anik.f6xcontroller.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anik.f6xcontroller.ui.theme.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Computes control-assist output with strict deadzone and smooth non-restricting axis snapping.
 * - Radial deadzone ensures exact 0.0f output within deadzone.
 * - Smooth cubic Hermite S-curve blending: assists straight driving and spot turns without trapping the user:
 *   finger is freely tracked, and exiting the axis is completely fluid and continuous.
 */
fun calculateControlAssist(
    rawX: Float,
    rawY: Float,
    deadzone: Float,
    snapToAxes: Boolean,
    snapThreshold: Float
): Pair<Float, Float> {
    val dz = deadzone.coerceIn(0.0f, 0.40f)
    val snapThresh = snapThreshold.coerceIn(0.02f, 0.40f)

    // 1. Center radial deadzone check: inside circle -> strictly 0
    val dist = hypot(rawX, rawY)
    if (dist <= dz) {
        return Pair(0f, 0f)
    }

    // Rescale radial magnitude smoothly from 0.0f upward outside deadzone
    val scaledMag = ((dist - dz) / (1.0f - dz)).coerceIn(0f, 1f)
    val normX = (rawX / dist) * scaledMag
    val normY = (rawY / dist) * scaledMag

    var finalX = normX
    var finalY = normY

    // 2. Control assist (snapping without restriction or trapping):
    if (snapToAxes) {
        if (abs(normY) >= snapThresh) {
            val ratioX = (abs(normX) / snapThresh).coerceIn(0f, 1f)
            val smoothFactor = ratioX * ratioX * (3.0f - 2.0f * ratioX)
            finalX = normX * smoothFactor
        }
        if (abs(normX) >= snapThresh) {
            val ratioY = (abs(normY) / snapThresh).coerceIn(0f, 1f)
            val smoothFactor = ratioY * ratioY * (3.0f - 2.0f * ratioY)
            finalY = normY * smoothFactor
        }
    }

    return Pair(finalX.coerceIn(-1f, 1f), finalY.coerceIn(-1f, 1f))
}

@Composable
fun VirtualJoystick(
    modifier: Modifier = Modifier,
    title: String,
    xLabel: String,
    yLabel: String,
    xValue: Float, // -1.0f to +1.0f
    yValue: Float, // -1.0f to +1.0f
    autoCenterX: Boolean = true,
    autoCenterY: Boolean = true,
    snapToAxes: Boolean = false,
    snapThreshold: Float = 0.15f,
    deadzone: Float = 0.15f,
    onValueChange: (x: Float, y: Float) -> Unit,
    onToggleAutoCenter: (() -> Unit)? = null,
    baseSizeDp: Int = 180,
    scale: Float = 1.0f,
    isEditMode: Boolean = false,
    isSelected: Boolean = false,
    onSelect: (() -> Unit)? = null
) {
    val effectiveSize = (baseSizeDp * scale).dp
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    var isTouching by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(effectiveSize)
            .background(PureBlack)
            .then(
                if (isEditMode) {
                    Modifier
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) PureWhite else PillBorderMuted,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelect?.invoke() }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(autoCenterX, autoCenterY, scale, isEditMode, snapToAxes, snapThreshold, deadzone) {
                    if (isEditMode) return@pointerInput

                    val radius = size.width / 2.0f
                    val maxTravel = radius * 0.75f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isTouching = true

                        fun handlePointer(pos: Offset) {
                            val delta = pos - center
                            val dist = delta.getDistance()
                            val clampedDist = dist.coerceAtMost(maxTravel)
                            val angle = atan2(delta.y, delta.x)

                            val rawX = if (maxTravel > 0f) ((clampedDist * cos(angle)) / maxTravel).coerceIn(-1f, 1f) else 0f
                            val rawY = if (maxTravel > 0f) ((-clampedDist * sin(angle)) / maxTravel).coerceIn(-1f, 1f) else 0f

                            val (outX, outY) = calculateControlAssist(rawX, rawY, deadzone, snapToAxes, snapThreshold)
                            thumbOffset = Offset(outX * maxTravel, -outY * maxTravel)
                            // Send raw stick inputs so FsI6xMixer applies the mathematical DSP pipeline once
                            onValueChange(rawX, rawY)
                        }

                        handlePointer(down.position)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                break
                            }
                            change.consume()
                            handlePointer(change.position)
                        }

                        // Touch ended: release / snap back to center
                        isTouching = false
                        val finalX = if (autoCenterX) 0f else (thumbOffset.x / maxTravel).coerceIn(-1f, 1f)
                        val finalY = if (autoCenterY) 0f else (-thumbOffset.y / maxTravel).coerceIn(-1f, 1f)
                        thumbOffset = Offset(
                            if (autoCenterX) 0f else thumbOffset.x,
                            if (autoCenterY) 0f else thumbOffset.y
                        )
                        onValueChange(finalX, finalY)
                    }
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width / 2f
            val maxTravel = radius * 0.75f

            val currentThumbOffset = if (!isTouching && (xValue != 0f || yValue != 0f)) {
                Offset(xValue * maxTravel, -yValue * maxTravel)
            } else {
                thumbOffset
            }

            // 1. Subtle Monochrome Crosshairs (dashed line)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            drawLine(
                color = Color(0x33FFFFFF),
                start = Offset(center.x, center.y - maxTravel),
                end = Offset(center.x, center.y + maxTravel),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )
            drawLine(
                color = Color(0x33FFFFFF),
                start = Offset(center.x - maxTravel, center.y),
                end = Offset(center.x + maxTravel, center.y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )

            // 2. Outer boundary ring (crisp solid white vector border)
            drawCircle(
                color = PureWhite,
                radius = maxTravel,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // 3. Vector Tether Line connecting center to current stick position
            val stickCenter = center + currentThumbOffset
            drawLine(
                color = Color(0x88FFFFFF),
                start = center,
                end = stickCenter,
                strokeWidth = 1.5.dp.toPx()
            )

            // 4. Center deadzone boundary ring (clearly visualizes the 0-output deadband)
            val deadbandRadius = (maxTravel * deadzone).coerceAtLeast(4.dp.toPx())
            drawCircle(
                color = Color(0x44FFFFFF),
                radius = deadbandRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // 5. Thumb Stick Handle (Modern minimal ring with subtle fill)
            val thumbRadius = 24.dp.toPx() * scale
            drawCircle(
                color = Color(0x22FFFFFF),
                radius = thumbRadius,
                center = stickCenter
            )
            drawCircle(
                color = PureWhite,
                radius = thumbRadius,
                center = stickCenter,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = PureWhite,
                radius = 3.dp.toPx(),
                center = stickCenter
            )
        }

        // Live Monospace % Readouts & Title
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val xPct = (xValue * 100f).roundToInt()
            val yPct = (yValue * 100f).roundToInt()
            val xSign = if (xPct >= 0) "+" else ""
            val ySign = if (yPct >= 0) "+" else ""

            Text(
                text = title,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = (10 * scale).sp,
                color = PureWhite
            )
            Text(
                text = "$xLabel:$xSign$xPct%  $yLabel:$ySign$yPct%",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                fontSize = (9 * scale).sp,
                color = TextSecondary
            )
            if (onToggleAutoCenter != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(PillSurface)
                        .border(1.dp, if (autoCenterY) PureWhite else PillBorderMuted, RoundedCornerShape(10.dp))
                        .clickable { onToggleAutoCenter() }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (autoCenterY) "AUTO-CENTER" else "HOLD",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = (8 * scale).sp,
                        color = PureWhite
                    )
                }
            }
        }
    }
}
