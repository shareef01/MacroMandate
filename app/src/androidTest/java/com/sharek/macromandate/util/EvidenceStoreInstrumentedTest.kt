package com.sharek.macromandate.util

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EvidenceStoreInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun cleanUp() {
        EvidenceStore.deleteAll(context)
    }

    @Test
    fun evidencePersist_copyFails_removesPartialFile() {
        val id = "copy-failure"
        val result = EvidenceStore.persist(context, Uri.fromFile(File(context.cacheDir, "missing.jpg")), id)
        assertTrue(result is EvidencePersistResult.IoFailure)
        assertFalse(EvidenceStore.newFile(context, id).exists())
        assertFalse(EvidenceStore.directory(context).listFiles()?.any { it.name.contains(id) } == true)
    }

    @Test
    fun evidencePersist_oversizedInput_rejectedBeforeUnboundedWrite() {
        val source = File(context.cacheDir, "oversized-evidence.jpg")
        source.outputStream().use { output ->
            val block = ByteArray(8192)
            var remaining = EvidenceStore.MAX_EVIDENCE_BYTES + 1
            while (remaining > 0) {
                val count = minOf(block.size.toLong(), remaining).toInt()
                output.write(block, 0, count)
                remaining -= count
            }
        }
        try {
            val result = EvidenceStore.persist(context, Uri.fromFile(source), "oversized")
            assertTrue(result is EvidencePersistResult.TooLarge)
            assertFalse(EvidenceStore.newFile(context, "oversized").exists())
        } finally {
            source.delete()
        }
    }

    @Test
    fun restore_pathTraversal_rejected() {
        val root = EvidenceStore.directory(context)
        val traversal = Uri.parse("file://${root.path}/../databases/private.jpg")
        assertFalse(EvidenceStore.isStored(context, traversal))
    }

    @Test
    fun restore_externalFileOutsideEvidenceRoot_rejected() {
        val external = Uri.fromFile(File(context.cacheDir, "outside.jpg"))
        assertFalse(EvidenceStore.isStored(context, external))
    }
}
