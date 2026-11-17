package com.anik.f6xcontroller.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anik.f6xcontroller.network.AppUpdateManager
import com.anik.f6xcontroller.network.UpdateInfo
import com.anik.f6xcontroller.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun AppUpdateDialog(
    currentRepo: String,
    updateInfo: UpdateInfo?,
    isChecking: Boolean,
    statusText: String?,
    downloadProgress: Float?,
    onCheckUpdate: (String) -> Unit,
    onDownloadUpdate: (Context) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var repoInput by remember(currentRepo) { mutableStateOf(currentRepo) }

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
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(PureBlack)
                .border(1.dp, PillBorder, RoundedCornerShape(16.dp))
                .padding(22.dp)
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                            .clip(CircleShape)
                            .background(PureWhite)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "GITHUB APP UPDATES",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp,
                        color = PureWhite
                    )
                }

                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(PillSurface)
                        .border(1.dp, PillBorderMuted, RoundedCornerShape(15.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕ CLOSE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = PureWhite,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Info Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PillSurface)
                    .border(1.dp, PillBorderMuted, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                        fontSize = 11.sp,
                        color = PureWhite
                    )
                }

                Text(
                    text = "GITHUB REPOSITORY (OWNER/REPO):",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TextMuted
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(PureBlack)
                        .border(1.dp, PillBorderMuted, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = repoInput,
                        onValueChange = { repoInput = it },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = PureWhite
                        ),
                        cursorBrush = SolidColor(PureWhite),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Status / Error Messages
            if (!statusText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(PillSurface)
                        .border(1.dp, PillBorderMuted, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = statusText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = PureWhite
                    )
                }
            }

            // Check Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(if (isChecking) PillSurface else PureWhite)
                    .clickable(enabled = !isChecking) { onCheckUpdate(repoInput) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isChecking) "CHECKING GITHUB RELEASES..." else "CHECK FOR UPDATES NOW",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = if (isChecking) TextSecondary else PureBlack,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // If Update Available Section
            if (updateInfo != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PillSurface)
                        .border(1.dp, PureWhite, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "NEW VERSION AVAILABLE:",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = PureWhite
                        )
                        Text(
                            text = updateInfo.tagName,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = PureWhite
                        )
                    }

                    if (updateInfo.releaseNotes.isNotBlank()) {
                        Text(
                            text = updateInfo.releaseNotes,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            lineHeight = 13.sp
                        )
                    }

                    if (downloadProgress != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = PureWhite,
                                trackColor = PillBorderMuted
                            )
                            Text(
                                text = "DOWNLOADING: ${(downloadProgress * 100).roundToInt()}%",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = PureWhite
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clip(RoundedCornerShape(19.dp))
                                .background(PureWhite)
                                .clickable { onDownloadUpdate(context) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "DOWNLOAD & INSTALL UPDATE",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp,
                                color = PureBlack,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Direct Browser Link Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(PureBlack)
                    .border(1.dp, PillBorderMuted, RoundedCornerShape(17.dp))
                .clickable {
                    AppUpdateManager.openReleasesInBrowser(context, repoInput)
                },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "OPEN GITHUB RELEASES IN BROWSER ↗",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
