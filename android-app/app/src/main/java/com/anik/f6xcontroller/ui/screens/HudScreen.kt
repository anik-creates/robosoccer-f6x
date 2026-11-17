package com.anik.f6xcontroller.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
    val isTuningOpen by viewModel.isTuningOpen.collectAsState()
    val isUpdateDialogOpen by viewModel.isUpdateDialogOpen.collectAsState()

    val updateInfo by viewModel.updateInfo.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val updateStatusText by viewModel.updateStatusText.collectAsState()
    val updateProgress by viewModel.updateProgress.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // -------------------------------------------------------------------------
            // Locked-Height Top Navbar (Strictly Fixed 48.dp, Never Squeezes Vertically)
            // -------------------------------------------------------------------------
            ApexTopNavbar(
                isWifiConnected = isWifiLocked,
                telemetry = telemetry,
                hasUpdate = updateInfo != null,
                onOpenTuning = { viewModel.isTuningOpen.value = true },
                onToggleChannelMonitor = { viewModel.isChannelMonitorOpen.value = !isChannelMonitorOpen },
                onToggleQuickSetup = { viewModel.isQuickSetupOpen.value = !isQuickSetupOpen },
                onToggleEditMode = { viewModel.toggleEditMode() },
                onOpenAddChannel = { viewModel.isAddChannelOpen.value = true },
                onOpenUpdate = {
                    viewModel.isUpdateDialogOpen.value = true
                    viewModel.checkForUpdates()
                },
                onOpenMenu = { viewModel.isMenuOpen.value = true },
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
                // 1. Left Virtual Joystick (Snap-to-Center Trim Stick)
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
                        deadzone = tankSettings.deadzone,
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

                // 2. Right Virtual Joystick (CH1 STEERING + CH2 THROTTLE DRIVE with 15% Axis Snapping)
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
                        snapToAxes = tankSettings.snapToAxes,
                        snapThreshold = tankSettings.snapThreshold,
                        deadzone = tankSettings.deadzone,
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
        // Dedicated Tank Bot Tuning & Dynamics Dialog (Expo, Speed Limiter, Deadzone, Snap)
        // -----------------------------------------------------------------------------
        if (isTuningOpen) {
            TankTuningDialog(
                tankSettings = tankSettings,
                mixingMode = mixingMode,
                onUpdateTankSettings = { viewModel.updateTankSettings(it) },
                onSelectMixingMode = { viewModel.setMixingMode(it) },
                onDismiss = { viewModel.isTuningOpen.value = false }
            )
        }

        // -----------------------------------------------------------------------------
        // Dedicated GitHub App Update Dialog
        // -----------------------------------------------------------------------------
        if (isUpdateDialogOpen) {
            AppUpdateDialog(
                currentRepo = tankSettings.githubRepo,
                updateInfo = updateInfo,
                isChecking = isCheckingUpdate,
                statusText = updateStatusText,
                downloadProgress = updateProgress,
                onCheckUpdate = { repo ->
                    viewModel.updateTankSettings(tankSettings.copy(githubRepo = repo))
                    viewModel.checkForUpdates(repo)
                },
                onDownloadUpdate = { ctx -> viewModel.downloadAndInstallUpdate(ctx) },
                onDismiss = { viewModel.isUpdateDialogOpen.value = false }
            )
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
    hasUpdate: Boolean,
    onOpenTuning: () -> Unit,
    onToggleChannelMonitor: () -> Unit,
    onToggleQuickSetup: () -> Unit,
    onToggleEditMode: () -> Unit,
    onOpenAddChannel: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenMenu: () -> Unit,
    onEmergencyBrake: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(PureBlack)
            .border(width = 1.dp, color = PillBorderMuted)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Unique Brand Logo + Name + Live Bot Connection Pill (Locked 32.dp height)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(32.dp)
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
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    color = PureWhite,
                    maxLines = 1,
                    softWrap = false
                )

                // Bot Connection Status Pill
                Box(
                    modifier = Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (isWifiConnected) PureWhite else PillSurface)
                        .border(
                            width = 1.dp,
                            color = if (isWifiConnected) PureWhite else PillBorderMuted,
                            shape = RoundedCornerShape(13.dp)
                        )
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
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
                            color = if (isWifiConnected) PureBlack else TextSecondary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Center: Plain White Motor Speed Bars (Strictly Locked Height & Width, No Pill)
            LockedTankMotorSpeedBars(
                leftPwm = telemetry.leftPwm.toInt(),
                rightPwm = telemetry.rightPwm.toInt(),
                lastRttMs = telemetry.lastRttMs
            )

            // Right: Responsive Action Pills (Locked 32.dp height, Never wraps vertically)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .height(32.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                // TUNING Pill Button (Direct Access to Speed Limiter, Expo, Deadzone, Snap)
                NavPillButton(label = "TUNING", onClick = onOpenTuning)

                // CHANNELS Pill Button (Live Channel Monitor)
                NavPillButton(label = "CHANNELS", onClick = onToggleChannelMonitor)

                // + ADD CH Pill Button
                NavPillButton(label = "+ ADD CH", onClick = onOpenAddChannel)

                // SETUP Pill Button (Beginner Guide)
                NavPillButton(label = "SETUP", onClick = onToggleQuickSetup)

                // UPDATE Pill Button (Opens GitHub Update Dialog)
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (hasUpdate) PureWhite else PillSurface)
                        .border(
                            width = 1.dp,
                            color = if (hasUpdate) PureWhite else PillBorderMuted,
                            shape = RoundedCornerShape(15.dp)
                        )
                        .clickable(onClick = onOpenUpdate)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (hasUpdate) "★ UPDATE" else "UPDATE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = if (hasUpdate) PureBlack else TextSecondary,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // MENU Pill Button (FlySky Advanced Settings)
                NavPillButton(label = "MENU", onClick = onOpenMenu)

                // BRAKE / STOP Pill Button
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(PureWhite)
                        .clickable(onClick = onEmergencyBrake)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "BRAKE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp,
                        color = PureBlack,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/**
 * Plain White Locked Motor Speed Bars (No Pill, Strictly Fixed Width & Height)
 * - Zero shifting: locked container (186.dp width x 28.dp height)
 * - Plain white rectangular outline and solid fill (no pill container)
 * - Values 0..255 never change the width, layout, or alignment
 */
@Composable
private fun LockedTankMotorSpeedBars(
    leftPwm: Int,
    rightPwm: Int,
    lastRttMs: Float,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .width(186.dp)
            .height(28.dp)
    ) {
        // Left Track Plain White Bar (Strictly locked 74.dp width, 24.dp height)
        PlainWhiteTrackBar(label = "L", pwm = leftPwm, modifier = Modifier.width(74.dp).height(24.dp))

        // Right Track Plain White Bar (Strictly locked 74.dp width, 24.dp height)
        PlainWhiteTrackBar(label = "R", pwm = rightPwm, modifier = Modifier.width(74.dp).height(24.dp))

        // RTT Latency Readout (Strictly locked 26.dp width)
        Box(
            modifier = Modifier.width(26.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            val rttText = "${lastRttMs.roundToInt()}ms"
            Text(
                text = rttText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp,
                color = PureWhite,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun PlainWhiteTrackBar(
    label: String,
    pwm: Int,
    modifier: Modifier = Modifier
) {
    val norm = (pwm / 255.0f).coerceIn(-1.0f, 1.0f)
    val pct = (abs(norm) * 100).roundToInt()
    val dir = if (pwm > 0) "F" else if (pwm < 0) "R" else " "
    val speedText = "$dir$pct%".padStart(4, ' ')

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
    ) {
        // Fixed-width track label (e.g. "L" or "R")
        Box(
            modifier = Modifier.width(10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                color = PureWhite,
                maxLines = 1,
                softWrap = false
            )
        }

        // Plain White Rectangular Bar (Strictly locked 36.dp width x 12.dp height, NO PILL)
        Canvas(modifier = Modifier.size(width = 36.dp, height = 12.dp)) {
            val w = size.width
            val h = size.height
            val midX = w / 2f

            // 1. Crisp White Rectangular Outer Border (1.dp solid, NO rounded pill)
            drawRect(
                color = PureWhite,
                topLeft = Offset(0f, 0f),
                size = Size(w, h),
                style = Stroke(width = 1.dp.toPx())
            )

            // 2. Center Zero Baseline Tick
            drawLine(
                color = PureWhite,
                start = Offset(midX, 0f),
                end = Offset(midX, h),
                strokeWidth = 1.dp.toPx()
            )

            // 3. Plain White Solid Fill (Left = REV, Right = FWD)
            val maxFillW = (midX - 2.dp.toPx()).coerceAtLeast(0f)
            if (norm > 0f) {
                // Forward (Fill rightwards)
                val fillW = maxFillW * norm
                drawRect(
                    color = PureWhite,
                    topLeft = Offset(midX + 1.dp.toPx(), 2.dp.toPx()),
                    size = Size(fillW, h - 4.dp.toPx())
                )
            } else if (norm < 0f) {
                // Reverse (Fill leftwards)
                val fillW = maxFillW * abs(norm)
                drawRect(
                    color = PureWhite,
                    topLeft = Offset(midX - 1.dp.toPx() - fillW, 2.dp.toPx()),
                    size = Size(fillW, h - 4.dp.toPx())
                )
            }
        }

        // Fixed-width percentage readout (Strictly locked 24.dp width)
        Box(
            modifier = Modifier.width(24.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = speedText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp,
                color = PureWhite,
                maxLines = 1,
                softWrap = false
            )
        }
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
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (isSelected) PureWhite else PillSurface)
            .border(
                width = 1.dp,
                color = if (isSelected) PureWhite else PillBorderMuted,
                shape = RoundedCornerShape(15.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = if (isSelected) PureBlack else PureWhite,
            maxLines = 1,
            softWrap = false
        )
    }
}
