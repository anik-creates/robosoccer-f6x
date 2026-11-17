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
 * Computes control-assist output with strict deadzone and non-restricting axis snapping.
 * - Radial and per-axis deadzone ensures exact 0.0f output within deadzone.
 * - Axis snapping assists straight driving and spot turns without trapping the user:
 *   finger is freely tracked, and exiting the axis is instant and smooth.
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

    var px = rawX
    var py = rawY

    // 2. Independent axis deadzone:
    // If lateral steering drift is within deadzone, steer is strictly 0 (rock-solid straight driving!)
    if (abs(px) <= dz) {
        px = 0f
    }
    // If longitudinal throttle drift is within deadzone, throttle is strictly 0 (rock-solid spot turns!)
    if (abs(py) <= dz) {
        py = 0f
    }

    if (px == 0f && py == 0f) {
        return Pair(0f, 0f)
    }

    // 3. Control assist (snapping without restriction):
    // Assist without trapping:
    // When driving forward/reverse (|rawY| >= snapThresh) and lateral deflection |rawX| < snapThresh:
    // snap steering to 0 for a laser-straight line.
    // When doing a spot turn (|rawX| >= snapThresh) and longitudinal deflection |rawY| < snapThresh:
    // snap throttle to 0 for a pure spot turn.
    if (snapToAxes) {
        if (abs(rawX) < snapThresh && abs(rawY) >= snapThresh) {
            px = 0f
        }
        if (abs(rawY) < snapThresh && abs(rawX) >= snapThresh) {
            py = 0f
        }
    }

    // 4. Smooth linear scaling so there is no sudden jump when exiting deadzone or snap zone
    val outX = if (px == 0f) 0f else {
        val sign = if (px > 0f) 1f else -1f
        val thresh = if (snapToAxes && abs(rawX) < snapThresh) snapThresh else dz
        val mag = if (thresh < 1f) ((abs(px) - thresh) / (1f - thresh)).coerceIn(0f, 1f) else 1f
        sign * mag
    }

    val outY = if (py == 0f) 0f else {
        val sign = if (py > 0f) 1f else -1f
        val thresh = if (snapToAxes && abs(rawY) < snapThresh) snapThresh else dz
        val mag = if (thresh < 1f) ((abs(py) - thresh) / (1f - thresh)).coerceIn(0f, 1f) else 1f
        sign * mag
    }

    return Pair(outX.coerceIn(-1f, 1f), outY.coerceIn(-1f, 1f))
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
                            onValueChange(outX, outY)
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
