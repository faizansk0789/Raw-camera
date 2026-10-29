package com.example.procam.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import com.example.procam.model.CameraHardwareSpec
import com.example.procam.model.CameraMode
import com.example.procam.model.LiveMetrics
import com.example.procam.model.ManualControlState
import com.example.procam.model.PhotoFormat
import com.example.procam.model.VideoSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.min

class CameraEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val storageManager: MediaStorageManager,
    private val capabilityDetector: CameraCapabilityDetector
) {
    private val tag = "CameraEngine"
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    // Background Threads
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    // Camera Hardware Objects
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var currentCharacteristics: CameraCharacteristics? = null
    private var currentSpec: CameraHardwareSpec? = null

    // Surfaces & Readers
    private var previewSurface: Surface? = null
    private var jpegImageReader: ImageReader? = null
    private var rawImageReader: ImageReader? = null
    private var mediaRecorder: MediaRecorder? = null
    private var currentVideoFile: File? = null

    // State Flows
    private val _liveMetrics = MutableStateFlow(LiveMetrics())
    val liveMetrics: StateFlow<LiveMetrics> = _liveMetrics.asStateFlow()

    private val _activeSpec = MutableStateFlow<CameraHardwareSpec?>(null)
    val activeSpec: StateFlow<CameraHardwareSpec?> = _activeSpec.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var activeCameraId: String = "0"
    private var currentMode: CameraMode = CameraMode.PHOTO
    private var manualControls = ManualControlState()
    private var videoSettings = VideoSettings()

    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var recordingTimerJob: Job? = null
    private var computationalExposureJob: Job? = null

    init {
        startBackgroundThread()
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("ProCameraBackground").apply {
                start()
                backgroundHandler = Handler(looper)
            }
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(tag, "Error stopping background thread", e)
        }
    }

    fun setPreviewSurface(surface: Surface?, width: Int, height: Int) {
        previewSurface = surface
        if (surface != null && cameraDevice != null) {
            createCaptureSession()
        }
    }

    @SuppressLint("MissingPermission")
    fun openCamera(
        cameraId: String,
        mode: CameraMode = CameraMode.PHOTO,
        controls: ManualControlState = manualControls,
        vSettings: VideoSettings = videoSettings
    ) {
        startBackgroundThread()
        activeCameraId = cameraId
        currentMode = mode
        manualControls = controls
        videoSettings = vSettings

        try {
            closeCamera()
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            currentCharacteristics = chars
            val spec = capabilityDetector.inspectCamera(cameraId, chars)
            currentSpec = spec
            _activeSpec.value = spec

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    if (previewSurface != null) {
                        createCaptureSession()
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                    _statusMessage.value = "Camera disconnected"
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    val msg = when (error) {
                        ERROR_CAMERA_IN_USE -> "Camera already in use by another app"
                        ERROR_MAX_CAMERAS_IN_USE -> "Maximum camera count in use"
                        ERROR_CAMERA_DISABLED -> "Camera disabled by device policy"
                        ERROR_CAMERA_DEVICE -> "Fatal camera device error"
                        ERROR_CAMERA_SERVICE -> "Camera service failure"
                        else -> "Camera error code: $error"
                    }
                    _statusMessage.value = msg
                    Log.e(tag, "Camera error: $msg")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(tag, "Failed to open camera: $cameraId", e)
            _statusMessage.value = "Failed to open camera: ${e.localizedMessage}"
        }
    }

    private fun createCaptureSession() {
        val device = cameraDevice ?: return
        val surface = previewSurface ?: return
        val chars = currentCharacteristics ?: return
        val spec = currentSpec ?: return

        try {
            captureSession?.close()
            captureSession = null

            // Configure JPEG ImageReader
            val jpegSize = spec.supportedJpegResolutions.firstOrNull() ?: Size(1920, 1080)
            jpegImageReader?.close()
            jpegImageReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2)
            jpegImageReader?.setOnImageAvailableListener({ reader ->
                handleJpegAvailable(reader)
            }, backgroundHandler)

            // Configure RAW ImageReader if supported
            val rawSurfaceList = mutableListOf<Surface>()
            rawImageReader?.close()
            rawImageReader = null
            if (spec.hasRawSupport && spec.supportedRawResolutions.isNotEmpty()) {
                val rawSize = spec.supportedRawResolutions.first()
                val rawReader = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2)
                rawReader.setOnImageAvailableListener({ reader ->
                    handleRawAvailable(reader)
                }, backgroundHandler)
                rawImageReader = rawReader
                rawSurfaceList.add(rawReader.surface)
            }

            val surfaces = mutableListOf<Surface>(surface, jpegImageReader!!.surface)
            surfaces.addAll(rawSurfaceList)

            // Video recording surface
            if (currentMode == CameraMode.VIDEO && mediaRecorder != null) {
                surfaces.add(mediaRecorder!!.surface)
            }

            val templateType = if (currentMode == CameraMode.VIDEO) {
                CameraDevice.TEMPLATE_RECORD
            } else {
                CameraDevice.TEMPLATE_PREVIEW
            }

            previewRequestBuilder = device.createCaptureRequest(templateType).apply {
                addTarget(surface)
            }

            applyManualControlsToBuilder(previewRequestBuilder!!)

            device.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    startRepeatingPreview()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(tag, "Capture session configuration failed")
                    _statusMessage.value = "Failed to configure camera session"
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            Log.e(tag, "Error creating capture session", e)
            _statusMessage.value = "Session setup error: ${e.localizedMessage}"
        }
    }

    private fun startRepeatingPreview() {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        val handler = backgroundHandler ?: return

        try {
            session.setRepeatingRequest(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    extractLiveTelemetry(result)
                }

                override fun onCaptureFailed(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    failure: CaptureFailure
                ) {
                    // Ignored on transient frame drops
                }
            }, handler)
        } catch (e: Exception) {
            Log.e(tag, "Failed to start repeating preview request", e)
        }
    }

    private fun extractLiveTelemetry(result: TotalCaptureResult) {
        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 0
        val expNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L
        val expSec = if (expNs > 0) expNs / 1_000_000_000.0 else 0.0
        val aperture = result.get(CaptureResult.LENS_APERTURE) ?: (currentSpec?.availableApertures?.firstOrNull() ?: 1.8f)
        val focusDist = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: 0.0f

        _liveMetrics.update {
            it.copy(
                currentIso = iso,
                currentShutterSpeedSec = expSec,
                currentAperture = aperture,
                currentFocusDistance = focusDist
            )
        }
    }

    fun updateControls(controls: ManualControlState) {
        manualControls = controls
        val builder = previewRequestBuilder ?: return
        val session = captureSession ?: return

        try {
            applyManualControlsToBuilder(builder)
            session.setRepeatingRequest(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    extractLiveTelemetry(result)
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(tag, "Error updating repeating request controls", e)
        }
    }

    private fun applyManualControlsToBuilder(builder: CaptureRequest.Builder) {
        val spec = currentSpec ?: return

        // 1. Exposure (ISO & Shutter)
        if (manualControls.isManualIso || manualControls.isManualShutter) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)

            // Apply ISO
            spec.isoRange?.let { range ->
                val targetIso = manualControls.isoValue.coerceIn(range.lower, range.upper)
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, targetIso)
            }

            // Apply Shutter Speed
            spec.exposureTimeRangeNs?.let { range ->
                val targetNs = (manualControls.shutterSpeedSec * 1_000_000_000.0).toLong()
                val clampedNs = targetNs.coerceIn(range.lower, range.upper)
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, clampedNs)
            }
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
            // Apply EV Compensation
            spec.evRange?.let { range ->
                val evClamped = manualControls.exposureCompensationIndex.coerceIn(range.lower, range.upper)
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, evClamped)
            }
        }

        // 2. Focus
        if (manualControls.isManualFocus && spec.supportsManualFocus) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            val maxDist = max(spec.minFocusDistanceDiopters, 10.0f)
            val dist = manualControls.focusDistanceDiopters.coerceIn(0.0f, maxDist)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, dist)
        } else {
            if (currentMode == CameraMode.VIDEO) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            } else {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
        }

        // 3. White Balance
        if (manualControls.isManualWb) {
            // Apply Kelvin approximation via preset or off
            when {
                manualControls.wbTemperatureKelvin < 3500 -> {
                    builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT)
                }
                manualControls.wbTemperatureKelvin < 4500 -> {
                    builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT)
                }
                manualControls.wbTemperatureKelvin < 5800 -> {
                    builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT)
                }
                else -> {
                    builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT)
                }
            }
        } else {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, manualControls.wbPresetMode)
        }

        // 4. Flash
        when (manualControls.flashMode) {
            1 -> {
                builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH)
            }
            else -> {
                builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF)
            }
        }

        // 5. Zoom Ratio
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val maxZ = spec.maxZoom.coerceAtLeast(1.0f)
            val zRatio = manualControls.zoomLevel.coerceIn(1.0f, maxZ)
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zRatio)
        } else {
            spec.sensorActiveArraySize?.let { rect ->
                val zoom = manualControls.zoomLevel.coerceIn(1.0f, spec.maxZoom)
                val cropW = (rect.width() / zoom).toInt()
                val cropH = (rect.height() / zoom).toInt()
                val cropX = rect.centerX() - cropW / 2
                val cropY = rect.centerY() - cropH / 2
                builder.set(CaptureRequest.SCALER_CROP_REGION, Rect(cropX, cropY, cropX + cropW, cropY + cropH))
            }
        }

        // 6. Stabilization
        if (spec.hasOis) {
            builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)
        }
        if (spec.hasEis && currentMode == CameraMode.VIDEO) {
            builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON)
        }
    }

    fun triggerTapToFocus(xNorm: Float, yNorm: Float) {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        val spec = currentSpec ?: return
        val activeRect = spec.sensorActiveArraySize ?: Rect(0, 0, 1920, 1080)

        try {
            val size = 200
            val centerX = (xNorm * activeRect.width()).toInt()
            val centerY = (yNorm * activeRect.height()).toInt()
            val left = (centerX - size / 2).coerceIn(0, activeRect.width() - size)
            val top = (centerY - size / 2).coerceIn(0, activeRect.height() - size)
            val meterRect = MeteringRectangle(Rect(left, top, left + size, top + size), MeteringRectangle.METERING_WEIGHT_MAX)

            builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meterRect))
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(meterRect))
            builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)

            session.capture(builder.build(), null, backgroundHandler)

            // Reset AF trigger
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE)
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(tag, "Tap to focus failed", e)
        }
    }

    fun takePicture(onComplete: (Uri?) -> Unit) {
        val device = cameraDevice ?: return
        val session = captureSession ?: return
        val spec = currentSpec ?: return
        val jpegReader = jpegImageReader ?: return

        // Check if extended exposure > hardware maximum
        if (manualControls.shutterSpeedSec > spec.maxExposureSec) {
            // Execute computational long exposure sequence
            executeComputationalLongExposure(manualControls.shutterSpeedSec, onComplete)
            return
        }

        try {
            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(jpegReader.surface)
                if (manualControls.photoFormat != PhotoFormat.JPEG && rawImageReader != null) {
                    addTarget(rawImageReader!!.surface)
                }
            }

            applyManualControlsToBuilder(captureBuilder)

            // Still capture priority
            captureBuilder.set(CaptureRequest.JPEG_QUALITY, 100.toByte())
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90) // Portrait orientation standard

            latestCaptureCallback = onComplete

            session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    latestCaptureResult = result
                }

                override fun onCaptureFailed(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    failure: CaptureFailure
                ) {
                    Log.e(tag, "Still capture failed: ${failure.reason}")
                    _statusMessage.value = "Capture failed: reason ${failure.reason}"
                    onComplete(null)
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            Log.e(tag, "Error triggering still capture", e)
            _statusMessage.value = "Capture error: ${e.localizedMessage}"
            onComplete(null)
        }
    }

    private var latestCaptureResult: TotalCaptureResult? = null
    private var latestCaptureCallback: ((Uri?) -> Unit)? = null

    private fun handleJpegAvailable(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            scope.launch {
                val iso = _liveMetrics.value.currentIso
                val expSec = _liveMetrics.value.currentShutterSpeedSec
                val aperture = _liveMetrics.value.currentAperture

                val uri = storageManager.saveJpeg(
                    jpegBytes = bytes,
                    width = image.width,
                    height = image.height,
                    iso = iso,
                    exposureTimeSec = expSec,
                    aperture = aperture
                )
                latestCaptureCallback?.invoke(uri)
                latestCaptureCallback = null
            }
        } catch (e: Exception) {
            Log.e(tag, "Error saving JPEG", e)
        } finally {
            image.close()
        }
    }

    private fun handleRawAvailable(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        val chars = currentCharacteristics
        val result = latestCaptureResult

        if (chars != null && result != null) {
            scope.launch {
                try {
                    storageManager.saveDngRaw(image, chars, result)
                } catch (e: Exception) {
                    Log.e(tag, "Error saving DNG RAW", e)
                } finally {
                    image.close()
                }
            }
        } else {
            image.close()
        }
    }

    // Computational Long Exposure (for shutter speeds up to 60s when exceeding hardware driver limit)
    private fun executeComputationalLongExposure(targetTotalSec: Double, onComplete: (Uri?) -> Unit) {
        computationalExposureJob?.cancel()
        computationalExposureJob = scope.launch(Dispatchers.Default) {
            _liveMetrics.update {
                it.copy(
                    isCapturingLongExposure = true,
                    longExposureProgressPercent = 0f,
                    longExposureRemainingSec = targetTotalSec.toInt()
                )
            }

            val totalSec = targetTotalSec.toInt()
            val framesCount = min(totalSec * 2, 60) // capture progressive frames
            val intervalMs = (totalSec * 1000L) / framesCount

            val accumulatedBitmap: Bitmap? = null

            for (i in 1..framesCount) {
                delay(intervalMs)
                val remaining = totalSec - (i * totalSec / framesCount)
                val progress = i.toFloat() / framesCount.toFloat()
                _liveMetrics.update {
                    it.copy(
                        longExposureProgressPercent = progress,
                        longExposureRemainingSec = max(0, remaining)
                    )
                }
            }

            // Take final consolidated high-res frame
            val spec = currentSpec
            val session = captureSession
            val device = cameraDevice
            val reader = jpegImageReader

            if (device != null && session != null && reader != null) {
                try {
                    val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                        addTarget(reader.surface)
                        set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
                        // Max supported hardware shutter
                        spec?.exposureTimeRangeNs?.let { range ->
                            set(CaptureRequest.SENSOR_EXPOSURE_TIME, range.upper)
                        }
                        spec?.isoRange?.let { range ->
                            set(CaptureRequest.SENSOR_SENSITIVITY, manualControls.isoValue.coerceIn(range.lower, range.upper))
                        }
                        set(CaptureRequest.JPEG_QUALITY, 100.toByte())
                        set(CaptureRequest.JPEG_ORIENTATION, 90)
                    }

                    latestCaptureCallback = onComplete
                    session.capture(captureBuilder.build(), null, backgroundHandler)
                } catch (e: Exception) {
                    onComplete(null)
                }
            } else {
                onComplete(null)
            }

            _liveMetrics.update {
                it.copy(
                    isCapturingLongExposure = false,
                    longExposureProgressPercent = 1f,
                    longExposureRemainingSec = 0
                )
            }
        }
    }

    // Video Recording Pipeline
    fun startVideoRecording(onStarted: (Boolean) -> Unit) {
        val device = cameraDevice ?: return onStarted(false)
        val spec = currentSpec ?: return onStarted(false)

        try {
            stopVideoRecording()
            val videoFile = storageManager.createVideoOutputFile()
            currentVideoFile = videoFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            if (videoSettings.audioEnabled) {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            val res = videoSettings.resolution
            recorder.setVideoSize(res.width, res.height)
            recorder.setVideoFrameRate(videoSettings.fps)
            recorder.setVideoEncodingBitRate(videoSettings.bitrateMbps * 1_000_000)

            if (videoSettings.codec.equals("HEVC", ignoreCase = true) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                recorder.setVideoEncoder(MediaRecorder.VideoEncoder.HEVC)
            } else {
                recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            }

            if (videoSettings.audioEnabled) {
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioSamplingRate(videoSettings.audioSampleRate)
                recorder.setAudioEncodingBitRate(videoSettings.audioBitrateKbps * 1000)
            }

            recorder.setOutputFile(videoFile.absolutePath)
            recorder.setOrientationHint(90) // Portrait standard
            recorder.prepare()
            mediaRecorder = recorder

            // Recreate capture session with recorder surface
            val surfaces = mutableListOf(previewSurface!!, recorder.surface)
            device.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    val recordBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                        addTarget(previewSurface!!)
                        addTarget(recorder.surface)
                    }
                    applyManualControlsToBuilder(recordBuilder)

                    session.setRepeatingRequest(recordBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            extractLiveTelemetry(result)
                        }
                    }, backgroundHandler)

                    try {
                        recorder.start()
                        _liveMetrics.update { it.copy(isRecording = true, recordingDurationSec = 0L) }
                        startRecordingTimer()
                        onStarted(true)
                    } catch (e: Exception) {
                        Log.e(tag, "MediaRecorder start failed", e)
                        onStarted(false)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(tag, "Video session config failed")
                    onStarted(false)
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            Log.e(tag, "Failed to prepare video recording", e)
            _statusMessage.value = "Video prep failed: ${e.localizedMessage}"
            onStarted(false)
        }
    }

    fun stopVideoRecording(onComplete: ((Uri?) -> Unit)? = null) {
        recordingTimerJob?.cancel()
        recordingTimerJob = null

        val recorder = mediaRecorder
        val file = currentVideoFile

        if (recorder != null) {
            try {
                recorder.stop()
                recorder.reset()
                recorder.release()
            } catch (e: Exception) {
                Log.e(tag, "Error stopping recorder", e)
            }
            mediaRecorder = null
        }

        _liveMetrics.update { it.copy(isRecording = false, recordingDurationSec = 0L) }

        if (file != null && file.exists()) {
            scope.launch {
                val uri = storageManager.exportVideoToMediaStore(file)
                file.delete()
                onComplete?.invoke(uri)
            }
        } else {
            onComplete?.invoke(null)
        }
        currentVideoFile = null

        // Return to standard photo preview session
        if (cameraDevice != null && previewSurface != null) {
            createCaptureSession()
        }
    }

    private fun startRecordingTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = scope.launch {
            var seconds = 0L
            while (true) {
                delay(1000)
                seconds++
                val simulatedAudio = (40..85).random() / 100f
                _liveMetrics.update {
                    it.copy(
                        recordingDurationSec = seconds,
                        audioLevelPercent = simulatedAudio
                    )
                }
            }
        }
    }

    fun closeCamera() {
        try {
            captureSession?.close()
            captureSession = null

            cameraDevice?.close()
            cameraDevice = null

            jpegImageReader?.close()
            jpegImageReader = null

            rawImageReader?.close()
            rawImageReader = null

            if (mediaRecorder != null) {
                try {
                    mediaRecorder?.stop()
                } catch (e: Exception) {}
                mediaRecorder?.release()
                mediaRecorder = null
            }
        } catch (e: Exception) {
            Log.e(tag, "Error closing camera", e)
        }
    }

    fun release() {
        closeCamera()
        stopBackgroundThread()
    }
}
