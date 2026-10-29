package com.example.procam.ui.components

import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
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
import com.example.procam.model.CameraHardwareSpec
import com.example.procam.model.LiveMetrics
import com.example.procam.model.VideoSettings
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.FocusGreen
import com.example.ui.theme.RecordRed
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.TextLight
import com.example.ui.theme.WarningYellow

@Composable
fun VideoControlsBar(
    spec: CameraHardwareSpec?,
    videoSettings: VideoSettings,
    liveMetrics: LiveMetrics,
    onResolutionChange: (Size) -> Unit,
    onFpsChange: (Int) -> Unit,
    onBitrateChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xE60B0D13))
            .border(1.dp, Color(0x33334155))
            .padding(8.dp)
    ) {
        // Top row: Format Pills & 180° rule recommendation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Resolutions
            val is4k = videoSettings.resolution.width >= 3840
            val is1080p = videoSettings.resolution.width in 1920..3839
            val is720p = videoSettings.resolution.width < 1920

            PillButton(
                text = "4K UHD",
                isActive = is4k,
                onClick = { onResolutionChange(Size(3840, 2160)) }
            )
            PillButton(
                text = "1080p FHD",
                isActive = is1080p,
                onClick = { onResolutionChange(Size(1920, 1080)) }
            )
            PillButton(
                text = "720p HD",
                isActive = is720p,
                onClick = { onResolutionChange(Size(1280, 720)) }
            )

            // FPS Options
            listOf(24, 25, 30, 60).forEach { fps ->
                PillButton(
                    text = "${fps} FPS",
                    isActive = videoSettings.fps == fps,
                    onClick = { onFpsChange(fps) }
                )
            }

            // Bitrate Options
            listOf(16, 28, 50).forEach { mbps ->
                PillButton(
                    text = "${mbps} Mbps",
                    isActive = videoSettings.bitrateMbps == mbps,
                    onClick = { onBitrateChange(mbps) }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Audio VU meter and 180° shutter helper
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Audio Level Meter
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (videoSettings.audioEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = null,
                    tint = if (videoSettings.audioEnabled) FocusGreen else SecondaryText,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                // VU Bar
                Box(
                    modifier = Modifier
                        .width(90.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1E232B))
                ) {
                    val progress = if (liveMetrics.isRecording) liveMetrics.audioLevelPercent else 0.35f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progress.coerceIn(0.05f, 1f))
                            .height(8.dp)
                            .background(if (progress > 0.85f) RecordRed else if (progress > 0.7f) WarningYellow else FocusGreen)
                    )
                }
            }

            // 180° Shutter Tip
            val recShutterFraction = videoSettings.fps * 2
            Text(
                text = "180° Cinematic Shutter: 1/${recShutterFraction}s",
                color = AccentAmber,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
