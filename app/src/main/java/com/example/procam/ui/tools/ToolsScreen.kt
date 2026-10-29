package com.example.procam.ui.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShutterSpeed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.procam.model.CameraMode
import com.example.procam.model.ToolPreset
import com.example.procam.ui.components.PillButton
import com.example.procam.ui.components.formatShutter
import com.example.procam.viewmodel.CameraViewModel
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.FocusCyan
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.TextLight
import com.example.ui.theme.WarningYellow
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ToolsScreen(
    viewModel: CameraViewModel,
    onBackToPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBackToPhoto() }

    val activeSpec by viewModel.cameraEngine.activeSpec.collectAsState()
    val manualControls by viewModel.manualControls.collectAsState()
    val liveMetrics by viewModel.cameraEngine.liveMetrics.collectAsState()

    var longExposureSec by remember { mutableFloatStateOf(10f) }
    var aebFrameCount by remember { mutableIntStateOf(3) }
    var aebEvStep by remember { mutableFloatStateOf(1.0f) }
    var timelapseIntervalSec by remember { mutableIntStateOf(5) }
    var timelapseDurationMin by remember { mutableIntStateOf(15) }

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
            IconButton(onClick = onBackToPhoto) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextLight)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "PROFESSIONAL TOOLS",
                    color = AccentAmber,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Extended exposures, bracketing, timelapse, and presets",
                    color = SecondaryText,
                    fontSize = 12.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Long Exposure & Star Trails Card
            item {
                ToolSectionCard(
                    title = "LONG EXPOSURE & STAR TRAILS",
                    icon = Icons.Default.NightsStay
                ) {
                    val maxHw = activeSpec?.maxExposureSec ?: 0.5
                    val exceedsHardware = longExposureSec > maxHw

                    Text(
                        text = "Target Exposure: ${longExposureSec.toInt()} Seconds",
                        color = TextLight,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (exceedsHardware) {
                        Text(
                            text = "COMPUTATIONAL STACKING: Device hardware max exposure is ${activeSpec?.maxExposureSecFormatted ?: "0.5s"}. Durations up to 60s combine sequential exposures to prevent ISP driver timeouts.",
                            color = WarningYellow,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    } else {
                        Text(
                            text = "GENUINE HARDWARE EXPOSURE: Supported directly by camera sensor.",
                            color = FocusCyan,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(2f, 5f, 10f, 20f, 30f, 60f).forEach { sec ->
                            PillButton(
                                text = "${sec.toInt()}s",
                                isActive = longExposureSec == sec,
                                onClick = { longExposureSec = sec }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = longExposureSec,
                        onValueChange = { longExposureSec = it },
                        valueRange = 1f..60f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentAmber,
                            activeTrackColor = AccentAmber,
                            inactiveTrackColor = DarkBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.setShutterSpeed(longExposureSec.toDouble(), isManual = true)
                            viewModel.setMode(CameraMode.PHOTO)
                            viewModel.capturePhoto()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "START ${longExposureSec.toInt()}s EXPOSURE",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // 2. Exposure Bracketing (AEB)
            item {
                ToolSectionCard(
                    title = "EXPOSURE BRACKETING (AEB)",
                    icon = Icons.Default.Exposure
                ) {
                    Text(
                        text = "Capture sequential frames at varying EV offsets for HDR merging.",
                        color = SecondaryText,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Frame Count:", color = TextLight, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(3, 5).forEach { count ->
                                PillButton(
                                    text = "$count Frames",
                                    isActive = aebFrameCount == count,
                                    onClick = { aebFrameCount = count }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("EV Step Interval:", color = TextLight, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0.7f, 1.0f, 2.0f).forEach { step ->
                                PillButton(
                                    text = "±${step} EV",
                                    isActive = aebEvStep == step,
                                    onClick = { aebEvStep = step }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.setMode(CameraMode.PHOTO)
                            viewModel.capturePhoto()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "CAPTURE $aebFrameCount-FRAME AEB",
                            color = AccentAmber,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // 3. Timelapse Intervalometer
            item {
                ToolSectionCard(
                    title = "TIMELAPSE INTERVALOMETER",
                    icon = Icons.Default.Schedule
                ) {
                    Text(
                        text = "Capture interval frames for smooth accelerated video clips.",
                        color = SecondaryText,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 2, 5, 10, 30).forEach { s ->
                            PillButton(
                                text = "Every ${s}s",
                                isActive = timelapseIntervalSec == s,
                                onClick = { timelapseIntervalSec = s }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Calculated stats
                    val totalFrames = (timelapseDurationMin * 60) / timelapseIntervalSec
                    val videoDurationSec = totalFrames / 30
                    Text(
                        text = "Estimate: $totalFrames frames → ${videoDurationSec}s video @ 30 FPS",
                        color = FocusCyan,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // 4. Professional Photography Presets
            item {
                Text(
                    text = "PROFESSIONAL PRESETS",
                    color = AccentAmber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(viewModel.presets) { preset ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.name,
                                color = TextLight,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = preset.description,
                                color = SecondaryText,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "ISO ${preset.iso}",
                                    color = AccentAmber,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = formatShutter(preset.shutterSpeedSec),
                                    color = AccentAmber,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = preset.format.label,
                                    color = FocusCyan,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.applyPreset(preset)
                                viewModel.setMode(CameraMode.PHOTO)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("LOAD", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ToolSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = AccentAmber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
