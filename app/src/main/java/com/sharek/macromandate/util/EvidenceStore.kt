package com.sharek.macromandate.util

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import org.json.JSONArray
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

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
    private const val STAGING_DIR = "pending"
    internal const val MAX_EVIDENCE_BYTES = 16L * 1024 * 1024 // 16 MB

    fun directory(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    fun stagingDirectory(context: Context): File =
        File(context.cacheDir, STAGING_DIR).apply { if (!exists()) mkdirs() }

    private val activeStaging = mutableSetOf<String>()

    @Synchronized
    fun newStagingFile(context: Context, id: String): File =
        File(stagingDirectory(context), "$id.jpg").also { activeStaging.add(it.absolutePath) }

    @Synchronized
    fun releaseStaging(uri: Uri) {
        activeStaging.remove(uri.path)
    }

    fun discardStaging(context: Context, uri: Uri) {
        val file = File(uri.path ?: return)
        if (file.parentFile == stagingDirectory(context)) file.delete()
    }

    internal fun ownedFile(context: Context, uri: Uri): File? {
        if (uri.scheme != "file" || !uri.authority.isNullOrEmpty()) return null
        val file = File(uri.path ?: return null)
        val parent = directory(context).canonicalFile
        return file.takeIf {
            it.absoluteFile.parentFile?.canonicalFile == parent &&
                it.canonicalFile == File(parent, it.name) &&
                !java.nio.file.Files.isSymbolicLink(it.toPath())
        }
    }

    /**
     * Commits an image to durable storage under [id].
     * Uses a temporary file during the bounded copy, then atomically renames.
     */
    fun commit(context: Context, uri: Uri): Uri? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { store(context, it) }
        } catch (e: Exception) {
            Log.e(TAG, "Could not commit evidence image", e)
            null
        }
    }

    internal fun store(context: Context, input: InputStream, limit: Long = MAX_EVIDENCE_BYTES): Uri {
        val folder = directory(context)
        val temporary = File.createTempFile("incoming-", ".tmp", folder)
        val target = File(folder, "photo-" + temporary.name.removeSuffix(".tmp") + ".jpg")
        try {
            temporary.outputStream().use { output ->
                copyBounded(input, output, limit)
                if (temporary.length() == 0L) throw IOException("Evidence image is empty")
                output.fd.sync()
            }
            java.nio.file.Files.move(temporary.toPath(), target.toPath())
            return Uri.fromFile(target)
        } finally {
            temporary.delete()
        }
    }

    internal fun copyBounded(input: InputStream, output: OutputStream, limit: Long, checkActive: () -> Unit = {}) {
        val buffer = ByteArray(8192)
        var totalRead = 0L
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            checkActive()
            totalRead += bytesRead
            if (totalRead > limit) {
                throw IOException("File exceeds maximum allowed size")
            }
            output.write(buffer, 0, bytesRead)
        }
    }

    /** Removes the backing file for a stored evidence URI, if this store owns it. */
    fun delete(context: Context, imageUri: String?): Result<Unit> {
        val uri = imageUri?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() } ?: return Result.success(Unit)
        return try {
            val file = ownedFile(context, uri) ?: return Result.success(Unit)
            if (!file.exists()) return Result.success(Unit)
            if (file.delete()) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("Failed to delete ${file.name}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Records the intent before a Room delete. Reconciliation checks the committed references. */
    internal fun markForDeletion(context: Context, imageUri: String?) {
        val file = imageUri?.let { ownedFile(context, Uri.parse(it)) } ?: return
        val pending = readDeletionJournal(context)
        writeDeletionJournal(context, pending + file.name)
    }

    internal fun markAllForDeletion(context: Context) {
        val files = directory(context).listFiles() ?: throw IOException("Could not list evidence files")
        val names = files.filter { ownedFile(context, Uri.fromFile(it)) != null }.map { it.name }
        writeDeletionJournal(context, readDeletionJournal(context) + names)
    }

    /** Call under the mutation lock with references from a successfully read Room snapshot. */
    internal fun reconcileDurable(context: Context, referencedUris: Set<String>): Result<Unit> {
        val referencedFiles = referencedUris.mapNotNull { ownedFile(context, Uri.parse(it)) }
            .mapTo(HashSet()) { it.canonicalFile }
        val pending = readDeletionJournal(context)
        val failed = mutableSetOf<String>()
        val files = directory(context).listFiles() ?: throw IOException("Could not list evidence files")
        files.forEach { file ->
            val managed = file.name.startsWith("photo-incoming-") || file.name.startsWith("incoming-")
            if ((managed || file.name in pending) && file.canonicalFile !in referencedFiles) {
                if (!file.delete() && file.exists()) failed.add(file.name)
            }
        }
        if (pending.isNotEmpty() || failed.isNotEmpty()) writeDeletionJournal(context, failed)
        return if (failed.isEmpty()) Result.success(Unit)
        else Result.failure(IOException("Some evidence files could not be removed"))
    }

    private fun deletionJournal(context: Context) =
        AtomicFile(File(context.filesDir, "pending-evidence-deletions.json"))

    private fun readDeletionJournal(context: Context): Set<String> {
        val journal = deletionJournal(context)
        val bytes = try {
            journal.readFully()
        } catch (e: FileNotFoundException) {
            if (journal.baseFile.exists()) throw e
            return emptySet()
        }
        val names = JSONArray(bytes.toString(Charsets.UTF_8))
        return (0 until names.length()).mapTo(HashSet()) { names.getString(it) }
    }

    private fun writeDeletionJournal(context: Context, names: Set<String>) {
        val journal = deletionJournal(context)
        if (names.isEmpty()) {
            journal.delete()
            return
        }
        val output = journal.startWrite()
        try {
            output.write(JSONArray(names.toList()).toString().toByteArray(Charsets.UTF_8))
            journal.finishWrite(output)
        } catch (e: Exception) {
            journal.failWrite(output)
            throw e
        }
    }

    /** Cleans up aged orphaned staging files. */
    @Synchronized
    fun reconcileStaging(context: Context) {
        try {
            val threshold = System.currentTimeMillis() - 24 * 60 * 60 * 1000L // 24 hours
            stagingDirectory(context).listFiles()?.forEach { file ->
                if (file.absolutePath !in activeStaging && file.lastModified() < threshold) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reconcile staging directory", e)
        }
    }
}
