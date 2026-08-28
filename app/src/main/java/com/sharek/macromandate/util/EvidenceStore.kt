package com.sharek.macromandate.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import java.io.File

/**
 * Durable storage for meal evidence images.
 *
 * Neither of the two capture paths produces a URI that survives on its own:
 * cacheDir is evictable by the OS under storage pressure, and a photo-picker
 * content URI carries a read grant that dies with the process. Persisting either
 * one directly leaves MealDetailScreen rendering a blank image after a restart,
 * so both are funnelled into filesDir instead.
 */
object EvidenceStore {

    private const val TAG = "EvidenceStore"
    private const val DIR = "evidence"

    fun directory(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    /** Destination for a freshly captured frame, named after the meal it will back. */
    fun newFile(context: Context, id: String): File = File(directory(context), "$id.jpg")

    /**
     * True when [uri] already points at a file this store owns.
     *
     * Compares *canonical* paths. `getAbsolutePath` does not resolve `..`, so
     * `.../files/evidence/../../databases/macro_mandate_db` passed the old
     * prefix check — and [delete] would then have unlinked the meal database.
     * A restored backup can name any path it likes, which made that reachable.
     */
    fun isStored(context: Context, uri: Uri): Boolean {
        if (uri.scheme != "file") return false
        val path = uri.path ?: return false
        return try {
            val root = directory(context).canonicalFile
            val candidate = File(path).canonicalFile
            candidate != root && candidate.toPath().startsWith(root.toPath())
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve evidence path", e)
            false
        }
    }

    /**
     * Copies [uri] into internal storage under [id] and returns the durable file
     * URI. Returns the original URI unchanged if it is already stored here, or
     * null if the copy fails.
     */
    fun persist(context: Context, uri: Uri, id: String): Uri? {
        if (isStored(context, uri)) return uri
        return try {
            val target = newFile(context, id)
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
            if (copied) Uri.fromFile(target) else null
        } catch (e: Exception) {
            Log.e(TAG, "Could not persist evidence image", e)
            null
        }
    }

    /** Removes the backing file for a stored evidence URI, if this store owns it. */
    fun delete(context: Context, imageUri: String?) {
        val uri = imageUri?.let { runCatching { it.toUri() }.getOrNull() } ?: return
        if (!isStored(context, uri)) return
        val path = uri.path ?: return
        runCatching { File(path).delete() }
            .onFailure { Log.w(TAG, "Could not delete evidence image", it) }
    }

    /** Clears every stored image. Used when the whole meal log is wiped. */
    fun deleteAll(context: Context) {
        runCatching { directory(context).listFiles()?.forEach { it.delete() } }
            .onFailure { Log.w(TAG, "Could not clear evidence directory", it) }
    }
}
