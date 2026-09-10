package com.sharek.macromandate.domain

import com.sharek.macromandate.util.DossierExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupIoTest {
    @Test
    fun reportExport_nullOutputStream_returnsFailure() {
        assertFalse(writeNonEmptyUtf8("report") { null })
    }

    @Test
    fun export_zeroBytePayload_returnsFailure() {
        assertFalse(writeNonEmptyUtf8("") { ByteArrayOutputStream() })
    }

    @Test
    fun export_writesAndFlushesPayload() {
        val output = ByteArrayOutputStream()
        assertTrue(writeNonEmptyUtf8("hello") { output })
        assertEquals("hello", output.toString("UTF-8"))
    }

    @Test
    fun restore_oversizeInput_abortsDuringRead() {
        val input = ByteArrayInputStream(ByteArray(2048))
        val result = runCatching { readUtf8Bounded(input, 1024) }
        assertTrue(result.exceptionOrNull() is DossierExporter.RestoreException)
        assertTrue(input.available() > 0)
    }
}
