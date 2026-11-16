package com.anik.f6xcontroller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anik.f6xcontroller.data.TelemetryStats
import com.anik.f6xcontroller.dsp.ProcessedChannels
import com.anik.f6xcontroller.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt

data class ChannelBarData(
    val id: String,
    val name: String,
    val function: String,
    val valueNormalized: Float, // -1.0f to +1.0f (or 0.0f to 1.0f)
    val displayValue: String,
    val isBipolar: Boolean = true
)

@Composable
fun ChannelMonitorOverlay(
    channels: ProcessedChannels,
    telemetry: TelemetryStats,
    auxValues: Map<Int, Float>,
    onClose: () -> Unit
) {
    val leftPwm = telemetry.leftPwm.toInt()
    val rightPwm = telemetry.rightPwm.toInt()

    val monitorItems = listOf(
        ChannelBarData("CH1", "CH1 - STEERING", "Yaw / Aileron", channels.ch1Aileron, "${(channels.ch1Aileron * 100).roundToInt()}%"),
        ChannelBarData("CH2", "CH2 - PITCH", "Elevator", channels.ch2Elevator, "${(channels.ch2Elevator * 100).roundToInt()}%"),
        ChannelBarData("CH3", "CH3 - THROTTLE", "Forward / Reverse", channels.ch3Throttle, "${(channels.ch3Throttle * 100).roundToInt()}%"),
        ChannelBarData("CH4", "CH4 - RUDDER", "Trim / Differential", channels.ch4Rudder, "${(channels.ch4Rudder * 100).roundToInt()}%"),
        ChannelBarData("MOT_L", "4WD LEFT TRACK", if (leftPwm >= 0) "FORWARD" else "REVERSE", leftPwm / 255.0f, "$leftPwm PWM"),
        ChannelBarData("MOT_R", "4WD RIGHT TRACK", if (rightPwm >= 0) "FORWARD" else "REVERSE", rightPwm / 255.0f, "$rightPwm PWM"),
        ChannelBarData("CH5", "CH5 - AUX 1", "Multi-Pos Switch", auxValues[5] ?: 0f, "${((auxValues[5] ?: 0f) * 100).roundToInt()}%", false),
        ChannelBarData("CH6", "CH6 - DRIBBLER", "Aux PWM / Speed", auxValues[6] ?: 0f, "${((auxValues[6] ?: 0f) * 100).roundToInt()}%", false),
        ChannelBarData("CH7", "CH7 - AUX 3", "Custom Channel", auxValues[7] ?: 0f, "${((auxValues[7] ?: 0f) * 100).roundToInt()}%", false),
        ChannelBarData("CH8", "CH8 - AUX 4", "Custom Channel", auxValues[8] ?: 0f, "${((auxValues[8] ?: 0f) * 100).roundToInt()}%", false),
        ChannelBarData("CH9", "CH9 - AUX 5", "Toggle Switch", auxValues[9] ?: 0f, "${((auxValues[9] ?: 0f) * 100).roundToInt()}%", false),
        ChannelBarData("CH10", "CH10 - AUX 6", "Toggle Switch", auxValues[10] ?: 0f, "${((auxValues[10] ?: 0f) * 100).roundToInt()}%", false)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ModalScrim)
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .fillMaxHeight(0.92f)
                .background(PureBlack, RoundedCornerShape(16.dp))
                .border(1.dp, PillBorder, RoundedCornerShape(16.dp))
                .padding(20.dp)
                .clickable(enabled = false) {}
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(PureWhite, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "LIVE CHANNEL OUTPUT MONITOR",
                        color = PureWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }

                // Close Pill Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(PureBlack)
                        .border(1.dp, PillBorder, RoundedCornerShape(20.dp))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "✕ CLOSE",
                        color = PureWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Subtitle info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "CHANNEL & FUNCTION",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "REAL-TIME PWM / VALUE",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Scrollable List of Channels
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(monitorItems) { item ->
                    ChannelBarRow(item)
                }
            }
        }
    }
}

@Composable
private fun ChannelBarRow(data: ChannelBarData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PillSurface, RoundedCornerShape(8.dp))
            .border(1.dp, PillBorderMuted, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = data.name,
                    color = PureWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = data.function,
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = data.displayValue,
                color = PureWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Live Bar Graph Meter
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1A1A1A))
                .border(1.dp, PillBorderMuted, RoundedCornerShape(4.dp))
        ) {
            if (data.isBipolar) {
                // Center-zero bar (-1.0 to +1.0)
                val clamped = data.valueNormalized.coerceIn(-1.0f, 1.0f)
                val isPositive = clamped >= 0f
                val barFraction = abs(clamped) / 2.0f // 0.0 to 0.5 of width

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = barFraction)
                        .align(if (isPositive) Alignment.CenterEnd else Alignment.CenterStart)
                        .background(PureWhite)
                )

                // Center zero line
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .align(Alignment.Center)
                        .background(PureWhite)
                )
            } else {
                // Unipolar bar (0.0 to 1.0)
                val fraction = data.valueNormalized.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = fraction)
                        .background(PureWhite)
                )
            }
        }
    }
}
