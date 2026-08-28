package com.sharek.macromandate.util

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ImageForensics {

    private const val TAG = "ImageForensics"

    /**
     * Computes a power-of-two [BitmapFactory.Options.inSampleSize] that keeps the
     * decoded bitmap within [maxDimension] on its longest edge. Shared by the
     * watermarking and the base64-analysis paths so no full-resolution frame is
     * ever materialized (avoids OutOfMemoryError on modern camera sensors).
     */
    fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        if (height > maxDimension || width > maxDimension) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / sampleSize >= maxDimension || halfWidth / sampleSize >= maxDimension) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }

    /**
     * Decodes [uri] downsampled to [maxDimension] and **rotated upright**.
     *
     * Phone cameras usually record the frame in the sensor's native orientation
     * and describe the correction in an EXIF tag rather than rotating the pixels.
     * Both image paths previously ignored that tag, so a photo taken in portrait
     * was handed to the vision model on its side. Coil applies EXIF when it
     * renders, so this was invisible in the app and showed up only as worse
     * estimates — the failure mode that is hardest to notice and most expensive
     * to have.
     *
     * Returns null if the image cannot be read or decoded.
     */
    fun decodeUpright(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: return null

        val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            })
        } ?: return null

        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            // A missing or malformed EXIF block is not a reason to lose the photo.
            Log.w(TAG, "Could not read EXIF orientation", e)
            ExifInterface.ORIENTATION_NORMAL
        }

        return applyOrientation(decoded, orientation)
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f); matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { rotated -> if (rotated !== bitmap) bitmap.recycle() }
        } catch (e: OutOfMemoryError) {
            // Better an unrotated image than no image.
            Log.w(TAG, "Not enough memory to rotate; using the frame as decoded")
            bitmap
        }
    }

    fun watermarkImage(
        context: Context,
        uri: Uri,
        id: String,
        latitude: Double?,
        longitude: Double?,
        timestamp: Long
    ): Uri? {
        try {
            // Shared upright decode: the watermark must sit on the same
            // orientation the model will see, or the text lands sideways.
            val decoded = decodeUpright(context, uri, maxDimension = 1600) ?: return null

            val mutableBitmap = decoded.copy(Bitmap.Config.ARGB_8888, true)
            if (mutableBitmap !== decoded) {
                decoded.recycle()
            }

            val canvas = Canvas(mutableBitmap)

            val paint = Paint().apply {
                color = Color.CYAN
                textSize = (mutableBitmap.height / 30).toFloat()
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                setShadowLayer(2f, 1f, 1f, Color.BLACK)
            }

            // Was "... 'UTC'" formatted with the device's default zone, which
            // burned a false timezone claim into the image permanently. Use a
            // real offset so the watermark states what it actually shows.
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ssXXX", Locale.US)
            val timeText = "TS: ${dateFormat.format(Date(timestamp))}"
            val idText = "ID: ${id.take(8).uppercase()}"
            val geoText = if (latitude != null && longitude != null) {
                "GEO: ${"%.4f".format(latitude)}, ${"%.4f".format(longitude)}"
            } else {
                "GEO: SIGNAL JAMMED"
            }

            val x = 20f
            var y = mutableBitmap.height - 40f

            canvas.drawText(geoText, x, y, paint)
            y -= paint.textSize + 10f
            canvas.drawText(timeText, x, y, paint)
            y -= paint.textSize + 10f
            canvas.drawText(idText, x, y, paint)

            // Save back
            val file = File(context.cacheDir, "watermarked_${id.take(8)}.jpg")
            FileOutputStream(file).use { out ->
                mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            mutableBitmap.recycle()

            return Uri.fromFile(file)
        } catch (e: Exception) {
            Log.w(TAG, "Could not watermark evidence image", e)
            return null
        }
    }
}
