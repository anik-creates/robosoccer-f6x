package com.anik.f6xcontroller.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anik.f6xcontroller.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun AuxSwitch2Pos(
    modifier: Modifier = Modifier,
    channel: Int,
    value: Float, // 0.0f (DOWN) or 1.0f (UP)
    onValueChange: (Float) -> Unit,
    scale: Float = 1.0f,
    isEditMode: Boolean = false,
    isSelected: Boolean = false,
    onSelect: (() -> Unit)? = null
) {
    val isUp = value > 0.5f

    Box(
        modifier = modifier
            .size((74 * scale).dp, (95 * scale).dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PillSurface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) PureWhite else PillBorderMuted,
                shape = RoundedCornerShape(12.dp)
            )
            .then(
                if (isEditMode) {
                    Modifier.clickable { onSelect?.invoke() }
                } else {
                    Modifier.clickable {
                        onValueChange(if (isUp) 0f else 1f)
                    }
                }
            )
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = "CH$channel",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = (10 * scale).sp,
                color = PureWhite
            )

            // Switch Graphic
            Canvas(
                modifier = Modifier
                    .width((32 * scale).dp)
                    .height((48 * scale).dp)
            ) {
                val w = size.width
                val h = size.height

                // Track slot
                drawRoundRect(
                    color = Color(0xFF1E1E1E),
                    topLeft = Offset(w * 0.35f, 0f),
                    size = Size(w * 0.3f, h),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                drawRoundRect(
                    color = PillBorderMuted,
                    topLeft = Offset(w * 0.35f, 0f),
                    size = Size(w * 0.3f, h),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )

                // Lever handle
                val handleY = if (isUp) h * 0.18f else h * 0.82f
                drawCircle(
                    color = if (isUp) PureWhite else Color(0xFF555555),
                    radius = (10 * scale).dp.toPx(),
                    center = Offset(w * 0.5f, handleY)
                )
                drawCircle(
                    color = PureBlack,
                    radius = (10 * scale).dp.toPx(),
                    center = Offset(w * 0.5f, handleY),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            Text(
                text = if (isUp) "ON" else "OFF",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = (9 * scale).sp,
                color = if (isUp) PureWhite else TextMuted
            )
        }
    }
}

@Composable
fun AuxSwitch3Pos(
    modifier: Modifier = Modifier,
    channel: Int,
    value: Float, // 0.0f (DOWN), 0.5f (MID), 1.0f (UP)
    onValueChange: (Float) -> Unit,
    scale: Float = 1.0f,
    isEditMode: Boolean = false,
    isSelected: Boolean = false,
    onSelect: (() -> Unit)? = null
) {
    val state = when {
        value < 0.25f -> 0 // DOWN
        value in 0.25f..0.75f -> 1 // MID
        else -> 2 // UP
    }

    Box(
        modifier = modifier
            .size((74 * scale).dp, (115 * scale).dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PillSurface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) PureWhite else PillBorderMuted,
                shape = RoundedCornerShape(12.dp)
            )
            .then(
                if (isEditMode) {
                    Modifier.clickable { onSelect?.invoke() }
                } else {
                    Modifier.clickable {
                        val next = (state + 1) % 3
                        val nextVal = when (next) {
                            0 -> 0.0f
                            1 -> 0.5f
                            else -> 1.0f
                        }
                        onValueChange(nextVal)
                    }
                }
            )
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = "CH$channel",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = (10 * scale).sp,
                color = PureWhite
            )

            Canvas(
                modifier = Modifier
                    .width((32 * scale).dp)
                    .height((64 * scale).dp)
            ) {
                val w = size.width
                val h = size.height

                drawRoundRect(
                    color = Color(0xFF1E1E1E),
                    topLeft = Offset(w * 0.35f, 0f),
                    size = Size(w * 0.3f, h),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                drawRoundRect(
                    color = PillBorderMuted,
                    topLeft = Offset(w * 0.35f, 0f),
                    size = Size(w * 0.3f, h),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )

                val handleY = when (state) {
                    2 -> h * 0.15f
                    1 -> h * 0.50f
                    else -> h * 0.85f
                }

                drawCircle(
                    color = PureWhite,
                    radius = (9 * scale).dp.toPx(),
                    center = Offset(w * 0.5f, handleY)
                )
                drawCircle(
                    color = PureBlack,
                    radius = (9 * scale).dp.toPx(),
                    center = Offset(w * 0.5f, handleY),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            Text(
                text = when (state) {
                    2 -> "UP"
                    1 -> "MID"
                    else -> "DOWN"
                },
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = (9 * scale).sp,
                color = PureWhite
            )
        }
    }
}

@Composable
fun AuxRotarySlider(
    modifier: Modifier = Modifier,
    channel: Int,
    value: Float, // 0.0f to 1.0f
    onValueChange: (Float) -> Unit,
    scale: Float = 1.0f,
    isEditMode: Boolean = false,
    isSelected: Boolean = false,
    onSelect: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .size((74 * scale).dp, (130 * scale).dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PillSurface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) PureWhite else PillBorderMuted,
                shape = RoundedCornerShape(12.dp)
            )
            .then(
                if (isEditMode) {
                    Modifier.clickable { onSelect?.invoke() }
                } else Modifier
            )
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = "CH$channel",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = (10 * scale).sp,
                color = PureWhite
            )

            // Vertical Slider Bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .width((24 * scale).dp)
                    .pointerInput(isEditMode) {
                        if (isEditMode) return@pointerInput
                        detectDragGestures { change, _ ->
                            change.consume()
                            val fraction = 1.0f - (change.position.y / size.height).coerceIn(0f, 1f)
                            onValueChange(fraction)
                        }
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1E1E))
                    .border(1.dp, PillBorderMuted, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Active fill bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction = value.coerceIn(0f, 1f))
                        .background(PureWhite)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${(value * 100).roundToInt()}%",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = (9 * scale).sp,
                color = PureWhite
            )
        }
    }
}

@Composable
fun AuxMomentaryButton(
    modifier: Modifier = Modifier,
    channel: Int,
    label: String = "PUSH",
    value: Float, // 0.0f (released) or 1.0f (pressed)
    onValueChange: (Float) -> Unit,
    scale: Float = 1.0f,
    isEditMode: Boolean = false,
    isSelected: Boolean = false,
    onSelect: (() -> Unit)? = null
) {
    val isPressed = value > 0.5f

    Box(
        modifier = modifier
            .size((74 * scale).dp, (74 * scale).dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPressed) PureWhite else PillSurface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) PureWhite else PillBorderMuted,
                shape = RoundedCornerShape(12.dp)
            )
            .then(
                if (isEditMode) {
                    Modifier.clickable { onSelect?.invoke() }
                } else {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onValueChange(1.0f)
                                tryAwaitRelease()
                                onValueChange(0.0f)
                            }
                        )
                    }
                }
            )
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "CH$channel",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = (10 * scale).sp,
                color = if (isPressed) PureBlack else TextSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = (11 * scale).sp,
                color = if (isPressed) PureBlack else PureWhite
            )
        }
    }
}
