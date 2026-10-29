package com.example.procam.ui.diagnostics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.procam.model.CameraHardwareSpec
import com.example.procam.viewmodel.CameraViewModel
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.FocusCyan
import com.example.ui.theme.FocusGreen
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.TextLight
import com.example.ui.theme.WarningYellow

@Composable
fun DiagnosticScreen(
    viewModel: CameraViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val cameraSpecs by viewModel.cameraSpecs.collectAsState()

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
            Column {
                Text(
                    text = "HARDWARE CAPABILITY REPORT",
                    color = AccentAmber,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Samsung Galaxy A55 5G Camera2 Diagnostics",
                    color = SecondaryText,
                    fontSize = 11.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(cameraSpecs) { spec ->
                CameraModuleCard(spec = spec)
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun CameraModuleCard(spec: CameraHardwareSpec) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Module Title & Hardware Level
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = spec.facing,
                    color = TextLight,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Box(
                    modifier = Modifier
                        .background(if (spec.hardwareLevel == "FULL" || spec.hardwareLevel == "LEVEL_3") FocusGreen else WarningYellow, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "LEVEL ${spec.hardwareLevel}",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Capabilities Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CapabilityStatus("MANUAL SENSOR", spec.hasManualSensor)
                CapabilityStatus("RAW (DNG)", spec.hasRawSupport)
                CapabilityStatus("OPTICAL OIS", spec.hasOis)
                CapabilityStatus("MANUAL FOCUS", spec.supportsManualFocus)
            }

            HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 12.dp))

            // Technical Specs
            SpecRow("ISO Range", "${spec.isoRange?.lower ?: 50} – ${spec.isoRange?.upper ?: 3200}")
            SpecRow("Shutter Speed Range", "${spec.minExposureSecFormatted} to ${spec.maxExposureSecFormatted}")
            SpecRow("Apertures", if (spec.isFixedAperture) "f/${spec.availableApertures.firstOrNull() ?: 1.8f} (Fixed Physical Lens)" else spec.availableApertures.joinToString { "f/$it" })
            SpecRow("Min Focus Distance", if (spec.supportsManualFocus) "${spec.minFocusDistanceDiopters} Diopters (Macro to ∞)" else "Fixed Focus Lens")
            SpecRow("EV Compensation", "${spec.evRange?.lower ?: -6} to +${spec.evRange?.upper ?: 6} (Step: ${String.format("%.2f", spec.evStep)} EV)")
            SpecRow("Max Digital Zoom", "${spec.maxZoom}x")

            if (spec.supportedJpegResolutions.isNotEmpty()) {
                val maxJpeg = spec.supportedJpegResolutions.first()
                val mp = (maxJpeg.width * maxJpeg.height) / 1_000_000.0
                SpecRow("Max Photo Resolution", "${maxJpeg.width} x ${maxJpeg.height} (${String.format("%.1f", mp)} MP)")
            }

            if (spec.supportedVideoResolutions.isNotEmpty()) {
                val has4k = spec.supportedVideoResolutions.any { it.width >= 3840 }
                SpecRow("Video Capabilities", if (has4k) "4K UHD, 1080p FHD, 720p HD" else "1080p FHD, 720p HD")
            }

            // Hardware Limitations Rationale
            if (spec.limitationsSummary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Hardware Constraints & Driver Rationale:",
                    color = WarningYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                spec.limitationsSummary.forEach { lim ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("• ", color = WarningYellow, fontSize = 11.sp)
                        Text(lim, color = SecondaryText, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CapabilityStatus(label: String, supported: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = if (supported) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (supported) FocusGreen else WarningYellow,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            color = SecondaryText,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = SecondaryText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = AccentAmber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}
