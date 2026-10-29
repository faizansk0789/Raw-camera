package com.example.procam.engine

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.media.Image
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.media.ExifInterface
import com.example.procam.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaStorageManager(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    suspend fun saveJpeg(
        jpegBytes: ByteArray,
        width: Int,
        height: Int,
        iso: Int? = null,
        exposureTimeSec: Double? = null,
        aperture: Float? = null
    ): Uri? = withContext(Dispatchers.IO) {
        val timeStamp = dateFormat.format(Date())
        val fileName = "PRO_CAM_${timeStamp}.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.WIDTH, width)
            put(MediaStore.Images.Media.HEIGHT, height)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/AperturePro")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { os ->
                    os.write(jpegBytes)
                    os.flush()
                }

                // Add EXIF tags
                try {
                    context.contentResolver.openFileDescriptor(it, "rw")?.use { pfd ->
                        val exif = ExifInterface(pfd.fileDescriptor)
                        iso?.let { isoVal -> exif.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, isoVal.toString()) }
                        exposureTimeSec?.let { expVal ->
                            val expValStr = if (expVal < 1.0) "1/${(1.0 / expVal).toInt()}" else expVal.toString()
                            exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, expValStr)
                        }
                        aperture?.let { apVal -> exif.setAttribute(ExifInterface.TAG_F_NUMBER, apVal.toString()) }
                        exif.setAttribute(ExifInterface.TAG_MAKE, "Samsung / Aperture Pro")
                        exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
                        exif.saveAttributes()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(it, values, null, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        uri
    }

    suspend fun saveBitmapAsJpeg(
        bitmap: Bitmap,
        suffix: String = "EXP"
    ): Uri? = withContext(Dispatchers.IO) {
        val timeStamp = dateFormat.format(Date())
        val fileName = "PRO_CAM_${timeStamp}_${suffix}.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/AperturePro")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { os ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 96, os)
                    os.flush()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(it, values, null, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        uri
    }

    suspend fun saveDngRaw(
        rawImage: Image,
        characteristics: CameraCharacteristics,
        captureResult: CaptureResult
    ): Uri? = withContext(Dispatchers.IO) {
        val timeStamp = dateFormat.format(Date())
        val fileName = "PRO_RAW_${timeStamp}.dng"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
            put(MediaStore.Images.Media.WIDTH, rawImage.width)
            put(MediaStore.Images.Media.HEIGHT, rawImage.height)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/AperturePro")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { os ->
                    val dngCreator = DngCreator(characteristics, captureResult)
                    dngCreator.writeImage(os, rawImage)
                    dngCreator.close()
                    os.flush()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(it, values, null, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        uri
    }

    fun createVideoOutputFile(): File {
        val timeStamp = dateFormat.format(Date())
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "AperturePro")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "PRO_REC_${timeStamp}.mp4")
    }

    suspend fun exportVideoToMediaStore(videoFile: File): Uri? = withContext(Dispatchers.IO) {
        if (!videoFile.exists()) return@withContext null

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/AperturePro")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { os ->
                    videoFile.inputStream().use { input ->
                        input.copyTo(os)
                    }
                    os.flush()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(it, values, null, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        uri
    }

    suspend fun loadGalleryMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()

        // Load Images
        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE
        )
        val imageSortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imageProjection,
            null,
            null,
            imageSortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            var count = 0
            while (cursor.moveToNext() && count < 60) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: ""
                val date = cursor.getLong(dateCol)
                val width = cursor.getInt(widthCol)
                val height = cursor.getInt(heightCol)
                val size = cursor.getLong(sizeCol)
                val mime = cursor.getString(mimeCol) ?: ""

                val isRaw = name.endsWith(".dng", ignoreCase = true) || mime.contains("dng")
                val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                // Read EXIF if possible
                var iso: String? = null
                var shutter: String? = null
                var aperture: String? = null
                var focalLength: String? = null

                try {
                    context.contentResolver.openInputStream(contentUri)?.use { input ->
                        val exif = ExifInterface(input)
                        iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                        shutter = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                        aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
                        focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
                    }
                } catch (e: Exception) {
                    // Ignore exif parsing failure
                }

                items.add(
                    MediaItem(
                        id = id,
                        uriString = contentUri.toString(),
                        displayName = name,
                        dateAddedSec = date,
                        isVideo = false,
                        isRaw = isRaw,
                        width = width,
                        height = height,
                        sizeBytes = size,
                        iso = iso,
                        shutterSpeed = shutter,
                        aperture = aperture,
                        focalLength = focalLength,
                        cameraModel = Build.MODEL
                    )
                )
                count++
            }
        }

        // Load Videos
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.SIZE
        )
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

            var count = 0
            while (cursor.moveToNext() && count < 30) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: ""
                val date = cursor.getLong(dateCol)
                val width = cursor.getInt(widthCol)
                val height = cursor.getInt(heightCol)
                val size = cursor.getLong(sizeCol)
                val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                items.add(
                    MediaItem(
                        id = id,
                        uriString = contentUri.toString(),
                        displayName = name,
                        dateAddedSec = date,
                        isVideo = true,
                        isRaw = false,
                        width = width,
                        height = height,
                        sizeBytes = size,
                        cameraModel = Build.MODEL
                    )
                )
                count++
            }
        }

        items.sortedByDescending { it.dateAddedSec }
    }

    suspend fun deleteMedia(uriString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val rows = context.contentResolver.delete(uri, null, null)
            rows > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
