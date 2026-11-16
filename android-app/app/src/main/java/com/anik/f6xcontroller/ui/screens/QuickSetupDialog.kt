package com.anik.f6xcontroller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anik.f6xcontroller.ui.theme.*

@Composable
fun QuickSetupDialog(
    isConnected: Boolean,
    onConnectWifiClick: () -> Unit,
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
                .width(520.dp)
                .fillMaxHeight(0.88f)
                .background(PureBlack, RoundedCornerShape(16.dp))
                .border(1.dp, PillBorder, RoundedCornerShape(16.dp))
                .padding(24.dp)
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState())
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
                            .background(PureWhite, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "BEGINNER QUICK SETUP GUIDE",
                        color = PureWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(PureBlack)
                        .border(1.dp, PillBorder, RoundedCornerShape(20.dp))
                        .clickable(onClick = onDismiss)
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

            Spacer(modifier = Modifier.height(18.dp))

            // Step 1
            SetupStepItem(
                number = "01",
                title = "POWER ON YOUR SOCCERBOT",
                description = "Turn on the battery power switch to the ESP32-S3 and BTS7960 drivers. The onboard NeoPixel LED will glow Yellow on startup, then pulse Blue when ready for phone connection."
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Step 2
            SetupStepItem(
                number = "02",
                title = "CONNECT TO ROBOT WI-FI AP",
                description = "Open your Android Wi-Fi settings and connect to:\n• SSID: RoboSoccer_S3\n• Password: soccer2026\nIf Android prompts 'Wi-Fi has no internet access', tap 'Always Keep Connection'."
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Step 3
            SetupStepItem(
                number = "03",
                title = "ZERO-DISCONNECT AUTO PERSISTENCE",
                description = "This app automatically locks process network traffic directly to the ESP32-S3 robot at 100 Hz. The top status pill will display CONNECTED and the robot NeoPixel will turn solid Green!"
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Live Connection Status Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PillSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, PillBorderMuted, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CURRENT ROBOT STATUS",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (isConnected) "● BOT LINK ACTIVE (100 Hz)" else "○ SEARCHING ROBOT AP...",
                        color = PureWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isConnected) PureBlack else PureWhite)
                        .border(1.dp, PillBorder, RoundedCornerShape(20.dp))
                        .clickable(onClick = onConnectWifiClick)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isConnected) "RECONNECT" else "CONNECT NOW",
                        color = if (isConnected) PureWhite else PureBlack,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupStepItem(
    number: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PillSurface, RoundedCornerShape(10.dp))
            .border(1.dp, PillBorderMuted, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(PureBlack, CircleShape)
                .border(1.dp, PillBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = PureWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = PureWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
