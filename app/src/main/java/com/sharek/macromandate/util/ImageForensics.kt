package com.sharek.macromandate.util

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.Log
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
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / sampleSize >= maxDimension || halfWidth / sampleSize >= maxDimension) {
                sampleSize *= 2
            }
        }
        return sampleSize
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
            // Decode bounds first, then sample down so watermarking never holds
            // two full-resolution bitmaps in memory.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension = 1600)
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return null

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
