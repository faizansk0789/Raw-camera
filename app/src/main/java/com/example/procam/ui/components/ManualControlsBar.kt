package com.example.procam.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.example.procam.model.ManualControlState
import com.example.procam.model.VideoSettings
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentAmberDim
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.FocusCyan
import com.example.ui.theme.FocusGreen
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.TextLight
import com.example.ui.theme.WarningYellow
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ManualControlsBar(
    spec: CameraHardwareSpec?,
    controls: ManualControlState,
    liveMetrics: LiveMetrics,
    activeDrawer: String?,
    videoSettings: VideoSettings,
    isVideoMode: Boolean,
    onDrawerSelect: (String?) -> Unit,
    onIsoChange: (Int, Boolean) -> Unit,
    onShutterChange: (Double, Boolean) -> Unit,
    onFocusChange: (Float, Boolean) -> Unit,
    onWbChange: (Int, Boolean) -> Unit,
    onEvChange: (Int) -> Unit,
    onFocusPeakingToggle: () -> Unit,
    onVideoFpsChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xE60B0D13))
            .border(1.dp, Color(0x33334155))
    ) {
        // Quick Readout Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ISO Item
            QuickControlPill(
                label = "ISO",
                value = if (controls.isManualIso) "${controls.isoValue}" else "AUTO ${if (liveMetrics.currentIso > 0) liveMetrics.currentIso else ""}",
                isManual = controls.isManualIso,
                isSelected = activeDrawer == "ISO",
                onClick = { onDrawerSelect("ISO") }
            )

            // Shutter Item
            val shutterDisplay = formatShutter(if (controls.isManualShutter) controls.shutterSpeedSec else liveMetrics.currentShutterSpeedSec)
            QuickControlPill(
                label = "SEC",
                value = if (controls.isManualShutter) shutterDisplay else "AUTO $shutterDisplay",
                isManual = controls.isManualShutter,
                isSelected = activeDrawer == "SHUTTER",
                onClick = { onDrawerSelect("SHUTTER") }
            )

            // EV Item
            val evVal = (controls.exposureCompensationIndex * (spec?.evStep ?: 0.33f))
            QuickControlPill(
                label = "EV",
                value = String.format(Locale.US, "%+.1f", evVal),
                isManual = controls.exposureCompensationIndex != 0,
                isSelected = activeDrawer == "EV",
                onClick = { onDrawerSelect("EV") }
            )

            // Focus Item
            QuickControlPill(
                label = "FOCUS",
                value = if (controls.isManualFocus) String.format(Locale.US, "MF %.1fD", controls.focusDistanceDiopters) else "AF-C",
                isManual = controls.isManualFocus,
                isSelected = activeDrawer == "FOCUS",
                onClick = { onDrawerSelect("FOCUS") }
            )

            // WB Item
            QuickControlPill(
                label = "WB",
                value = if (controls.isManualWb) "${controls.wbTemperatureKelvin}K" else "AWB",
                isManual = controls.isManualWb,
                isSelected = activeDrawer == "WB",
                onClick = { onDrawerSelect("WB") }
            )

            // Fixed Aperture display (Galaxy A55 physical lens f/1.8 or f/2.2)
            val ap = spec?.availableApertures?.firstOrNull() ?: 1.8f
            ApertureFixedPill(aperture = ap, isFixed = spec?.isFixedAperture ?: true)
        }

        // Expanded Drawer
        AnimatedVisibility(
            visible = activeDrawer != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceElevated)
                    .padding(12.dp)
            ) {
                when (activeDrawer) {
                    "ISO" -> IsoAdjustmentDrawer(
                        spec = spec,
                        currentIso = controls.isoValue,
                        isManual = controls.isManualIso,
                        onIsoSelected = { iso, man -> onIsoChange(iso, man) },
                        onClose = { onDrawerSelect(null) }
                    )
                    "SHUTTER" -> ShutterAdjustmentDrawer(
                        spec = spec,
                        currentShutter = controls.shutterSpeedSec,
                        isManual = controls.isManualShutter,
                        onShutterSelected = { sec, man -> onShutterChange(sec, man) },
                        onClose = { onDrawerSelect(null) }
                    )
                    "FOCUS" -> FocusAdjustmentDrawer(
                        spec = spec,
                        currentDiopters = controls.focusDistanceDiopters,
                        isManual = controls.isManualFocus,
                        peaking = controls.focusPeakingEnabled,
                        onFocusSelected = { d, man -> onFocusChange(d, man) },
                        onPeakingToggle = onFocusPeakingToggle,
                        onClose = { onDrawerSelect(null) }
                    )
                    "WB" -> WhiteBalanceAdjustmentDrawer(
                        spec = spec,
                        currentKelvin = controls.wbTemperatureKelvin,
                        isManual = controls.isManualWb,
                        onWbSelected = { k, man -> onWbChange(k, man) },
                        onClose = { onDrawerSelect(null) }
                    )
                    "EV" -> EvAdjustmentDrawer(
                        spec = spec,
                        currentIndex = controls.exposureCompensationIndex,
                        onEvChange = onEvChange,
                        onClose = { onDrawerSelect(null) }
                    )
                }
            }
        }
    }
}

@Composable
fun QuickControlPill(
    label: String,
    value: String,
    isManual: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) AccentAmber else if (isManual) DarkSurfaceElevated else Color(0x331F2430))
            .border(1.dp, if (isSelected) AccentAmber else if (isManual) AccentAmberDim else DarkBorder, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.Black else SecondaryText,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) Color.Black else if (isManual) AccentAmber else TextLight,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun ApertureFixedPill(
    aperture: Float,
    isFixed: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x331F2430))
            .border(1.dp, DarkBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "IRIS",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = SecondaryText,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "f/$aperture ${if (isFixed) "FIX" else ""}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = Color(0xFF94A3B8),
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun IsoAdjustmentDrawer(
    spec: CameraHardwareSpec?,
    currentIso: Int,
    isManual: Boolean,
    onIsoSelected: (Int, Boolean) -> Unit,
    onClose: () -> Unit
) {
    val steps = listOf(50, 100, 200, 400, 800, 1600, 3200, 6400)
    val minIso = spec?.isoRange?.lower ?: 50
    val maxIso = spec?.isoRange?.upper ?: 3200

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MANUAL SENSITIVITY (ISO)",
                color = AccentAmber,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = SecondaryText)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Preset Steps
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Auto Button
            PillButton(
                text = "AUTO",
                isActive = !isManual,
                onClick = { onIsoSelected(currentIso, false) }
            )

            steps.filter { it in minIso..maxIso }.forEach { iso ->
                PillButton(
                    text = "$iso",
                    isActive = isManual && currentIso == iso,
                    onClick = { onIsoSelected(iso, true) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Fine Adjustment Slider
        if (isManual) {
            Slider(
                value = currentIso.toFloat(),
                onValueChange = { onIsoSelected(it.roundToInt(), true) },
                valueRange = minIso.toFloat()..maxIso.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = AccentAmber,
                    activeTrackColor = AccentAmber,
                    inactiveTrackColor = DarkBorder
                )
            )
            Text(
                text = "Hardware ISO range: $minIso – $maxIso",
                color = SecondaryText,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ShutterAdjustmentDrawer(
    spec: CameraHardwareSpec?,
    currentShutter: Double,
    isManual: Boolean,
    onShutterSelected: (Double, Boolean) -> Unit,
    onClose: () -> Unit
) {
    val shutterPresets = listOf(
        1.0 / 8000.0, 1.0 / 4000.0, 1.0 / 2000.0, 1.0 / 1000.0,
        1.0 / 500.0, 1.0 / 250.0, 1.0 / 125.0, 1.0 / 60.0,
        1.0 / 30.0, 1.0 / 15.0, 1.0 / 8.0, 1.0 / 4.0,
        1.0 / 2.0, 1.0, 2.0, 4.0, 10.0, 15.0, 30.0, 60.0
    )

    val maxHardwareSec = spec?.maxExposureSec ?: 0.5
    val isComputational = currentShutter > maxHardwareSec

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SHUTTER SPEED (EXPOSURE)",
                    color = AccentAmber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                if (isManual && isComputational) {
                    Text(
                        text = "COMPUTATIONAL STACK (> ${spec?.maxExposureSecFormatted ?: "1s"} hardware limit)",
                        color = WarningYellow,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = SecondaryText)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Preset Steps
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PillButton(
                text = "AUTO",
                isActive = !isManual,
                onClick = { onShutterSelected(currentShutter, false) }
            )

            shutterPresets.forEach { sec ->
                val exceedsHw = sec > maxHardwareSec
                PillButton(
                    text = formatShutter(sec),
                    isActive = isManual && kotlin.math.abs(currentShutter - sec) < 0.0001,
                    isWarning = exceedsHw,
                    onClick = { onShutterSelected(sec, true) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isComputational) {
                    "Driver sensor maxes at ${spec?.maxExposureSecFormatted}. Extended ${formatShutter(currentShutter)} captures sequential frames and aligns."
                } else {
                    "Genuine hardware sensor exposure: ${spec?.minExposureSecFormatted ?: "1/8000s"} to ${spec?.maxExposureSecFormatted ?: "1/2s"}."
                },
                color = if (isComputational) WarningYellow else SecondaryText,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun FocusAdjustmentDrawer(
    spec: CameraHardwareSpec?,
    currentDiopters: Float,
    isManual: Boolean,
    peaking: Boolean,
    onFocusSelected: (Float, Boolean) -> Unit,
    onPeakingToggle: () -> Unit,
    onClose: () -> Unit
) {
    val maxDiopter = spec?.minFocusDistanceDiopters?.coerceAtLeast(10.0f) ?: 10.0f
    val supportsMf = spec?.supportsManualFocus ?: true

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FOCUS CONTROL (DIOPTERS)",
                color = AccentAmber,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = SecondaryText)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillButton(
                text = "AUTO FOCUS",
                isActive = !isManual,
                onClick = { onFocusSelected(0.0f, false) }
            )

            PillButton(
                text = "INFINITY (∞)",
                isActive = isManual && currentDiopters == 0.0f,
                enabled = supportsMf,
                onClick = { onFocusSelected(0.0f, true) }
            )

            PillButton(
                text = "MACRO (CLOSE)",
                isActive = isManual && currentDiopters >= maxDiopter - 0.5f,
                enabled = supportsMf,
                onClick = { onFocusSelected(maxDiopter, true) }
            )

            PillButton(
                text = if (peaking) "PEAKING ON" else "PEAKING",
                isActive = peaking,
                accentColor = FocusCyan,
                onClick = onPeakingToggle
            )
        }

        if (supportsMf && isManual) {
            Spacer(modifier = Modifier.height(12.dp))
            Slider(
                value = currentDiopters,
                onValueChange = { onFocusSelected(it, true) },
                valueRange = 0.0f..maxDiopter,
                colors = SliderDefaults.colors(
                    thumbColor = FocusCyan,
                    activeTrackColor = FocusCyan,
                    inactiveTrackColor = DarkBorder
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("∞ Infinity (0.0D)", color = SecondaryText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text(String.format(Locale.US, "%.1f Diopters", currentDiopters), color = FocusCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("Macro (${String.format(Locale.US, "%.1fD", maxDiopter)})", color = SecondaryText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        } else if (!supportsMf) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This camera module has a fixed focus lens (no VCM voice-coil motor).",
                color = WarningYellow,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun WhiteBalanceAdjustmentDrawer(
    spec: CameraHardwareSpec?,
    currentKelvin: Int,
    isManual: Boolean,
    onWbSelected: (Int, Boolean) -> Unit,
    onClose: () -> Unit
) {
    val presets = listOf(
        Pair("TUNGSTEN", 3200),
        Pair("FLUOR", 4000),
        Pair("DAYLIGHT", 5500),
        Pair("CLOUDY", 6500),
        Pair("SHADE", 7500)
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "WHITE BALANCE (COLOR TEMP)",
                color = AccentAmber,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = SecondaryText)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PillButton(
                text = "AWB",
                isActive = !isManual,
                onClick = { onWbSelected(currentKelvin, false) }
            )

            presets.forEach { (name, kelvin) ->
                PillButton(
                    text = "$name (${kelvin}K)",
                    isActive = isManual && currentKelvin == kelvin,
                    onClick = { onWbSelected(kelvin, true) }
                )
            }
        }

        if (isManual) {
            Spacer(modifier = Modifier.height(12.dp))
            Slider(
                value = currentKelvin.toFloat(),
                onValueChange = { onWbSelected(it.roundToInt(), true) },
                valueRange = 2500f..8500f,
                colors = SliderDefaults.colors(
                    thumbColor = AccentAmber,
                    activeTrackColor = AccentAmber,
                    inactiveTrackColor = DarkBorder
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("2500K (Warm)", color = SecondaryText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text("${currentKelvin}K", color = AccentAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("8500K (Cool)", color = SecondaryText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun EvAdjustmentDrawer(
    spec: CameraHardwareSpec?,
    currentIndex: Int,
    onEvChange: (Int) -> Unit,
    onClose: () -> Unit
) {
    val evRange = spec?.evRange ?: android.util.Range(-6, 6)
    val step = spec?.evStep ?: 0.333f

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EXPOSURE COMPENSATION",
                color = AccentAmber,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = SecondaryText)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillButton(
                text = "RESET (0.0 EV)",
                isActive = currentIndex == 0,
                onClick = { onEvChange(0) }
            )
            PillButton(
                text = "-1.0 EV",
                isActive = false,
                onClick = { onEvChange((-1.0f / step).roundToInt()) }
            )
            PillButton(
                text = "+1.0 EV",
                isActive = false,
                onClick = { onEvChange((1.0f / step).roundToInt()) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Slider(
            value = currentIndex.toFloat(),
            onValueChange = { onEvChange(it.roundToInt()) },
            valueRange = evRange.lower.toFloat()..evRange.upper.toFloat(),
            steps = (evRange.upper - evRange.lower) - 1,
            colors = SliderDefaults.colors(
                thumbColor = AccentAmber,
                activeTrackColor = AccentAmber,
                inactiveTrackColor = DarkBorder
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(String.format(Locale.US, "%.1f EV", evRange.lower * step), color = SecondaryText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text(String.format(Locale.US, "%+.1f EV", currentIndex * step), color = AccentAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(String.format(Locale.US, "+%.1f EV", evRange.upper * step), color = SecondaryText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun PillButton(
    text: String,
    isActive: Boolean,
    isWarning: Boolean = false,
    enabled: Boolean = true,
    accentColor: Color = AccentAmber,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isActive) accentColor else if (isWarning) Color(0x33FFD600) else DarkSurface)
            .border(
                1.dp,
                if (isActive) accentColor else if (isWarning) WarningYellow else DarkBorder,
                RoundedCornerShape(6.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isActive) Color.Black else if (isWarning) WarningYellow else if (enabled) TextLight else Color(0x55FFFFFF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

fun formatShutter(seconds: Double): String {
    return if (seconds <= 0.0) {
        "0s"
    } else if (seconds < 1.0) {
        val fraction = (1.0 / seconds).roundToInt()
        "1/${fraction}s"
    } else {
        String.format(Locale.US, "%.1fs", seconds)
    }
}
