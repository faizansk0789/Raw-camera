package com.example.procam.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Size
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.procam.engine.CameraCapabilityDetector
import com.example.procam.engine.CameraEngine
import com.example.procam.engine.MediaStorageManager
import com.example.procam.model.CameraHardwareSpec
import com.example.procam.model.CameraMode
import com.example.procam.model.GridType
import com.example.procam.model.LiveMetrics
import com.example.procam.model.ManualControlState
import com.example.procam.model.MediaItem
import com.example.procam.model.PhotoFormat
import com.example.procam.model.ToolPreset
import com.example.procam.model.VideoSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

class CameraViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val context = application.applicationContext
    val storageManager = MediaStorageManager(context)
    val capabilityDetector = CameraCapabilityDetector(context)
    val cameraEngine = CameraEngine(context, viewModelScope, storageManager, capabilityDetector)

    // Camera Specs & Selection
    private val _cameraSpecs = MutableStateFlow<List<CameraHardwareSpec>>(emptyList())
    val cameraSpecs: StateFlow<List<CameraHardwareSpec>> = _cameraSpecs.asStateFlow()

    private val _selectedCameraId = MutableStateFlow("0")
    val selectedCameraId: StateFlow<String> = _selectedCameraId.asStateFlow()

    // Mode
    private val _currentMode = MutableStateFlow(CameraMode.PHOTO)
    val currentMode: StateFlow<CameraMode> = _currentMode.asStateFlow()

    // Manual Controls
    private val _manualControls = MutableStateFlow(ManualControlState())
    val manualControls: StateFlow<ManualControlState> = _manualControls.asStateFlow()

    // Video Settings
    private val _videoSettings = MutableStateFlow(VideoSettings())
    val videoSettings: StateFlow<VideoSettings> = _videoSettings.asStateFlow()

    // Gallery Items
    private val _galleryItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val galleryItems: StateFlow<List<MediaItem>> = _galleryItems.asStateFlow()

    // Active Drawer (which manual parameter slider is currently open: "ISO", "SHUTTER", "FOCUS", "WB", "EV", "RES", null)
    private val _activeDrawer = MutableStateFlow<String?>(null)
    val activeDrawer: StateFlow<String?> = _activeDrawer.asStateFlow()

    // Presets
    val presets = listOf(
        ToolPreset("Street Photography", "High shutter 1/500s to freeze action, ISO 400", 400, 1.0 / 500.0, null, 5500, PhotoFormat.JPEG),
        ToolPreset("Portrait Pro", "Soft natural Kelvin 5200K, shallow focus, RAW+JPEG", 100, 1.0 / 125.0, 1.8f, 5200, PhotoFormat.RAW_PLUS_JPEG),
        ToolPreset("Night Cityscape", "Low ISO 100 for clean blacks, 2.0s shutter, RAW DNG", 100, 2.0, 0.0f, 4200, PhotoFormat.RAW_DNG),
        ToolPreset("Silky Water / Trails", "6.0s extended exposure, ISO 50", 50, 6.0, 0.0f, 5500, PhotoFormat.RAW_DNG),
        ToolPreset("Star Trails", "15.0s sensor exposure, ISO 1600, Infinity focus", 1600, 15.0, 0.0f, 3800, PhotoFormat.RAW_DNG),
        ToolPreset("Cinematic 24FPS", "180° Shutter rule (1/48s), 24 FPS, 4K UHD", 200, 1.0 / 48.0, null, 5600, PhotoFormat.JPEG),
        ToolPreset("Cinematic 30FPS", "180° Shutter rule (1/60s), 30 FPS, 1080p FHD", 200, 1.0 / 60.0, null, 5600, PhotoFormat.JPEG)
    )

    // Sensor Manager for horizon level
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Vibrator for tactile haptics
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    init {
        loadCapabilities()
        startSensorMonitoring()
        updateStorageAndThermal()
        refreshGallery()
    }

    fun loadCapabilities() {
        viewModelScope.launch(Dispatchers.IO) {
            val specs = capabilityDetector.inspectAllCameras()
            _cameraSpecs.value = specs
            if (specs.isNotEmpty() && !specs.any { it.cameraId == _selectedCameraId.value }) {
                _selectedCameraId.value = specs.first().cameraId
            }
        }
    }

    fun setPreviewSurface(surface: Surface?, width: Int, height: Int) {
        cameraEngine.setPreviewSurface(surface, width, height)
    }

    fun switchCamera(cameraId: String) {
        if (_selectedCameraId.value == cameraId) return
        _selectedCameraId.value = cameraId
        triggerHaptic()
        cameraEngine.openCamera(cameraId, _currentMode.value, _manualControls.value, _videoSettings.value)
    }

    fun setMode(mode: CameraMode) {
        if (_currentMode.value == mode) return
        _currentMode.value = mode
        _activeDrawer.value = null
        triggerHaptic()

        if (mode == CameraMode.GALLERY) {
            refreshGallery()
            cameraEngine.closeCamera()
        } else {
            cameraEngine.openCamera(_selectedCameraId.value, mode, _manualControls.value, _videoSettings.value)
        }
    }

    fun setActiveDrawer(drawer: String?) {
        _activeDrawer.value = if (_activeDrawer.value == drawer) null else drawer
        triggerHaptic()
    }

    // Manual Parameter Updates
    fun setIso(iso: Int, isManual: Boolean = true) {
        _manualControls.update { it.copy(isoValue = iso, isManualIso = isManual) }
        cameraEngine.updateControls(_manualControls.value)
        triggerHaptic()
    }

    fun toggleAutoIso() {
        val current = _manualControls.value.isManualIso
        _manualControls.update { it.copy(isManualIso = !current) }
        cameraEngine.updateControls(_manualControls.value)
        triggerHaptic()
    }

    fun setShutterSpeed(seconds: Double, isManual: Boolean = true) {
        _manualControls.update { it.copy(shutterSpeedSec = seconds, isManualShutter = isManual) }
        cameraEngine.updateControls(_manualControls.value)
        triggerHaptic()
    }

    fun toggleAutoShutter() {
        val current = _manualControls.value.isManualShutter
        _manualControls.update { it.copy(isManualShutter = !current) }
        cameraEngine.updateControls(_manualControls.value)
        triggerHaptic()
    }

    fun setExposureCompensation(evIndex: Int) {
        _manualControls.update { it.copy(exposureCompensationIndex = evIndex) }
        cameraEngine.updateControls(_manualControls.value)
        triggerHaptic()
    }

    fun setManualFocus(diopters: Float, isManual: Boolean = true) {
        _manualControls.update { it.copy(focusDistanceDiopters = diopters, isManualFocus = isManual) }
        cameraEngine.updateControls(_manualControls.value)
    }

    fun toggleAutoFocus() {
        val current = _manualControls.value.isManualFocus
        _manualControls.update { it.copy(isManualFocus = !current) }
        cameraEngine.updateControls(_manualControls.value)
        triggerHaptic()
    }

    fun toggleFocusPeaking() {
        _manualControls.update { it.copy(focusPeakingEnabled = !it.focusPeakingEnabled) }
        triggerHaptic()
    }

    fun setWhiteBalance(kelvin: Int, isManual: Boolean = true) {
        _manualControls.update { it.copy(wbTemperatureKelvin = kelvin, isManualWb = isManual) }
        cameraEngine.updateControls(_manualControls.value)
        triggerHaptic()
    }

    fun setAwbPreset(presetMode: Int) {
        _manualControls.update { it.copy(wbPresetMode = presetMode, isManualWb = false) }
        cameraEngine.updateControls(_manualControls.value)
        triggerHaptic()
    }

    fun setPhotoFormat(format: PhotoFormat) {
        _manualControls.update { it.copy(photoFormat = format) }
        triggerHaptic()
    }

    fun setZoom(zoom: Float) {
        _manualControls.update { it.copy(zoomLevel = zoom) }
        cameraEngine.updateControls(_manualControls.value)
    }

    fun toggleFlash() {
        val next = (_manualControls.value.flashMode + 1) % 2 // toggle between OFF (0) and TORCH (1)
        _manualControls.update { it.copy(flashMode = next) }
        cameraEngine.updateControls(_manualControls.value)
        triggerHaptic()
    }

    fun setGrid(grid: GridType) {
        _manualControls.update { it.copy(gridType = grid) }
        triggerHaptic()
    }

    fun toggleHistogram() {
        _manualControls.update { it.copy(showHistogram = !it.showHistogram) }
        triggerHaptic()
    }

    fun toggleZebra() {
        _manualControls.update { it.copy(zebraWarningEnabled = !it.zebraWarningEnabled) }
        triggerHaptic()
    }

    fun toggleLevel() {
        _manualControls.update { it.copy(showLevelHorizon = !it.showLevelHorizon) }
        triggerHaptic()
    }

    // Video Parameters
    fun setVideoResolution(res: Size) {
        _videoSettings.update { it.copy(resolution = res) }
        triggerHaptic()
    }

    fun setVideoFps(fps: Int) {
        _videoSettings.update { it.copy(fps = fps) }
        // 180° cinematic shutter recommendation
        val recommendedShutter = 1.0 / (fps * 2.0)
        _manualControls.update { it.copy(shutterSpeedSec = recommendedShutter) }
        triggerHaptic()
    }

    fun setVideoBitrate(bitrateMbps: Int) {
        _videoSettings.update { it.copy(bitrateMbps = bitrateMbps) }
    }

    fun applyPreset(preset: ToolPreset) {
        _manualControls.update {
            it.copy(
                isoValue = preset.iso,
                isManualIso = true,
                shutterSpeedSec = preset.shutterSpeedSec,
                isManualShutter = true,
                focusDistanceDiopters = preset.focusDiopters ?: it.focusDistanceDiopters,
                isManualFocus = preset.focusDiopters != null,
                wbTemperatureKelvin = preset.wbKelvin ?: it.wbTemperatureKelvin,
                isManualWb = preset.wbKelvin != null,
                photoFormat = preset.format
            )
        }
        cameraEngine.updateControls(_manualControls.value)
        triggerHaptic()
    }

    // Capture Actions
    fun capturePhoto() {
        triggerHaptic()
        cameraEngine.takePicture { uri ->
            if (uri != null) {
                refreshGallery()
            }
        }
    }

    fun toggleVideoRecording() {
        triggerHaptic()
        if (cameraEngine.liveMetrics.value.isRecording) {
            cameraEngine.stopVideoRecording { uri ->
                if (uri != null) {
                    refreshGallery()
                }
            }
        } else {
            cameraEngine.startVideoRecording { started ->
                if (!started) {
                    triggerHaptic()
                }
            }
        }
    }

    fun tapToFocus(normX: Float, normY: Float) {
        triggerHaptic()
        cameraEngine.triggerTapToFocus(normX, normY)
    }

    fun refreshGallery() {
        viewModelScope.launch {
            val items = storageManager.loadGalleryMedia()
            _galleryItems.value = items
        }
    }

    fun deleteMedia(item: MediaItem) {
        viewModelScope.launch {
            val success = storageManager.deleteMedia(item.uriString)
            if (success) {
                refreshGallery()
            }
        }
    }

    fun triggerHaptic() {
        if (!_manualControls.value.hapticFeedback) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(20)
            }
        } catch (e: Exception) {}
    }

    private fun startSensorMonitoring() {
        gravitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val roll = (atan2(x.toDouble(), sqrt(y * y + z * z).toDouble()) * 180.0 / Math.PI).toFloat()
        val pitch = (atan2(y.toDouble(), sqrt(x * x + z * z).toDouble()) * 180.0 / Math.PI).toFloat()

        // Update live metrics roll/pitch (smooth update)
        // CameraEngine live metrics holds roll & pitch
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun updateStorageAndThermal() {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

            val thermalStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
                when (powerManager.currentThermalStatus) {
                    PowerManager.THERMAL_STATUS_NONE -> "Cool (Nominal)"
                    PowerManager.THERMAL_STATUS_LIGHT -> "Light Warmth"
                    PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Warmth"
                    PowerManager.THERMAL_STATUS_SEVERE -> "Severe Throttle"
                    PowerManager.THERMAL_STATUS_CRITICAL -> "Critical Overheat"
                    PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency Shutdown"
                    else -> "Normal"
                }
            } else {
                "Normal"
            }

            // Stored in LiveMetrics if needed
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager.unregisterListener(this)
        cameraEngine.release()
    }
}
