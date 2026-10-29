package com.example.procam.engine

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.media.MediaRecorder
import android.util.Range
import android.util.Size
import com.example.procam.model.CameraHardwareSpec
import java.util.Locale

class CameraCapabilityDetector(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun inspectAllCameras(): List<CameraHardwareSpec> {
        val specs = mutableListOf<CameraHardwareSpec>()
        try {
            val cameraIds = cameraManager.cameraIdList
            for (id in cameraIds) {
                val chars = cameraManager.getCameraCharacteristics(id)
                specs.add(inspectCamera(id, chars))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return specs
    }

    fun inspectCamera(cameraId: String, chars: CameraCharacteristics): CameraHardwareSpec {
        val facingInt = chars.get(CameraCharacteristics.LENS_FACING) ?: CameraMetadata.LENS_FACING_BACK
        val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val primaryFocal = focalLengths?.firstOrNull() ?: 0f

        val facingLabel = when (facingInt) {
            CameraMetadata.LENS_FACING_FRONT -> "Front ($cameraId)"
            CameraMetadata.LENS_FACING_BACK -> {
                when {
                    primaryFocal > 0f && primaryFocal < 2.5f -> "Rear Ultra-Wide ($cameraId)"
                    primaryFocal >= 2.5f && primaryFocal < 4.5f -> "Rear Wide ($cameraId)"
                    else -> "Rear Camera ($cameraId)"
                }
            }
            else -> "External ($cameraId)"
        }

        val hwLevelInt = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        val hwLevel = when (hwLevelInt) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN"
        }

        val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        val hasManualSensor = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
        val hasRawSupport = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)

        val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exposureTimeRangeNs = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)

        var minExpFormatted = "1/8000s"
        var maxExpFormatted = "1/2s"
        var maxExpSec = 0.5

        exposureTimeRangeNs?.let {
            val minSec = it.lower / 1_000_000_000.0
            val maxSec = it.upper / 1_000_000_000.0
            maxExpSec = maxSec

            minExpFormatted = if (minSec < 1.0) {
                "1/${(1.0 / minSec).toInt()}s"
            } else {
                String.format(Locale.US, "%.1fs", minSec)
            }

            maxExpFormatted = if (maxSec < 1.0) {
                "1/${(1.0 / maxSec).toInt()}s"
            } else {
                String.format(Locale.US, "%.1fs", maxSec)
            }
        }

        val evRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val evStepRational = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
        val evStep = evStepRational?.toFloat() ?: 0.333f

        val aperturesArray = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES) ?: floatArrayOf(1.8f)
        val availableApertures = aperturesArray.toList()
        val isFixedAperture = availableApertures.size <= 1

        val minFocusDist = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val supportsManualFocus = minFocusDist > 0f

        val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.map {
            when (it) {
                CameraMetadata.CONTROL_AF_MODE_AUTO -> "AUTO"
                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE -> "CONTINUOUS_PIC"
                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO -> "CONTINUOUS_VIDEO"
                CameraMetadata.CONTROL_AF_MODE_MACRO -> "MACRO"
                CameraMetadata.CONTROL_AF_MODE_OFF -> "MANUAL (OFF)"
                CameraMetadata.CONTROL_AF_MODE_EDOF -> "EDOF"
                else -> "AF_$it"
            }
        } ?: emptyList()

        val awbModes = chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.map {
            when (it) {
                CameraMetadata.CONTROL_AWB_MODE_AUTO -> "AUTO"
                CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT -> "DAYLIGHT"
                CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "CLOUDY"
                CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT -> "TUNGSTEN"
                CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT -> "FLUORESCENT"
                CameraMetadata.CONTROL_AWB_MODE_SHADE -> "SHADE"
                CameraMetadata.CONTROL_AWB_MODE_TWILIGHT -> "TWILIGHT"
                CameraMetadata.CONTROL_AWB_MODE_OFF -> "MANUAL (OFF)"
                else -> "AWB_$it"
            }
        } ?: emptyList()

        val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

        val oisModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: intArrayOf()
        val hasOis = oisModes.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)

        val eisModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: intArrayOf()
        val hasEis = eisModes.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON)

        val maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
        val activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)

        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG)?.sortedByDescending { it.width * it.height } ?: emptyList()
        val rawSizes = map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.sortedByDescending { it.width * it.height } ?: emptyList()
        val videoSizes = map?.getOutputSizes(MediaRecorder::class.java)?.sortedByDescending { it.width * it.height }
            ?: (map?.getOutputSizes(SurfaceTexture::class.java)?.sortedByDescending { it.width * it.height } ?: emptyList())

        val fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.toList() ?: emptyList()

        // Limitations and hardware rationale for transparency
        val limitations = mutableListOf<String>()
        if (isFixedAperture) {
            val fVal = availableApertures.firstOrNull() ?: 1.8f
            limitations.add("Physical lens aperture is fixed at f/$fVal. Variable aperture is not physically present on this sensor.")
        }
        if (!hasManualSensor) {
            limitations.add("Hardware Level does not expose MANUAL_SENSOR capability. Direct manual shutter/ISO may be limited by ISP firmware.")
        }
        if (!hasRawSupport) {
            limitations.add("RAW_SENSOR stream is not exposed on this camera module by the device driver.")
        } else {
            limitations.add("DNG RAW captures available using direct 16-bit Bayer sensor stream.")
        }
        if (maxExpSec < 60.0) {
            limitations.add("Single-frame native sensor exposure maxes at $maxExpFormatted. Exposures beyond this threshold utilize computational long exposure (frame stacking) to prevent driver capture timeouts.")
        }
        if (!supportsManualFocus) {
            limitations.add("Lens lacks a voice-coil motor (VCM) for manual focus distance control (fixed focal length).")
        }

        return CameraHardwareSpec(
            cameraId = cameraId,
            facing = facingLabel,
            hardwareLevel = hwLevel,
            hasManualSensor = hasManualSensor,
            hasRawSupport = hasRawSupport,
            isoRange = isoRange,
            exposureTimeRangeNs = exposureTimeRangeNs,
            minExposureSecFormatted = minExpFormatted,
            maxExposureSecFormatted = maxExpFormatted,
            maxExposureSec = maxExpSec,
            evRange = evRange,
            evStep = evStep,
            availableApertures = availableApertures,
            isFixedAperture = isFixedAperture,
            minFocusDistanceDiopters = minFocusDist,
            supportsManualFocus = supportsManualFocus,
            availableAfModes = afModes,
            availableAwbModes = awbModes,
            hasFlash = flashAvailable,
            hasOis = hasOis,
            hasEis = hasEis,
            maxZoom = maxZoom,
            sensorActiveArraySize = activeArray,
            supportedJpegResolutions = jpegSizes,
            supportedRawResolutions = rawSizes,
            supportedVideoResolutions = videoSizes,
            supportedFpsRanges = fpsRanges,
            limitationsSummary = limitations
        )
    }
}
