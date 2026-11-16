package com.anik.f6xcontroller.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
                .pointerInput(autoCenterX, autoCenterY, scale, isEditMode) {
                    if (isEditMode) return@pointerInput

                    val radius = size.width / 2.0f
                    val maxTravel = radius * 0.75f

                    detectDragGestures(
                        onDragStart = { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val delta = offset - center
                            val dist = delta.getDistance()
                            val clampedDist = dist.coerceAtMost(maxTravel)
                            val angle = atan2(delta.y, delta.x)

                            val tx = clampedDist * cos(angle)
                            val ty = clampedDist * sin(angle)
                            thumbOffset = Offset(tx, ty)

                            val normX = (tx / maxTravel).coerceIn(-1f, 1f)
                            val normY = (-ty / maxTravel).coerceIn(-1f, 1f)
                            onValueChange(normX, normY)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newPos = thumbOffset + dragAmount
                            val dist = newPos.getDistance()
                            val clampedDist = dist.coerceAtMost(maxTravel)
                            val angle = atan2(newPos.y, newPos.x)

                            val tx = clampedDist * cos(angle)
                            val ty = clampedDist * sin(angle)
                            thumbOffset = Offset(tx, ty)

                            val normX = (tx / maxTravel).coerceIn(-1f, 1f)
                            val normY = (-ty / maxTravel).coerceIn(-1f, 1f)
                            onValueChange(normX, normY)
                        },
                        onDragEnd = {
                            val newX = if (autoCenterX) 0f else (thumbOffset.x / maxTravel).coerceIn(-1f, 1f)
                            val newY = if (autoCenterY) 0f else (-thumbOffset.y / maxTravel).coerceIn(-1f, 1f)

                            val tx = if (autoCenterX) 0f else thumbOffset.x
                            val ty = if (autoCenterY) 0f else thumbOffset.y
                            thumbOffset = Offset(tx, ty)

                            onValueChange(newX, newY)
                        },
                        onDragCancel = {
                            if (autoCenterX && autoCenterY) {
                                thumbOffset = Offset.Zero
                                onValueChange(0f, 0f)
                            }
                        }
                    )
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width / 2f
            val maxTravel = radius * 0.75f

            val currentThumbOffset = if (thumbOffset == Offset.Zero && (xValue != 0f || yValue != 0f)) {
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

            // 4. Center deadband ring
            drawCircle(
                color = Color(0x44FFFFFF),
                radius = maxTravel * 0.08f,
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
