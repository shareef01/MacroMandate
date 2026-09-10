package com.sharek.macromandate.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

sealed interface EvidencePersistResult {
    data class Success(val uri: Uri, val newlyCreated: Boolean) : EvidencePersistResult
    data object SourceUnavailable : EvidencePersistResult
    data object TooLarge : EvidencePersistResult
    data object InvalidImage : EvidencePersistResult
    data object InvalidTarget : EvidencePersistResult
    data class IoFailure(val cause: IOException) : EvidencePersistResult
}

data class EvidenceDeleteResult(val deleted: Boolean, val owned: Boolean)

data class EvidenceDeleteAllResult(val deletedCount: Int, val failedCount: Int) {
    val complete: Boolean get() = failedCount == 0
}

data class OrphanCleanupResult(
    val deletedCount: Int,
    val failedCount: Int,
    val totalInspected: Int
)

/** Bounded, atomic storage for app-owned meal evidence. */
object EvidenceStore {
    private const val TAG = "EvidenceStore"
    private const val DIR = "evidence"
    private const val JPEG_SUFFIX = ".jpg"
    const val MAX_EVIDENCE_BYTES = 15L * 1024 * 1024

    fun directory(context: Context): File {
        val result = File(context.filesDir, DIR)
        check((result.isDirectory || result.mkdirs()) && result.canonicalFile.parentFile == context.filesDir.canonicalFile) {
            "Unable to create private evidence directory"
        }
        return result
    }

    fun newFile(context: Context, id: String): File {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Invalid evidence identifier" }
        return File(directory(context), "$id$JPEG_SUFFIX")
    }

    /** The single ownership check used by deletion, restore and orphan cleanup. */
    fun resolveOwnedFile(
        context: Context,
        uri: Uri,
        requireExists: Boolean = false,
        requireJpeg: Boolean = true
    ): File? {
        if (uri.scheme != "file" || uri.path.isNullOrBlank()) return null
        return try {
            val root = directory(context).canonicalFile
            val candidate = File(uri.path!!).canonicalFile
            candidate.takeIf {
                it.parentFile == root &&
                    (!requireJpeg || it.name.endsWith(JPEG_SUFFIX, ignoreCase = true)) &&
                    (!requireExists || it.isFile)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not resolve evidence path", e)
            null
        }
    }

    fun isStored(context: Context, uri: Uri, requireExists: Boolean = false): Boolean =
        resolveOwnedFile(context, uri, requireExists) != null

    /** Copies through a bounded stream to a temporary file and publishes atomically. */
    fun persist(context: Context, uri: Uri, id: String): EvidencePersistResult {
        resolveOwnedFile(context, uri, requireExists = true)?.let {
            return EvidencePersistResult.Success(Uri.fromFile(it), newlyCreated = false)
        }
        val target = try {
            newFile(context, id).canonicalFile
        } catch (_: IllegalArgumentException) {
            return EvidencePersistResult.InvalidTarget
        } catch (e: IOException) {
            return EvidencePersistResult.IoFailure(e)
        }
        if (resolveOwnedFile(context, Uri.fromFile(target)) == null) return EvidencePersistResult.InvalidTarget

        val temp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return EvidencePersistResult.SourceUnavailable
            input.use { source ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_EVIDENCE_BYTES) return EvidencePersistResult.TooLarge
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                    output.fd.sync()
                }
            }
            if (temp.length() == 0L) return EvidencePersistResult.SourceUnavailable
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return EvidencePersistResult.InvalidImage
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return EvidencePersistResult.Success(Uri.fromFile(target), newlyCreated = true)
        } catch (e: IOException) {
            return EvidencePersistResult.IoFailure(e)
        } finally {
            if (temp.exists() && !temp.delete()) Log.w(TAG, "Could not remove partial evidence file")
        }
    }

    fun delete(context: Context, imageUri: String?): EvidenceDeleteResult {
        val uri = imageUri?.let { runCatching { it.toUri() }.getOrNull() }
            ?: return EvidenceDeleteResult(deleted = true, owned = false)
        val file = resolveOwnedFile(context, uri)
            ?: return EvidenceDeleteResult(deleted = true, owned = false)
        if (!file.exists()) return EvidenceDeleteResult(deleted = true, owned = true)
        val deleted = try { file.delete() || !file.exists() } catch (e: SecurityException) {
            Log.w(TAG, "Could not delete evidence image", e)
            false
        }
        return EvidenceDeleteResult(deleted, owned = true)
    }

    fun deleteAll(context: Context): EvidenceDeleteAllResult {
        val files = directory(context).listFiles() ?: return EvidenceDeleteAllResult(0, 1)
        var deleted = 0
        var failed = 0
        for (entry in files) {
            val file = resolveOwnedFile(context, Uri.fromFile(entry), requireJpeg = false) ?: continue
            try {
                if (file.delete() || !file.exists()) deleted++ else failed++
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not delete evidence file", e)
                failed++
            }
        }
        return EvidenceDeleteAllResult(deleted, failed)
    }

    fun cleanupOrphans(context: Context, activeImageUris: Set<String>): OrphanCleanupResult {
        val files = directory(context).listFiles() ?: return OrphanCleanupResult(0, 1, 0)
        val active = activeImageUris.mapNotNull { raw ->
            runCatching { resolveOwnedFile(context, raw.toUri(), requireExists = true)?.canonicalPath }.getOrNull()
        }.toSet()
        var deleted = 0
        var failed = 0
        files.forEach { entry ->
            val file = resolveOwnedFile(context, Uri.fromFile(entry), requireJpeg = false) ?: return@forEach
            if (file.canonicalPath !in active) {
                if (file.delete() || !file.exists()) deleted++ else failed++
            }
        }
        return OrphanCleanupResult(deleted, failed, files.size)
    }
}
