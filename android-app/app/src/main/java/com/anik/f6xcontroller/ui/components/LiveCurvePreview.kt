package com.anik.f6xcontroller.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anik.f6xcontroller.dsp.FsI6xMixer
import com.anik.f6xcontroller.ui.theme.*

@Composable
fun LiveCurvePreview(
    modifier: Modifier = Modifier,
    channel: Int,
    dualRate: Int,      // 0..100%
    expo: Int,          // -100..+100%
    currentInput: Float // -1.0f..+1.0f
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PillSurface)
            .border(width = 1.dp, color = PillBorderMuted, shape = RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "CH$channel CURVE: RATE $dualRate% | EXPO $expo%",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = PureWhite
            )
            Spacer(modifier = Modifier.height(4.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f

                // 1. Grid lines
                val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                drawLine(
                    color = Color(0x33FFFFFF),
                    start = Offset(0f, cy),
                    end = Offset(w, cy),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dash
                )
                drawLine(
                    color = Color(0x33FFFFFF),
                    start = Offset(cx, 0f),
                    end = Offset(cx, h),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dash
                )

                // Linear reference line (45 deg diagonal)
                drawLine(
                    color = Color(0x22FFFFFF),
                    start = Offset(0f, h),
                    end = Offset(w, 0f),
                    strokeWidth = 1.dp.toPx()
                )

                // 2. Plot the FS-i6X D/R & Expo curve
                val path = Path()
                val steps = 80
                for (i in 0..steps) {
                    val normX = (i.toFloat() / steps.toFloat()) * 2.0f - 1.0f // -1.0 to +1.0
                    val normY = FsI6xMixer.computeCurvePoint(normX, dualRate, expo)

                    val px = cx + (normX * cx)
                    val py = cy - (normY * cy) // Invert Y for screen

                    if (i == 0) {
                        path.moveTo(px, py)
                    } else {
                        path.lineTo(px, py)
                    }
                }

                drawPath(
                    path = path,
                    color = PureWhite,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // 3. Current stick input dot
                val stickOut = FsI6xMixer.computeCurvePoint(currentInput, dualRate, expo)
                val dotX = cx + (currentInput.coerceIn(-1f, 1f) * cx)
                val dotY = cy - (stickOut * cy)

                drawCircle(
                    color = PureWhite,
                    radius = 4.dp.toPx(),
                    center = Offset(dotX, dotY)
                )
                drawCircle(
                    color = PureBlack,
                    radius = 4.dp.toPx(),
                    center = Offset(dotX, dotY),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}
