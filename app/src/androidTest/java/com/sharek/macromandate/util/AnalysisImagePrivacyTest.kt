package com.sharek.macromandate.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class AnalysisImagePrivacyTest {
    @Test
    fun aiRequest_doesNotPreserveSourceExifGps() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "source-with-gps.jpg")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
        }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        BitmapFactory.decodeFile(source.absolutePath).also {
            assertNotNull("Test fixture was not a decodable JPEG before EXIF mutation", it)
            it?.recycle()
        }
        ExifInterface(source).apply {
            setLatLong(52.5200, 13.4050)
            setAttribute(ExifInterface.TAG_MAKE, "TEST DEVICE")
            setAttribute(ExifInterface.TAG_MODEL, "TEST CAMERA")
            saveAttributes()
        }
        BitmapFactory.decodeFile(source.absolutePath).also {
            assertNotNull("EXIF mutation made the test fixture undecodable", it)
            it?.recycle()
        }

        try {
            val sourceBytes = source.readBytes()
            val decoded = ImageForensics.decodeUpright(context, Uri.fromFile(source), 1600)
            assertNotNull("Shared upright decoder rejected the fixture", decoded)
            ByteArrayOutputStream().use { probe ->
                assertTrue(
                    "Decoded software bitmap could not be JPEG-compressed",
                    decoded!!.compress(Bitmap.CompressFormat.JPEG, 80, probe)
                )
            }
            decoded?.recycle()
            val requestBytes = ImageForensics.encodeAnalysisJpeg(context, Uri.fromFile(source))
            assertNotNull(
                "Analysis encoding unexpectedly rejected a ${source.length()}-byte JPEG",
                requestBytes
            )
            requestBytes!!
            assertFalse(sourceBytes.contentEquals(requestBytes))
            assertTrue(requestBytes.isNotEmpty())
            val requestExif = ExifInterface(ByteArrayInputStream(requestBytes))
            assertNull(requestExif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            assertNull(requestExif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
            assertNull(requestExif.getAttribute(ExifInterface.TAG_MAKE))
            assertNull(requestExif.getAttribute(ExifInterface.TAG_MODEL))
        } finally {
            source.delete()
        }
    }
}
