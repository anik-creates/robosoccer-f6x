package com.anik.f6xcontroller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anik.f6xcontroller.data.*
import com.anik.f6xcontroller.network.AppUpdateManager
import com.anik.f6xcontroller.network.UpdateInfo
import com.anik.f6xcontroller.ui.components.LiveCurvePreview
import com.anik.f6xcontroller.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun FsI6xMenuDrawer(
    modifier: Modifier = Modifier,
    mixingMode: MixingMode,
    tankSettings: TankControlSettings,
    connectionSettings: ConnectionSettings,
    channelConfigs: Map<Int, ChannelConfig>,
    isWifiLocked: Boolean,
    wifiStatusMessage: String,
    updateInfo: UpdateInfo?,
    isCheckingUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onSelectMixingMode: (MixingMode) -> Unit,
    onUpdateTankSettings: (TankControlSettings) -> Unit,
    onUpdateConnectionSettings: (ConnectionSettings) -> Unit,
    onUpdateChannelConfig: (ChannelConfig) -> Unit,
    onLockWifi: () -> Unit,
    onUnlockWifi: () -> Unit,
    onClose: () -> Unit
) {
    var selectedTab by remember { mutableStateOf("SYSTEM") } // "SYSTEM" (Tank Drive) or "FUNCTIONS" (RC DSP)
    var selectedChannel by remember { mutableIntStateOf(1) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(480.dp)
            .background(PureBlack)
            .border(width = 1.dp, color = PillBorder)
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(PureWhite, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "APEX TANK BOT SETTINGS",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                        color = PureWhite
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(PillSurface)
                        .border(width = 1.dp, color = PillBorderMuted, shape = RoundedCornerShape(20.dp))
                        .clickable { onClose() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "✕ CLOSE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = PureWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Modern Pill Tab Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("SYSTEM", "FUNCTIONS").forEach { tab ->
                    val isSel = tab == selectedTab
                    val label = if (tab == "SYSTEM") "TANK CONTROLS & NET" else "RC DSP (CH1..CH10)"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSel) PureWhite else PillSurface)
                            .border(
                                width = 1.dp,
                                color = if (isSel) PureWhite else PillBorderMuted,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { selectedTab = tab }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp,
                            color = if (isSel) PureBlack else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Drawer Content Area
            if (selectedTab == "SYSTEM") {
                SystemTabContent(
                    mixingMode = mixingMode,
                    tankSettings = tankSettings,
                    connectionSettings = connectionSettings,
                    isWifiLocked = isWifiLocked,
                    wifiStatusMessage = wifiStatusMessage,
                    updateInfo = updateInfo,
                    isCheckingUpdate = isCheckingUpdate,
                    onCheckUpdate = onCheckUpdate,
                    onDownloadUpdate = onDownloadUpdate,
                    onSelectMixingMode = onSelectMixingMode,
                    onUpdateTankSettings = onUpdateTankSettings,
                    onUpdateConnectionSettings = onUpdateConnectionSettings,
                    onLockWifi = onLockWifi,
                    onUnlockWifi = onUnlockWifi
                )
            } else {
                FunctionsTabContent(
                    selectedChannel = selectedChannel,
                    onSelectChannel = { selectedChannel = it },
                    channelConfigs = channelConfigs,
                    onUpdateChannelConfig = onUpdateChannelConfig
                )
            }
        }
    }
}

@Composable
private fun SystemTabContent(
    mixingMode: MixingMode,
    tankSettings: TankControlSettings,
    connectionSettings: ConnectionSettings,
    isWifiLocked: Boolean,
    wifiStatusMessage: String,
    updateInfo: UpdateInfo?,
    isCheckingUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onSelectMixingMode: (MixingMode) -> Unit,
    onUpdateTankSettings: (TankControlSettings) -> Unit,
    onUpdateConnectionSettings: (ConnectionSettings) -> Unit,
    onLockWifi: () -> Unit,
    onUnlockWifi: () -> Unit
) {
    var ssid by remember(connectionSettings.ssid) { mutableStateOf(connectionSettings.ssid) }
    var pass by remember(connectionSettings.passphrase) { mutableStateOf(connectionSettings.passphrase) }
    var ip by remember(connectionSettings.ip) { mutableStateOf(connectionSettings.ip) }
    var portStr by remember(connectionSettings.port) { mutableStateOf(connectionSettings.port.toString()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Tank Motor Mixing Mode
        item {
            Text(
                text = "TANK DRIVE MODE",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = PureWhite
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MixingMode.entries.forEach { mix ->
                    val isSel = mix == mixingMode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSel) PureWhite else PillSurface)
                            .border(
                                width = 1.dp,
                                color = if (isSel) PureWhite else PillBorderMuted,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectMixingMode(mix) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (mix == MixingMode.ARCADE) "SINGLE-STICK ARCADE\n(RIGHT JOYSTICK)" else "TWIN-STICK TANK\n(L + R TRACKS)",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            lineHeight = 13.sp,
                            color = if (isSel) PureBlack else TextSecondary
                        )
                    }
                }
            }
        }

        // 2. Axis Snapping & Straight-Line Driving Assist
        item {
            Text(
                text = "AXIS SNAPPING & STEERING ASSIST",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = PureWhite
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PillSurface)
                    .border(1.dp, PillBorderMuted, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Snap to Cardinal Axes Toggle (Straight Line + Spot Turn)
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
                            fontSize = 10.sp,
                            color = PureWhite
                        )
                        Text(
                            text = "Snaps to straight forward/reverse or pure spot turn when within threshold",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = TextMuted
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (tankSettings.snapToAxes) PureWhite else PureBlack)
                            .border(1.dp, PillBorderMuted, RoundedCornerShape(20.dp))
                            .clickable {
                                onUpdateTankSettings(tankSettings.copy(snapToAxes = !tankSettings.snapToAxes))
                            }
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (tankSettings.snapToAxes) "ENABLED" else "DISABLED",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (tankSettings.snapToAxes) PureBlack else TextSecondary
                        )
                    }
                }

                // Snap Threshold Slider
                MonochromeParameterSlider(
                    label = "SNAP THRESHOLD",
                    value = tankSettings.snapThreshold * 100f,
                    range = 5f..35f,
                    suffix = "%"
                ) {
                    onUpdateTankSettings(tankSettings.copy(snapThreshold = it / 100f))
                }

                // Center Deadband Slider
                MonochromeParameterSlider(
                    label = "STICK DEADBAND",
                    value = tankSettings.deadzone * 100f,
                    range = 0f..25f,
                    suffix = "%"
                ) {
                    onUpdateTankSettings(tankSettings.copy(deadzone = it / 100f))
                }

                // Left Stick Snap to Center (Auto-Center)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LEFT STICK CENTER SNAP:",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = PureWhite
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (tankSettings.leftStickSnapCenter) PureWhite else PureBlack)
                            .border(1.dp, PillBorderMuted, RoundedCornerShape(20.dp))
                            .clickable {
                                onUpdateTankSettings(tankSettings.copy(leftStickSnapCenter = !tankSettings.leftStickSnapCenter))
                            }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (tankSettings.leftStickSnapCenter) "AUTO-CENTER" else "HOLD POSITION",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (tankSettings.leftStickSnapCenter) PureBlack else TextSecondary
                        )
                    }
                }

                // Steering Inversion Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STEERING DIRECTION (LEFT/RIGHT):",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = PureWhite
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (tankSettings.invertSteering) PureWhite else PureBlack)
                            .border(1.dp, PillBorderMuted, RoundedCornerShape(20.dp))
                            .clickable {
                                onUpdateTankSettings(tankSettings.copy(invertSteering = !tankSettings.invertSteering))
                            }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (tankSettings.invertSteering) "INVERTED" else "NORMAL",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (tankSettings.invertSteering) PureBlack else TextSecondary
                        )
                    }
                }

                // Throttle Inversion Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "THROTTLE DIRECTION (FWD/REV):",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = PureWhite
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (tankSettings.invertThrottle) PureWhite else PureBlack)
                            .border(1.dp, PillBorderMuted, RoundedCornerShape(20.dp))
                            .clickable {
                                onUpdateTankSettings(tankSettings.copy(invertThrottle = !tankSettings.invertThrottle))
                            }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (tankSettings.invertThrottle) "INVERTED" else "NORMAL",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (tankSettings.invertThrottle) PureBlack else TextSecondary
                        )
                    }
                }
            }
        }

        // 3. Motor Speed Balance & Channel Tuning (Left vs Right Track Calibration)
        item {
            Text(
                text = "MOTOR SPEED BALANCE & CHANNEL TUNING",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = PureWhite
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PillSurface)
                    .border(1.dp, PillBorderMuted, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Motor Power Trim
                MonochromeParameterSlider(
                    label = "LEFT TRACK MOTOR POWER",
                    value = tankSettings.leftMotorTrimPercent.toFloat(),
                    range = 50f..150f,
                    suffix = "%"
                ) {
                    onUpdateTankSettings(tankSettings.copy(leftMotorTrimPercent = it.roundToInt()))
                }

                // Right Motor Power Trim
                MonochromeParameterSlider(
                    label = "RIGHT TRACK MOTOR POWER",
                    value = tankSettings.rightMotorTrimPercent.toFloat(),
                    range = 50f..150f,
                    suffix = "%"
                ) {
                    onUpdateTankSettings(tankSettings.copy(rightMotorTrimPercent = it.roundToInt()))
                }

                // Straight-Line Tracking Trim
                MonochromeParameterSlider(
                    label = "STRAIGHT-LINE TRACKING TRIM (BIAS)",
                    value = tankSettings.steeringTrimOffset.toFloat(),
                    range = -50f..50f,
                    suffix = ""
                ) {
                    onUpdateTankSettings(tankSettings.copy(steeringTrimOffset = it.roundToInt()))
                }

                // Stiction / Deadband Compensation
                MonochromeParameterSlider(
                    label = "STICTION COMPENSATION (MIN START PWM)",
                    value = tankSettings.minMotorPwm.toFloat(),
                    range = 0f..50f,
                    suffix = " PWM"
                ) {
                    onUpdateTankSettings(tankSettings.copy(minMotorPwm = it.roundToInt()))
                }

                // Reset Balance Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PureBlack)
                        .border(1.dp, PillBorderMuted, RoundedCornerShape(8.dp))
                        .clickable {
                            onUpdateTankSettings(
                                tankSettings.copy(
                                    leftMotorTrimPercent = 100,
                                    rightMotorTrimPercent = 100,
                                    steeringTrimOffset = 0,
                                    minMotorPwm = 0
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↺ RESET MOTOR BALANCE TO 100% / 100% / 0",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = PureWhite
                    )
                }
            }
        }

        // 4. Network & Low-Latency Wi-Fi Lock
        item {
            Text(
                text = "ROBOT WI-FI & UDP TARGET",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = PureWhite
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MonochromeInputField("SSID", ssid) {
                    ssid = it
                    onUpdateConnectionSettings(connectionSettings.copy(ssid = it))
                }
                MonochromeInputField("PASSWORD", pass) {
                    pass = it
                    onUpdateConnectionSettings(connectionSettings.copy(passphrase = it))
                }
                MonochromeInputField("ROBOT IP", ip) {
                    ip = it
                    onUpdateConnectionSettings(connectionSettings.copy(ip = it))
                }
                MonochromeInputField("UDP PORT", portStr) {
                    portStr = it
                    val p = it.toIntOrNull() ?: 4210
                    onUpdateConnectionSettings(connectionSettings.copy(port = p))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Wi-Fi Connection & Binding Control
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isWifiLocked) PureWhite else PillSurface)
                    .border(
                        width = 1.dp,
                        color = if (isWifiLocked) PureWhite else PillBorderMuted,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { if (isWifiLocked) onUnlockWifi() else onLockWifi() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isWifiLocked) "DISCONNECT ROBOT WI-FI" else "AUTO-CONNECT ROBOT WI-FI",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = if (isWifiLocked) PureBlack else PureWhite
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "STATUS: $wifiStatusMessage",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TextSecondary
            )
        }

        // 4. GitHub In-App Auto-Update System
        item {
            Text(
                text = "APP AUTO-UPDATE (GITHUB)",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = PureWhite
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PillSurface)
                    .border(1.dp, PillBorderMuted, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "REPOSITORY:",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                    Text(
                        text = "anik-creates/robosoccer-f6x",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = PureWhite
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "INSTALLED VERSION:",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                    Text(
                        text = "v${AppUpdateManager.CURRENT_APP_VERSION}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = PureWhite
                    )
                }

                if (updateInfo != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "LATEST RELEASE:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                        Text(
                            text = updateInfo.tagName,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = PureWhite
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(PureWhite)
                            .clickable { onDownloadUpdate() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "INSTALL UPDATE NOW",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            color = PureBlack
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(PureBlack)
                            .border(1.dp, PillBorder, RoundedCornerShape(20.dp))
                            .clickable(enabled = !isCheckingUpdate) { onCheckUpdate() }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isCheckingUpdate) "CHECKING GITHUB..." else "CHECK FOR UPDATES",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            color = PureWhite
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FunctionsTabContent(
    selectedChannel: Int,
    onSelectChannel: (Int) -> Unit,
    channelConfigs: Map<Int, ChannelConfig>,
    onUpdateChannelConfig: (ChannelConfig) -> Unit
) {
    val currentConfig = channelConfigs[selectedChannel] ?: ChannelConfig(selectedChannel, "CH$selectedChannel")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Channel Selector strip
        item {
            Text(
                text = "SELECT CHANNEL",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = PureWhite
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                (1..10).forEach { ch ->
                    val isSel = ch == selectedChannel
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) PureWhite else PillSurface)
                            .border(
                                width = 1.dp,
                                color = if (isSel) PureWhite else PillBorderMuted,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectChannel(ch) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$ch",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (isSel) PureBlack else TextSecondary
                        )
                    }
                }
            }
        }

        // Live Exponential & Dual-Rate Curve Preview
        item {
            LiveCurvePreview(
                channel = selectedChannel,
                dualRate = currentConfig.dualRate,
                expo = currentConfig.expo,
                currentInput = 0f
            )
        }

        // Channel Parameters: REV / NOR
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CHANNEL DIRECTION",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = PureWhite
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (currentConfig.reverse) PureWhite else PillSurface)
                        .border(
                            width = 1.dp,
                            color = if (currentConfig.reverse) PureWhite else PillBorderMuted,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable {
                            onUpdateChannelConfig(currentConfig.copy(reverse = !currentConfig.reverse))
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (currentConfig.reverse) "REVERSE" else "NORMAL",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (currentConfig.reverse) PureBlack else PureWhite
                    )
                }
            }
        }

        // Subtrim (-120 to +120)
        item {
            MonochromeParameterSlider(
                label = "SUBTRIM",
                value = currentConfig.subtrim.toFloat(),
                range = -120f..120f,
                suffix = ""
            ) {
                onUpdateChannelConfig(currentConfig.copy(subtrim = it.roundToInt()))
            }
        }

        // Deadband (0% to 20%)
        item {
            MonochromeParameterSlider(
                label = "DEADBAND",
                value = (currentConfig.deadband * 100f),
                range = 0f..20f,
                suffix = "%"
            ) {
                onUpdateChannelConfig(currentConfig.copy(deadband = it / 100f))
            }
        }

        // EPA Low (0% to 120%)
        item {
            MonochromeParameterSlider(
                label = "EPA LOW",
                value = currentConfig.epaLow.toFloat(),
                range = 0f..120f,
                suffix = "%"
            ) {
                onUpdateChannelConfig(currentConfig.copy(epaLow = it.roundToInt()))
            }
        }

        // EPA High (0% to 120%)
        item {
            MonochromeParameterSlider(
                label = "EPA HIGH",
                value = currentConfig.epaHigh.toFloat(),
                range = 0f..120f,
                suffix = "%"
            ) {
                onUpdateChannelConfig(currentConfig.copy(epaHigh = it.roundToInt()))
            }
        }

        // Dual Rate (0% to 100%)
        item {
            MonochromeParameterSlider(
                label = "D/R (DUAL RATE)",
                value = currentConfig.dualRate.toFloat(),
                range = 0f..100f,
                suffix = "%"
            ) {
                onUpdateChannelConfig(currentConfig.copy(dualRate = it.roundToInt()))
            }
        }

        // Exponential (-100% to +100%)
        item {
            MonochromeParameterSlider(
                label = "EXP (EXPONENTIAL)",
                value = currentConfig.expo.toFloat(),
                range = -100f..100f,
                suffix = "%"
            ) {
                onUpdateChannelConfig(currentConfig.copy(expo = it.roundToInt()))
            }
        }
    }
}

@Composable
private fun MonochromeParameterSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = TextSecondary
            )
            val vInt = value.roundToInt()
            val sign = if (vInt > 0 && range.start < 0) "+" else ""
            Text(
                text = "$sign$vInt$suffix",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = PureWhite
            )
        }
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
private fun MonochromeInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PillSurface)
            .border(width = 1.dp, color = PillBorderMuted, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = TextMuted,
            modifier = Modifier.width(90.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = PureWhite
            ),
            cursorBrush = SolidColor(PureWhite),
            modifier = Modifier.weight(1f)
        )
    }
}
