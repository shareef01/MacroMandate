package com.sharek.macromandate.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Downsample sizing for the analysis and watermark paths.
 *
 * Only [ImageForensics.calculateInSampleSize] is exercised here — it is pure
 * arithmetic. The decode itself needs `BitmapFactory` and a `ContentResolver`,
 * so EXIF rotation is covered by the instrumented pass, not by these.
 */
class ImageForensicsTest {

    private fun decodedEdges(width: Int, height: Int, maxDimension: Int): Pair<Int, Int> {
        val sample = ImageForensics.calculateInSampleSize(width, height, maxDimension)
        return width / sample to height / sample
    }

    @Test
    fun imagesAlreadySmallEnoughAreNotDownsampled() {
        assertEquals(1, ImageForensics.calculateInSampleSize(800, 600, maxDimension = 1600))
        assertEquals(1, ImageForensics.calculateInSampleSize(1600, 1200, maxDimension = 1600))
    }

    @Test
    fun sampleSizeIsAlwaysAPowerOfTwo() {
        // BitmapFactory rounds anything else down to the nearest power of two, so
        // returning e.g. 3 would silently decode at 2 and use twice the memory.
        val sizes = listOf(
            4000 to 3000, 8000 to 6000, 12000 to 9000, 5312 to 2988, 48000 to 36000
        ).map { (w, h) -> ImageForensics.calculateInSampleSize(w, h, 1600) }

        sizes.forEach { size ->
            assertTrue("$size is not a power of two", size > 0 && (size and (size - 1)) == 0)
        }
    }

    @Test
    fun aTwelveMegapixelFrameIsBroughtWithinTwiceTheBudget() {
        // The AOSP algorithm this follows halves until *half* the dimension fits,
        // so the decoded edge can be up to ~2x maxDimension. That is the intended
        // behaviour — it trades a little memory for never overshooting downward
        // and losing detail the model needs.
        val (w, h) = decodedEdges(4000, 3000, maxDimension = 1600)
        assertTrue("decoded $w x $h is larger than the tolerated bound", maxOf(w, h) <= 3200)
        assertTrue("decoded $w x $h lost too much detail", maxOf(w, h) >= 1600)
    }

    @Test
    fun a50MegapixelFrameIsStillBounded() {
        // A modern flagship sensor. The point of sampling is that this never
        // materializes as a ~200MB ARGB_8888 bitmap.
        val (w, h) = decodedEdges(8160, 6120, maxDimension = 1600)
        assertTrue("decoded $w x $h is unbounded", maxOf(w, h) <= 3200)
    }

    @Test
    fun aspectRatioIsPreserved() {
        val sample = ImageForensics.calculateInSampleSize(4000, 3000, 1600)
        assertEquals(4000 / sample, 3000 / sample * 4 / 3)
    }

    @Test
    fun degenerateDimensionsDoNotLoopForever() {
        // Bounds decoding returns 0 x 0 for an unreadable stream; the caller must
        // still get a usable sample size rather than hang.
        assertEquals(1, ImageForensics.calculateInSampleSize(0, 0, 1600))
        assertEquals(1, ImageForensics.calculateInSampleSize(-1, -1, 1600))
    }

    @Test
    fun extremePanoramasAreSampledOnTheirLongEdge() {
        // A stitched panorama is short but very wide; sampling must respond to the
        // long edge or the decode blows the heap on width alone.
        val (w, _) = decodedEdges(30000, 800, maxDimension = 1600)
        assertTrue("panorama decoded at $w px wide", w <= 3200)
    }
}
