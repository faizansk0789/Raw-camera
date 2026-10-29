package com.example.procam.ui

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.procam.model.CameraMode
import com.example.procam.model.GridType
import com.example.procam.model.PhotoFormat
import com.example.procam.ui.components.ElectronicLevelHorizon
import com.example.procam.ui.components.LiveHistogram
import com.example.procam.ui.components.ManualControlsBar
import com.example.procam.ui.components.RecordingBadge
import com.example.procam.ui.components.TapFocusIndicator
import com.example.procam.ui.components.VideoControlsBar
import com.example.procam.ui.components.ViewfinderGrid
import com.example.procam.viewmodel.CameraViewModel
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.FocusCyan
import com.example.ui.theme.RecordRed
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.TextLight
import com.example.ui.theme.WarningYellow

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentMode by viewModel.currentMode.collectAsState()
    val manualControls by viewModel.manualControls.collectAsState()
    val videoSettings by viewModel.videoSettings.collectAsState()
    val liveMetrics by viewModel.cameraEngine.liveMetrics.collectAsState()
    val activeSpec by viewModel.cameraEngine.activeSpec.collectAsState()
    val cameraSpecs by viewModel.cameraSpecs.collectAsState()
    val selectedCameraId by viewModel.selectedCameraId.collectAsState()
    val activeDrawer by viewModel.activeDrawer.collectAsState()
    val galleryItems by viewModel.galleryItems.collectAsState()

    var tapFocusPoint by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // 1. Fullscreen Camera2 TextureView Viewfinder
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                            val surf = android.view.Surface(surface)
                            viewModel.setPreviewSurface(surf, width, height)
                            viewModel.cameraEngine.openCamera(selectedCameraId, currentMode, manualControls, videoSettings)
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                            val surf = android.view.Surface(surface)
                            viewModel.setPreviewSurface(surf, width, height)
                        }

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            viewModel.setPreviewSurface(null, 0, 0)
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        tapFocusPoint = offset
                        val normX = offset.x / size.width.toFloat()
                        val normY = offset.y / size.height.toFloat()
                        viewModel.tapToFocus(normX, normY)
                    }
                }
        )

        // 2. Viewfinder Overlays
        ViewfinderGrid(gridType = manualControls.gridType)

        if (manualControls.showLevelHorizon) {
            ElectronicLevelHorizon(
                rollDegrees = liveMetrics.rollDegrees,
                pitchDegrees = liveMetrics.pitchDegrees,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        TapFocusIndicator(focusPoint = tapFocusPoint)

        if (manualControls.showHistogram) {
            LiveHistogram(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 58.dp, end = 12.dp)
            )
        }

        // Long Exposure Progress Overlay
        if (liveMetrics.isCapturingLongExposure) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        progress = { liveMetrics.longExposureProgressPercent },
                        modifier = Modifier.size(72.dp),
                        color = AccentAmber,
                        strokeWidth = 6.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "EXTENDED SENSOR EXPOSURE",
                        color = AccentAmber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Remaining: ${liveMetrics.longExposureRemainingSec}s",
                        color = TextLight,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Keep camera steady",
                        color = SecondaryText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Recording Badge
        if (liveMetrics.isRecording) {
            RecordingBadge(
                durationSec = liveMetrics.recordingDurationSec,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 58.dp)
            )
        }

        // 3. Top Action Bar
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Color(0x880B0D13))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lens Switcher (e.g. Ultrawide, Main Wide, Front)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                cameraSpecs.forEach { spec ->
                    val isSelected = spec.cameraId == selectedCameraId
                    val shortName = when {
                        spec.facing.contains("Front", ignoreCase = true) -> "FRONT"
                        spec.facing.contains("Wide", ignoreCase = true) -> "1x WIDE"
                        spec.facing.contains("Ultra", ignoreCase = true) -> "0.5x UW"
                        else -> "CAM ${spec.cameraId}"
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) AccentAmber else DarkSurface)
                            .border(1.dp, if (isSelected) AccentAmber else DarkBorder, RoundedCornerShape(4.dp))
                            .clickable { viewModel.switchCamera(spec.cameraId) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = shortName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else TextLight,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Right Action Controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Flash Toggle
                IconButton(
                    onClick = { viewModel.toggleFlash() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (manualControls.flashMode > 0) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flash",
                        tint = if (manualControls.flashMode > 0) AccentAmber else TextLight,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Grid Toggle
                IconButton(
                    onClick = {
                        val nextGrid = when (manualControls.gridType) {
                            GridType.NONE -> GridType.RULE_OF_THIRDS
                            GridType.RULE_OF_THIRDS -> GridType.GOLDEN_RATIO
                            GridType.GOLDEN_RATIO -> GridType.CINEMATIC_2_35
                            GridType.CINEMATIC_2_35 -> GridType.NONE
                            else -> GridType.NONE
                        }
                        viewModel.setGrid(nextGrid)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.GridOn,
                        contentDescription = "Grid",
                        tint = if (manualControls.gridType != GridType.NONE) AccentAmber else SecondaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Photo Format Pill (JPEG / RAW DNG / RAW+JPEG)
                if (currentMode != CameraMode.VIDEO) {
                    val formatLabel = when (manualControls.photoFormat) {
                        PhotoFormat.JPEG -> "JPEG"
                        PhotoFormat.RAW_DNG -> "RAW"
                        PhotoFormat.RAW_PLUS_JPEG -> "RAW+JPG"
                    }
                    val rawSupported = activeSpec?.hasRawSupport ?: false
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (manualControls.photoFormat != PhotoFormat.JPEG) AccentAmber else DarkSurface)
                            .border(1.dp, if (manualControls.photoFormat != PhotoFormat.JPEG) AccentAmber else DarkBorder, RoundedCornerShape(4.dp))
                            .clickable {
                                if (rawSupported) {
                                    val nextFormat = when (manualControls.photoFormat) {
                                        PhotoFormat.JPEG -> PhotoFormat.RAW_DNG
                                        PhotoFormat.RAW_DNG -> PhotoFormat.RAW_PLUS_JPEG
                                        PhotoFormat.RAW_PLUS_JPEG -> PhotoFormat.JPEG
                                    }
                                    viewModel.setPhotoFormat(nextFormat)
                                } else {
                                    viewModel.setPhotoFormat(PhotoFormat.JPEG)
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (rawSupported) formatLabel else "JPEG ONLY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (manualControls.photoFormat != PhotoFormat.JPEG) Color.Black else TextLight,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Settings & Diagnostics Button
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextLight, modifier = Modifier.size(20.dp))
                }
            }
        }

        // 4. Lower Section: Manual Controls + Shutter + Modes
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Video Controls if in Video mode
            if (currentMode == CameraMode.VIDEO) {
                VideoControlsBar(
                    spec = activeSpec,
                    videoSettings = videoSettings,
                    liveMetrics = liveMetrics,
                    onResolutionChange = { viewModel.setVideoResolution(it) },
                    onFpsChange = { viewModel.setVideoFps(it) },
                    onBitrateChange = { viewModel.setVideoBitrate(it) }
                )
            }

            // Manual Controls Bar (active in PHOTO, VIDEO, AUTO)
            ManualControlsBar(
                spec = activeSpec,
                controls = manualControls,
                liveMetrics = liveMetrics,
                activeDrawer = activeDrawer,
                videoSettings = videoSettings,
                isVideoMode = currentMode == CameraMode.VIDEO,
                onDrawerSelect = { viewModel.setActiveDrawer(it) },
                onIsoChange = { iso, man -> viewModel.setIso(iso, man) },
                onShutterChange = { sec, man -> viewModel.setShutterSpeed(sec, man) },
                onFocusChange = { d, man -> viewModel.setManualFocus(d, man) },
                onWbChange = { k, man -> viewModel.setWhiteBalance(k, man) },
                onEvChange = { ev -> viewModel.setExposureCompensation(ev) },
                onFocusPeakingToggle = { viewModel.toggleFocusPeaking() },
                onVideoFpsChange = { viewModel.setVideoFps(it) }
            )

            // Zoom Selector Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xE60B0D13))
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                listOf(1.0f, 2.0f, 5.0f, 10.0f).forEach { zoom ->
                    val isSelected = manualControls.zoomLevel == zoom
                    Text(
                        text = "${zoom.toInt()}x",
                        color = if (isSelected) AccentAmber else SecondaryText,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable { viewModel.setZoom(zoom) }
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
            }

            // Main Trigger Area (Gallery Thumbnail, Big Shutter Button, Quick Flip)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xF007080B))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Thumbnail Button
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceElevated)
                        .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                        .clickable { viewModel.setMode(CameraMode.GALLERY) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = "Gallery",
                        tint = AccentAmber,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Shutter / Record Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(3.5.dp, Color.White, CircleShape)
                        .padding(5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentMode == CameraMode.VIDEO) {
                        // Video Record Button (Red circle or square when recording)
                        Box(
                            modifier = Modifier
                                .size(if (liveMetrics.isRecording) 32.dp else 60.dp)
                                .clip(if (liveMetrics.isRecording) RoundedCornerShape(6.dp) else CircleShape)
                                .background(RecordRed)
                                .clickable { viewModel.toggleVideoRecording() }
                                .testTag("record_button")
                        )
                    } else {
                        // Photo Shutter Button (Dual-layer metallic look)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable { viewModel.capturePhoto() }
                                .testTag("shutter_button")
                        )
                    }
                }

                // Camera Switch / Flip Button
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceElevated)
                        .border(1.dp, DarkBorder, CircleShape)
                        .clickable {
                            val nextCam = if (selectedCameraId == "0") "1" else "0"
                            viewModel.switchCamera(nextCam)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = TextLight,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Mode Selector Strip: PHOTO, VIDEO, AUTO, TOOLS, GALLERY
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF050608))
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CameraMode.values().forEach { mode ->
                    val isSelected = currentMode == mode
                    Text(
                        text = mode.label,
                        color = if (isSelected) AccentAmber else SecondaryText,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable { viewModel.setMode(mode) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("mode_${mode.name.lowercase()}")
                    )
                }
            }
        }
    }
}
