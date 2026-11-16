package com.anik.f6xcontroller.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anik.f6xcontroller.data.WidgetType
import com.anik.f6xcontroller.ui.components.*
import com.anik.f6xcontroller.ui.theme.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun HudScreen(
    viewModel: HudViewModel
) {
    val context = LocalContext.current

    val rawSticks by viewModel.rawSticks.collectAsState()
    val mixingMode by viewModel.mixingMode.collectAsState()
    val tankSettings by viewModel.tankSettings.collectAsState()
    val connectionSettings by viewModel.connectionSettings.collectAsState()
    val channelConfigs by viewModel.channelConfigs.collectAsState()
    val joystickPositions by viewModel.joystickPositions.collectAsState()
    val auxWidgets by viewModel.auxWidgets.collectAsState()
    val auxValues by viewModel.auxValues.collectAsState()

    val isWifiLocked by viewModel.wifiLockManager.isLocked.collectAsState()
    val wifiStatusMessage by viewModel.wifiLockManager.statusMessage.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val processedChannels by viewModel.processedChannels.collectAsState()

    val isEditMode by viewModel.isEditMode.collectAsState()
    val selectedWidgetId by viewModel.selectedWidgetId.collectAsState()
    val isMenuOpen by viewModel.isMenuOpen.collectAsState()
    val isAddChannelOpen by viewModel.isAddChannelOpen.collectAsState()
    val isChannelMonitorOpen by viewModel.isChannelMonitorOpen.collectAsState()
    val isQuickSetupOpen by viewModel.isQuickSetupOpen.collectAsState()

    val updateInfo by viewModel.updateInfo.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // -------------------------------------------------------------------------
            // Top Modern Designer Navbar with Live Motor Graph (TX Count OFF)
            // -------------------------------------------------------------------------
            ApexTopNavbar(
                isWifiConnected = isWifiLocked,
                telemetry = telemetry,
                isEditMode = isEditMode,
                hasUpdate = updateInfo != null,
                onToggleChannelMonitor = { viewModel.isChannelMonitorOpen.value = !isChannelMonitorOpen },
                onToggleQuickSetup = { viewModel.isQuickSetupOpen.value = !isQuickSetupOpen },
                onToggleEditMode = { viewModel.toggleEditMode() },
                onOpenAddChannel = { viewModel.isAddChannelOpen.value = true },
                onOpenMenu = { viewModel.isMenuOpen.value = true },
                onUpdateClick = {
                    if (updateInfo != null) {
                        viewModel.downloadAndInstallUpdate(context)
                    } else {
                        viewModel.checkForUpdates()
                    }
                },
                onEmergencyBrake = { viewModel.emergencyStop() }
            )

            // -------------------------------------------------------------------------
            // Main Arena Control Canvas (Fixed, Rock-Solid, Non-Shifting Layout)
            // -------------------------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(PureBlack)
            ) {
                // 1. Left Virtual Joystick (Snap to Center Trim Stick)
                // Anchored firmly at Bottom-Start with fixed margin (zero drifting)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 32.dp, bottom = 24.dp)
                ) {
                    VirtualJoystick(
                        title = "LEFT STICK (TRIM)",
                        xLabel = "CH4",
                        yLabel = "CH3",
                        xValue = rawSticks.leftX,
                        yValue = rawSticks.leftY,
                        autoCenterX = true,
                        autoCenterY = tankSettings.leftStickSnapCenter,
                        scale = joystickPositions.leftScale,
                        isEditMode = isEditMode,
                        isSelected = selectedWidgetId == "STICK_LEFT",
                        onSelect = { viewModel.selectWidget("STICK_LEFT") },
                        onValueChange = { x, y -> viewModel.updateLeftStick(x, y) },
                        onToggleAutoCenter = {
                            viewModel.updateTankSettings(
                                tankSettings.copy(leftStickSnapCenter = !tankSettings.leftStickSnapCenter)
                            )
                        }
                    )
                }

                // 2. Right Virtual Joystick (CH1 STEERING + CH2 THROTTLE DRIVE)
                // Anchored firmly at Bottom-End with fixed margin (zero drifting)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 32.dp, bottom = 24.dp)
                ) {
                    VirtualJoystick(
                        title = "DRIVE (CH1+CH2)",
                        xLabel = "CH1 STEER",
                        yLabel = "CH2 FWD/REV",
                        xValue = rawSticks.rightX,
                        yValue = rawSticks.rightY,
                        autoCenterX = true,
                        autoCenterY = true,
                        scale = joystickPositions.rightScale,
                        isEditMode = isEditMode,
                        isSelected = selectedWidgetId == "STICK_RIGHT",
                        onSelect = { viewModel.selectWidget("STICK_RIGHT") },
                        onValueChange = { x, y -> viewModel.updateRightStick(x, y) },
                        onToggleAutoCenter = {}
                    )
                }

                // 3. Dynamic Auxiliary Widgets (CH5–CH10) - Empty initially by request!
                if (auxWidgets.isNotEmpty()) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val canvasWidthPx = constraints.maxWidth.toFloat()
                        val canvasHeightPx = constraints.maxHeight.toFloat()
                        val density = LocalDensity.current.density

                        auxWidgets.forEach { widget ->
                            val wPxX = canvasWidthPx * widget.normalizedX
                            val wPxY = canvasHeightPx * widget.normalizedY
                            val wOffsetPxX = (wPxX - (40.dp.value * density * widget.scale)).coerceIn(0f, canvasWidthPx - 60f)
                            val wOffsetPxY = (wPxY - (50.dp.value * density * widget.scale)).coerceIn(0f, canvasHeightPx - 60f)

                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(wOffsetPxX.roundToInt(), wOffsetPxY.roundToInt()) }
                                    .then(
                                        if (isEditMode) {
                                            Modifier.pointerInput(widget.id) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    viewModel.selectWidget(widget.id)
                                                    val newNormX = (wPxX + dragAmount.x) / canvasWidthPx
                                                    val newNormY = (wPxY + dragAmount.y) / canvasHeightPx
                                                    viewModel.updateWidgetPosition(widget.id, newNormX, newNormY)
                                                }
                                            }
                                        } else Modifier
                                    )
                            ) {
                                val isSel = selectedWidgetId == widget.id
                                val curVal = auxValues[widget.channel] ?: 0f

                                when (widget.type) {
                                    WidgetType.SWITCH_2POS -> {
                                        AuxSwitch2Pos(
                                            channel = widget.channel,
                                            value = curVal,
                                            scale = widget.scale,
                                            isEditMode = isEditMode,
                                            isSelected = isSel,
                                            onSelect = { viewModel.selectWidget(widget.id) },
                                            onValueChange = { viewModel.updateAuxChannel(widget.channel, it) }
                                        )
                                    }
                                    WidgetType.SWITCH_3POS -> {
                                        AuxSwitch3Pos(
                                            channel = widget.channel,
                                            value = curVal,
                                            scale = widget.scale,
                                            isEditMode = isEditMode,
                                            isSelected = isSel,
                                            onSelect = { viewModel.selectWidget(widget.id) },
                                            onValueChange = { viewModel.updateAuxChannel(widget.channel, it) }
                                        )
                                    }
                                    WidgetType.ROTARY_SLIDER -> {
                                        AuxRotarySlider(
                                            channel = widget.channel,
                                            value = curVal,
                                            scale = widget.scale,
                                            isEditMode = isEditMode,
                                            isSelected = isSel,
                                            onSelect = { viewModel.selectWidget(widget.id) },
                                            onValueChange = { viewModel.updateAuxChannel(widget.channel, it) }
                                        )
                                    }
                                    WidgetType.MOMENTARY_BUTTON -> {
                                        AuxMomentaryButton(
                                            channel = widget.channel,
                                            value = curVal,
                                            scale = widget.scale,
                                            isEditMode = isEditMode,
                                            isSelected = isSel,
                                            onSelect = { viewModel.selectWidget(widget.id) },
                                            onValueChange = { viewModel.updateAuxChannel(widget.channel, it) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------------------
            // Edit Mode Inspector Bar (Appears when EDIT HUD is active)
            // -------------------------------------------------------------------------
            if (isEditMode) {
                val currentScale = when (selectedWidgetId) {
                    "STICK_LEFT" -> joystickPositions.leftScale
                    "STICK_RIGHT" -> joystickPositions.rightScale
                    null -> 1.0f
                    else -> auxWidgets.find { it.id == selectedWidgetId }?.scale ?: 1.0f
                }
                val currentName = when (selectedWidgetId) {
                    "STICK_LEFT" -> "LEFT JOYSTICK"
                    "STICK_RIGHT" -> "RIGHT JOYSTICK (DRIVE)"
                    null -> "NONE (TAP ANY CONTROL)"
                    else -> {
                        val w = auxWidgets.find { it.id == selectedWidgetId }
                        if (w != null) "CH${w.channel} (${w.type.label})" else "WIDGET"
                    }
                }
                val canDelete = selectedWidgetId?.startsWith("AUX_") == true

                EditHudInspectorBar(
                    selectedId = selectedWidgetId,
                    selectedName = currentName,
                    scale = currentScale,
                    onScaleChange = { viewModel.updateWidgetScale(it) },
                    canDelete = canDelete,
                    onDelete = { viewModel.deleteSelectedWidget() },
                    onCloseEditMode = { viewModel.toggleEditMode() }
                )
            }
        }

        // -----------------------------------------------------------------------------
        // Live Channel Output Monitor Overlay
        // -----------------------------------------------------------------------------
        if (isChannelMonitorOpen) {
            ChannelMonitorOverlay(
                channels = processedChannels,
                telemetry = telemetry,
                auxValues = auxValues,
                onClose = { viewModel.isChannelMonitorOpen.value = false }
            )
        }

        // -----------------------------------------------------------------------------
        // Beginner Quick Setup Guide Dialog
        // -----------------------------------------------------------------------------
        if (isQuickSetupOpen) {
            QuickSetupDialog(
                isConnected = isWifiLocked,
                onConnectWifiClick = {
                    viewModel.lockWifi()
                    viewModel.isQuickSetupOpen.value = false
                },
                onDismiss = { viewModel.isQuickSetupOpen.value = false }
            )
        }

        // -----------------------------------------------------------------------------
        // FS-i6X Slide-Over Menu (SYSTEM Tank Drive & FUNCTIONS RC DSP)
        // -----------------------------------------------------------------------------
        if (isMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ModalScrim)
                    .clickable { viewModel.isMenuOpen.value = false }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable(enabled = false) {}
                ) {
                    FsI6xMenuDrawer(
                        mixingMode = mixingMode,
                        tankSettings = tankSettings,
                        connectionSettings = connectionSettings,
                        channelConfigs = channelConfigs,
                        isWifiLocked = isWifiLocked,
                        wifiStatusMessage = wifiStatusMessage,
                        updateInfo = updateInfo,
                        isCheckingUpdate = isCheckingUpdate,
                        onCheckUpdate = { viewModel.checkForUpdates() },
                        onDownloadUpdate = { viewModel.downloadAndInstallUpdate(context) },
                        onSelectMixingMode = { viewModel.setMixingMode(it) },
                        onUpdateTankSettings = { viewModel.updateTankSettings(it) },
                        onUpdateConnectionSettings = { viewModel.updateConnectionSettings(it) },
                        onUpdateChannelConfig = { viewModel.updateChannelConfig(it) },
                        onLockWifi = { viewModel.lockWifi() },
                        onUnlockWifi = { viewModel.unlockWifi() },
                        onClose = { viewModel.isMenuOpen.value = false }
                    )
                }
            }
        }

        // -----------------------------------------------------------------------------
        // "+ ADD CH" Modal Dialog
        // -----------------------------------------------------------------------------
        if (isAddChannelOpen) {
            AddChannelDialog(
                onDismiss = { viewModel.isAddChannelOpen.value = false },
                onAddWidget = { ch, type -> viewModel.addAuxWidget(ch, type) }
            )
        }
    }
}

@Composable
private fun ApexTopNavbar(
    isWifiConnected: Boolean,
    telemetry: com.anik.f6xcontroller.data.TelemetryStats,
    isEditMode: Boolean,
    hasUpdate: Boolean,
    onToggleChannelMonitor: () -> Unit,
    onToggleQuickSetup: () -> Unit,
    onToggleEditMode: () -> Unit,
    onOpenAddChannel: () -> Unit,
    onOpenMenu: () -> Unit,
    onUpdateClick: () -> Unit,
    onEmergencyBrake: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PureBlack)
            .border(width = 1.dp, color = PillBorderMuted)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Unique Brand Logo + Name + Live Bot Connection Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Sleek Geometric Vector Logo (Hexagon Soccer Motive)
                Canvas(modifier = Modifier.size(24.dp)) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension / 2f - 1.dp.toPx()
                    val path = Path()
                    for (i in 0 until 6) {
                        val angle = (Math.PI / 3 * i - Math.PI / 2).toFloat()
                        val x = center.x + radius * cos(angle)
                        val y = center.y + radius * sin(angle)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(path, color = PureWhite, style = Stroke(width = 1.5.dp.toPx()))

                    // Inner apex triangle
                    val innerPath = Path()
                    val innerRadius = radius * 0.55f
                    for (i in 0 until 3) {
                        val angle = (Math.PI * 2 / 3 * i - Math.PI / 2).toFloat()
                        val x = center.x + innerRadius * cos(angle)
                        val y = center.y + innerRadius * sin(angle)
                        if (i == 0) innerPath.moveTo(x, y) else innerPath.lineTo(x, y)
                    }
                    innerPath.close()
                    drawPath(innerPath, color = PureWhite, style = Stroke(width = 1.5.dp.toPx()))
                }

                // Brand Name
                Text(
                    text = "APEX-F6X",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.5.sp,
                    color = PureWhite
                )

                // Bot Connection Status Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isWifiConnected) PureWhite else PillSurface)
                        .border(
                            width = 1.dp,
                            color = if (isWifiConnected) PureWhite else PillBorderMuted,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isWifiConnected) PureBlack else TextSecondary)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isWifiConnected) "CONNECTED" else "SEARCHING",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 0.5.sp,
                            color = if (isWifiConnected) PureBlack else TextSecondary
                        )
                    }
                }
            }

            // Center: Live Left & Right Motor Speed Graph (Forward & Reverse) - TX Count OFF!
            TankMotorSpeedGraph(
                leftPwm = telemetry.leftPwm.toInt(),
                rightPwm = telemetry.rightPwm.toInt(),
                lastRttMs = telemetry.lastRttMs,
                ackCount = telemetry.ackCount
            )

            // Right: Modern Designer Navigation Action Pills
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // CHANNELS Pill Button (Live Channel Monitor)
                NavPillButton(label = "CHANNELS", onClick = onToggleChannelMonitor)

                // + ADD CH Pill Button
                NavPillButton(label = "+ ADD CH", onClick = onOpenAddChannel)

                // EDIT HUD Pill Button
                NavPillButton(
                    label = if (isEditMode) "[EDITING]" else "EDIT HUD",
                    isSelected = isEditMode,
                    onClick = onToggleEditMode
                )

                // SETUP Pill Button (Beginner Guide)
                NavPillButton(label = "SETUP", onClick = onToggleQuickSetup)

                // UPDATE Pill Button (with badge if update found)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (hasUpdate) PureWhite else PillSurface)
                        .border(
                            width = 1.dp,
                            color = if (hasUpdate) PureWhite else PillBorderMuted,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable(onClick = onUpdateClick)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (hasUpdate) "★ UPDATE" else "UPDATE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp,
                        color = if (hasUpdate) PureBlack else TextSecondary
                    )
                }

                // MENU Pill Button
                NavPillButton(label = "MENU", onClick = onOpenMenu)

                // BRAKE / STOP Pill Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(PureWhite)
                        .clickable(onClick = onEmergencyBrake)
                        .padding(horizontal = 11.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "BRAKE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp,
                        color = PureBlack
                    )
                }
            }
        }
    }
}

/**
 * Live Bipolar Motor Speed Graph for 4WD Tank Drive (Forward & Reverse)
 * Completely eliminates TX count clutter, replacing it with an intuitive visual speed graph.
 */
@Composable
private fun TankMotorSpeedGraph(
    leftPwm: Int,
    rightPwm: Int,
    lastRttMs: Float,
    ackCount: Long
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left Track Dynamic Meter
        SingleTrackSpeedMeter(label = "L TRACK", pwm = leftPwm)

        // Right Track Dynamic Meter
        SingleTrackSpeedMeter(label = "R TRACK", pwm = rightPwm)

        // RTT Latency
        Text(
            text = "${"%.1f".format(lastRttMs)}ms",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = PureWhite
        )
    }
}

@Composable
private fun SingleTrackSpeedMeter(
    label: String,
    pwm: Int
) {
    val norm = (pwm / 255.0f).coerceIn(-1.0f, 1.0f)
    val isFwd = pwm >= 0
    val pct = (abs(norm) * 100).roundToInt()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PillSurface)
            .border(1.dp, PillBorderMuted, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            color = TextSecondary
        )

        // Bipolar Bar Graphic (Center 0, Left = REV, Right = FWD)
        Canvas(modifier = Modifier.size(width = 60.dp, height = 12.dp)) {
            val w = size.width
            val h = size.height
            val midX = w / 2f

            // Background slot
            drawRoundRect(
                color = Color(0xFF1E1E1E),
                topLeft = Offset(0f, 0f),
                size = Size(w, h),
                cornerRadius = CornerRadius(2.dp.toPx())
            )

            // Center baseline
            drawLine(
                color = PillBorderMuted,
                start = Offset(midX, 0f),
                end = Offset(midX, h),
                strokeWidth = 1.dp.toPx()
            )

            // Active bar
            if (norm > 0) {
                // Forward (Right fill)
                val barW = (midX * norm)
                drawRoundRect(
                    color = PureWhite,
                    topLeft = Offset(midX, 2f),
                    size = Size(barW, h - 4f),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )
            } else if (norm < 0) {
                // Reverse (Left fill)
                val barW = (midX * abs(norm))
                drawRoundRect(
                    color = Color(0xFFAAAAAA),
                    topLeft = Offset(midX - barW, 2f),
                    size = Size(barW, h - 4f),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )
            }
        }

        // Percentage & Direction
        val dir = if (pwm > 0) "FWD" else if (pwm < 0) "REV" else "STOP"
        Text(
            text = "$dir $pct%",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            color = PureWhite
        )
    }
}

@Composable
private fun NavPillButton(
    label: String,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) PureWhite else PillSurface)
            .border(
                width = 1.dp,
                color = if (isSelected) PureWhite else PillBorderMuted,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
            color = if (isSelected) PureBlack else PureWhite
        )
    }
}
