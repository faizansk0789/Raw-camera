package com.example.procam.model

import android.graphics.Rect
import android.util.Range
import android.util.Size

enum class CameraMode(val label: String) {
    PHOTO("PHOTO"),
    VIDEO("VIDEO"),
    AUTO("AUTO"),
    TOOLS("TOOLS"),
    GALLERY("GALLERY")
}

enum class PhotoFormat(val label: String, val extension: String) {
    JPEG("JPEG", "jpg"),
    RAW_DNG("RAW (DNG)", "dng"),
    RAW_PLUS_JPEG("RAW + JPEG", "dng+jpg")
}

enum class GridType(val label: String) {
    NONE("Off"),
    RULE_OF_THIRDS("3x3"),
    GOLDEN_RATIO("Golden"),
    CINEMATIC_16_9("16:9"),
    CINEMATIC_2_35("2.35:1"),
    CINEMATIC_2_39("2.39:1")
}

data class CameraHardwareSpec(
    val cameraId: String,
    val facing: String, // "Rear (Wide)", "Rear (Ultrawide)", "Rear (Macro)", "Front"
    val hardwareLevel: String, // "FULL", "LIMITED", "LEVEL_3", "LEGACY"
    val hasManualSensor: Boolean,
    val hasRawSupport: Boolean,
    val isoRange: Range<Int>?,
    val exposureTimeRangeNs: Range<Long>?,
    val minExposureSecFormatted: String,
    val maxExposureSecFormatted: String,
    val maxExposureSec: Double,
    val evRange: Range<Int>?,
    val evStep: Float,
    val availableApertures: List<Float>,
    val isFixedAperture: Boolean,
    val minFocusDistanceDiopters: Float,
    val supportsManualFocus: Boolean,
    val availableAfModes: List<String>,
    val availableAwbModes: List<String>,
    val hasFlash: Boolean,
    val hasOis: Boolean,
    val hasEis: Boolean,
    val maxZoom: Float,
    val sensorActiveArraySize: Rect?,
    val supportedJpegResolutions: List<Size>,
    val supportedRawResolutions: List<Size>,
    val supportedVideoResolutions: List<Size>,
    val supportedFpsRanges: List<Range<Int>>,
    val limitationsSummary: List<String>
)

data class ManualControlState(
    val isManualIso: Boolean = false,
    val isoValue: Int = 100,
    val isManualShutter: Boolean = false,
    val shutterSpeedSec: Double = 1.0 / 60.0, // in seconds, e.g. 0.0166 = 1/60s
    val isLongExposureMode: Boolean = false, // When > max native hardware exposure
    val computationalLongExposureDurationSec: Int = 10,
    val exposureCompensationIndex: Int = 0, // In EV steps
    val isManualFocus: Boolean = false,
    val focusDistanceDiopters: Float = 0.0f, // 0.0 = infinity, larger = closer
    val focusPeakingEnabled: Boolean = false,
    val isManualWb: Boolean = false,
    val wbTemperatureKelvin: Int = 5500,
    val wbPresetMode: Int = 1, // 1 = AUTO in CameraMetadata.CONTROL_AWB_MODE_AUTO
    val photoFormat: PhotoFormat = PhotoFormat.JPEG,
    val flashMode: Int = 0, // 0 = OFF, 1 = SINGLE/TORCH, 2 = AUTO
    val zoomLevel: Float = 1.0f,
    val gridType: GridType = GridType.RULE_OF_THIRDS,
    val showHistogram: Boolean = true,
    val showLevelHorizon: Boolean = true,
    val zebraWarningEnabled: Boolean = false,
    val hapticFeedback: Boolean = true
)

data class VideoSettings(
    val resolution: Size = Size(1920, 1080),
    val fps: Int = 30,
    val bitrateMbps: Int = 28, // High quality 1080p/4K
    val codec: String = "H.264", // H.264 or HEVC
    val audioEnabled: Boolean = true,
    val audioSampleRate: Int = 48000,
    val audioBitrateKbps: Int = 192,
    val videoStabilization: Boolean = true
)

data class LiveMetrics(
    val currentIso: Int = 0,
    val currentShutterSpeedSec: Double = 0.0,
    val currentAperture: Float = 1.8f,
    val currentFocusDistance: Float = 0.0f,
    val currentWbKelvin: Int = 5000,
    val rollDegrees: Float = 0f,
    val pitchDegrees: Float = 0f,
    val audioLevelPercent: Float = 0f,
    val freeStorageBytes: Long = 0L,
    val thermalState: String = "Normal",
    val isRecording: Boolean = false,
    val recordingDurationSec: Long = 0L,
    val isCapturingLongExposure: Boolean = false,
    val longExposureProgressPercent: Float = 0f,
    val longExposureRemainingSec: Int = 0
)

data class MediaItem(
    val id: Long,
    val uriString: String,
    val displayName: String,
    val dateAddedSec: Long,
    val isVideo: Boolean,
    val isRaw: Boolean,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val iso: String? = null,
    val shutterSpeed: String? = null,
    val aperture: String? = null,
    val focalLength: String? = null,
    val cameraModel: String? = null
)

data class ToolPreset(
    val name: String,
    val description: String,
    val iso: Int,
    val shutterSpeedSec: Double,
    val focusDiopters: Float?,
    val wbKelvin: Int?,
    val format: PhotoFormat
)
