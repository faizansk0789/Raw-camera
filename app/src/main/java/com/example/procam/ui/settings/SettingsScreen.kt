package com.example.procam.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.procam.model.GridType
import com.example.procam.model.PhotoFormat
import com.example.procam.ui.components.PillButton
import com.example.procam.viewmodel.CameraViewModel
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.FocusCyan
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.TextLight

@Composable
fun SettingsScreen(
    viewModel: CameraViewModel,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val manualControls by viewModel.manualControls.collectAsState()
    val videoSettings by viewModel.videoSettings.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextLight)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "CAMERA SETTINGS",
                color = AccentAmber,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Diagnostics Banner Button
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDiagnostics() },
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AccentAmber)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Hardware, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Camera2 Hardware Diagnostics",
                                color = TextLight,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "View sensors, ISO ranges, shutter limits & driver capabilities",
                                color = SecondaryText,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AccentAmber)
                }
            }

            // Capture Defaults
            SettingsSection(title = "CAPTURE DEFAULTS") {
                // Photo Format
                Text("Default Photo Format", color = TextLight, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PhotoFormat.JPEG, PhotoFormat.RAW_DNG, PhotoFormat.RAW_PLUS_JPEG).forEach { fmt ->
                        PillButton(
                            text = fmt.label,
                            isActive = manualControls.photoFormat == fmt,
                            onClick = { viewModel.setPhotoFormat(fmt) }
                        )
                    }
                }

                HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 12.dp))

                // Video Codec
                Text("Video Encoding Codec", color = TextLight, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("H.264", "HEVC").forEach { codec ->
                        PillButton(
                            text = codec,
                            isActive = videoSettings.codec == codec,
                            onClick = { /* codec */ }
                        )
                    }
                }
            }

            // Assistance Overlays
            SettingsSection(title = "PHOTOGRAPHY ASSISTANCE") {
                SettingToggleRow(
                    title = "Live Histogram",
                    subtitle = "Luminance and RGB clipping monitor",
                    checked = manualControls.showHistogram,
                    onCheckedChange = { viewModel.toggleHistogram() }
                )
                SettingToggleRow(
                    title = "Electronic Horizon Level",
                    subtitle = "High precision roll angle with zero-degree lock",
                    checked = manualControls.showLevelHorizon,
                    onCheckedChange = { viewModel.toggleLevel() }
                )
                SettingToggleRow(
                    title = "Focus Peaking",
                    subtitle = "Cyan edge highlight for manual focus verification",
                    checked = manualControls.focusPeakingEnabled,
                    onCheckedChange = { viewModel.toggleFocusPeaking() }
                )
                SettingToggleRow(
                    title = "Haptic Vibration",
                    subtitle = "Tactile mechanical feedback when turning dials",
                    checked = manualControls.hapticFeedback,
                    onCheckedChange = { /* toggle */ }
                )
            }

            // About Device Section
            SettingsSection(title = "DEVICE & ENVIRONMENT") {
                Text(
                    text = "Engine: Android Native Camera2 (Direct HAL Pipeline)\n" +
                            "Target Device: Samsung Galaxy A55 5G\n" +
                            "Sensors: 50MP Wide (f/1.8 OIS), 12MP Ultrawide, 5MP Macro\n" +
                            "Storage: Scoped MediaStore (DCIM/AperturePro)\n" +
                            "Privacy: 100% on-device processing, zero external tracking",
                    color = SecondaryText,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = AccentAmber,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = SecondaryText, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AccentAmber,
                checkedTrackColor = DarkSurfaceElevated,
                uncheckedThumbColor = SecondaryText,
                uncheckedTrackColor = DarkBorder
            )
        )
    }
}
