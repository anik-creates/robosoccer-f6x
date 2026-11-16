package com.anik.f6xcontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import kotlin.math.roundToInt

@Composable
fun EditHudInspectorBar(
    modifier: Modifier = Modifier,
    selectedId: String?,
    selectedName: String,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onCloseEditMode: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(PureBlack)
            .border(width = 1.dp, color = PillBorder)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Title and selection status
            Column {
                Text(
                    text = "LAYOUT CUSTOMIZER (DRAG & RESIZE)",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = PureWhite
                )
                Text(
                    text = if (selectedId != null) "SELECTED: $selectedName" else "TAP ANY WIDGET TO RESIZE OR DRAG",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal,
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }

            // Center: Scale Slider (50% to 160%)
            if (selectedId != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.width(280.dp)
                ) {
                    val pct = (scale * 100f).roundToInt()
                    Text(
                        text = "SCALE: $pct%",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = PureWhite,
                        modifier = Modifier.width(85.dp)
                    )
                    Slider(
                        value = scale,
                        onValueChange = onScaleChange,
                        valueRange = 0.5f..1.6f,
                        colors = SliderDefaults.colors(
                            thumbColor = PureWhite,
                            activeTrackColor = PureWhite,
                            inactiveTrackColor = PillBorderMuted
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Right: Actions (Pill buttons)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (canDelete) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(PureBlack)
                            .border(width = 1.dp, color = PillBorderMuted, shape = RoundedCornerShape(20.dp))
                            .clickable { onDelete() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "REMOVE",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = PureWhite
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(PureWhite)
                        .clickable { onCloseEditMode() }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "DONE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = PureBlack
                    )
                }
            }
        }
    }
}
