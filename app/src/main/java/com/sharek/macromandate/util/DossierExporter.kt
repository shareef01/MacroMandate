package com.sharek.macromandate.util

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CheckedInputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.sharek.macromandate.model.MealEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object DossierExporter {

    private const val BACKUP_VERSION = 1
    private const val ZIP_VERSION = 2
    private const val MANIFEST = "manifest.json"
    private const val MAX_JSON_BYTES = 16L * 1024 * 1024
    private const val MAX_EXPANDED_BYTES = 512L * 1024 * 1024
    private const val MAX_ARCHIVE_BYTES = 520L * 1024 * 1024
    private const val MAX_MEALS = 50_000
    private val recordId = Regex("[A-Za-z0-9_-]{1,128}")
    // Excel/Sheets evaluate a cell as a formula when it opens with one of these.
    private val FORMULA_TRIGGERS = charArrayOf('=', '+', '-', '@', '\t', '\r', '\n', '\uff1d', '\uff0b', '\uff0d', '\uff20')

    suspend fun generateCsv(meals: List<MealEntry>): String = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.append("ID,Timestamp,FoodName,Calories,Protein,Carbs,Fat,IsLiquid\n")

        meals.forEach { meal ->
            val timestamp = dateFormat.format(Date(meal.timestamp))
            sb.append("\"${escapeCsvField(meal.id)}\",")
            sb.append("$timestamp,")
            sb.append("\"${escapeCsvField(meal.foodName)}\",")
            sb.append("${meal.calories},")
            sb.append("${meal.proteinGrams},")
            sb.append("${meal.carbsGrams},")
            sb.append("${meal.fatGrams},")
            sb.append("${meal.isLiquid}\n")
        }
        sb.toString()
    }

    suspend fun generateJson(meals: List<MealEntry>): String = withContext(Dispatchers.IO) {
        manifest(meals, BACKUP_VERSION, MealEntry::imageUri)
    }

    private fun manifest(meals: List<MealEntry>, version: Int, photo: (MealEntry) -> String?): String {
        val root = JSONObject()
        root.put("version", version)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("count", meals.size)

        val array = JSONArray()
        meals.forEach { meal ->
            val obj = JSONObject().apply {
                put("id", meal.id)
                put("timestamp", meal.timestamp)
                put("foodName", meal.foodName)
                put("calories", meal.calories)
                put("proteinGrams", meal.proteinGrams.toDouble())
                put("carbsGrams", meal.carbsGrams.toDouble())
                put("fatGrams", meal.fatGrams.toDouble())
                put("isLiquid", meal.isLiquid)
                photo(meal)?.let { put("imageUri", it) }
                if (meal.latitude != null) put("latitude", meal.latitude)
                if (meal.longitude != null) put("longitude", meal.longitude)
                if (meal.assessment != null) put("assessment", meal.assessment)
                put("isRestricted", meal.isRestricted)
                put("isNightRefueling", meal.isNightRefueling)
            }
            array.put(obj)
        }
        root.put("meals", array)
        return if (version == BACKUP_VERSION) root.toString(2) else root.toString()
    }

    suspend fun parseJsonBackup(jsonString: String): List<MealEntry>? = withContext(Dispatchers.IO) {
        try {
            parseManifest(JSONObject(jsonString), BACKUP_VERSION)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseManifest(root: JSONObject, version: Int): List<MealEntry> {
        require(root.getInt("version") == version) { "Unsupported backup version." }
        val array = root.getJSONArray("meals")
        require(array.length() <= MAX_MEALS && root.optInt("count", array.length()) == array.length()) {
            "Invalid backup record count."
        }
        val ids = HashSet<String>()
        return List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            val id = obj.getString("id")
            require(recordId.matches(id) && ids.add(id)) { "Invalid or duplicate meal ID." }
            val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            val foodName = obj.optString("foodName", "UNKNOWN REFUELING")
            val calories = obj.optInt("calories", 0)
            val protein = obj.optDouble("proteinGrams", 0.0).toFloat()
            val carbs = obj.optDouble("carbsGrams", 0.0).toFloat()
            val fat = obj.optDouble("fatGrams", 0.0).toFloat()
            val isLiquid = obj.optBoolean("isLiquid", false)
            val imageUri = if (obj.has("imageUri")) obj.getString("imageUri") else null
            require(calories >= 0 && listOf(protein, carbs, fat).all { it.isFinite() && it >= 0f }) {
                "Invalid meal nutrition."
            }
            val latitude = if (obj.has("latitude") && !obj.isNull("latitude")) obj.getDouble("latitude") else null
            val longitude = if (obj.has("longitude") && !obj.isNull("longitude")) obj.getDouble("longitude") else null
            require(latitude == null || latitude in -90.0..90.0) { "Invalid latitude." }
            require(longitude == null || longitude in -180.0..180.0) { "Invalid longitude." }
            val assessment = if (obj.has("assessment") && !obj.isNull("assessment")) obj.getString("assessment") else null
            val isRestricted = obj.optBoolean("isRestricted", false)
            val isNightRefueling = obj.optBoolean("isNightRefueling", false)

            MealEntry(
                    id = id,
                    timestamp = timestamp,
                    imageUri = imageUri,
                    foodName = foodName,
                    calories = calories,
                    proteinGrams = protein,
                    carbsGrams = carbs,
                    fatGrams = fat,
                    isLiquid = isLiquid,
                    latitude = latitude,
                    longitude = longitude,
                    assessment = assessment,
                    isRestricted = isRestricted,
                    isNightRefueling = isNightRefueling
                )
        }
    }

    private fun photoEntry(index: Int) = "photos/" + index + ".jpg"

    suspend fun exportBackupZip(context: Context, meals: List<MealEntry>, output: OutputStream) =
        withContext(Dispatchers.IO) {
            require(meals.size <= MAX_MEALS && meals.all { recordId.matches(it.id) }) {
                "Meal records exceed the supported backup limits."
            }
            val photoNames = HashMap<String, String>()
            val photos = meals.mapIndexedNotNull { index, meal ->
                val uri = meal.imageUri ?: return@mapIndexedNotNull null
                val name = photoEntry(index)
                val file = EvidenceStore.ownedFile(context, Uri.parse(uri))
                require(file?.isFile == true) { "A meal photo is unavailable. CSV can export the meal records." }
                require(file.length() in 1..EvidenceStore.MAX_EVIDENCE_BYTES) { "A photo exceeds the backup limits." }
                photoNames[meal.id] = name
                name to file
            }
            val json = manifest(meals, ZIP_VERSION) { photoNames[it.id] }.toByteArray(Charsets.UTF_8)
            require(json.size <= MAX_JSON_BYTES && photos.sumOf { it.second.length() } + json.size <= MAX_EXPANDED_BYTES) {
                "Backup exceeds the supported size limit."
            }
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(json)
                zip.closeEntry()
                photos.forEach { (name, file) ->
                    currentCoroutineContext().ensureActive()
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().use { EvidenceStore.copyBounded(it, zip, EvidenceStore.MAX_EVIDENCE_BYTES) }
                    zip.closeEntry()
                }
            }
        }

    /**
     * Validates a complete archive before writing evidence. The caller serializes this with
     * meal mutations and inserts the returned rows in a single Room transaction.
     */
    suspend fun importBackup(context: Context, source: InputStream): List<MealEntry> {
        val input = BufferedInputStream(source)
        input.mark(1)
        val prefix = input.read()
        input.reset()
        val job = currentCoroutineContext()
        if (prefix != 'P'.code) {
            val bytes = ByteArrayOutputStream()
            EvidenceStore.copyBounded(input, bytes, MAX_JSON_BYTES) { job.ensureActive() }
            return parseManifest(JSONObject(bytes.toString(Charsets.UTF_8.name())), BACKUP_VERSION)
                .map { it.copy(imageUri = null) }
        }

        val archive = File.createTempFile("dossier-", ".backup", context.cacheDir)
        try {
            archive.outputStream().use { output ->
                EvidenceStore.copyBounded(input, output, MAX_ARCHIVE_BYTES) { job.ensureActive() }
            }
            return ZipFile(archive).use { zip ->
                val json = ByteArrayOutputStream()
                val manifest = zip.getEntry(MANIFEST) ?: error("Missing backup manifest.")
                readEntry(zip, manifest, MAX_JSON_BYTES) { EvidenceStore.copyBounded(it, json, MAX_JSON_BYTES) }
                val root = JSONObject(json.toString(Charsets.UTF_8.name()))
                val version = root.getInt("version")
                require(version == BACKUP_VERSION || version == ZIP_VERSION) { "Unsupported backup version." }
                val meals = parseManifest(root, version)
                val names = meals.mapIndexed { index, meal ->
                    meal.imageUri?.let { reference ->
                        if (version == BACKUP_VERSION) {
                            // Version 1 wrote photos by the meal ID and saved local device URIs.
                            (meal.id + ".jpg").takeIf { zip.getEntry(it) != null }
                        } else {
                            require(reference == photoEntry(index)) { "Invalid backup photo reference." }
                            reference
                        }
                    }
                }
                val expected = names.filterNotNull().toSet() + MANIFEST
                val entries = zip.entries()
                val found = HashSet<String>()
                var expanded = 0L
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    require(entry.name in expected && found.add(entry.name) && !entry.isDirectory) {
                        "Unexpected or duplicate backup entry."
                    }
                    val limit = if (entry.name == MANIFEST) MAX_JSON_BYTES else EvidenceStore.MAX_EVIDENCE_BYTES
                    require(entry.size in 1..limit) { "Invalid backup entry size." }
                    expanded += entry.size
                    require(expanded <= MAX_EXPANDED_BYTES) { "Backup exceeds the supported size limit." }
                }
                require(found == expected) { "Backup is missing a referenced photo." }

                val written = mutableListOf<Uri>()
                try {
                    meals.mapIndexed { index, meal ->
                        currentCoroutineContext().ensureActive()
                        val restored = names[index]?.let { name ->
                            val entry = zip.getEntry(name)
                            readEntry(zip, entry, EvidenceStore.MAX_EVIDENCE_BYTES) { image ->
                                EvidenceStore.store(context, image, entry.size).also(written::add)
                            }
                        }
                        meal.copy(imageUri = restored?.toString())
                    }
                } catch (e: Exception) {
                    written.forEach { EvidenceStore.delete(context, it.toString()) }
                    throw e
                }
            }
        } finally {
            archive.delete()
        }
    }

    private fun <T> readEntry(zip: ZipFile, entry: ZipEntry, limit: Long, read: (InputStream) -> T): T {
        require(entry.size in 1..limit) { "Invalid backup entry size." }
        return CheckedInputStream(zip.getInputStream(entry), CRC32()).use { input ->
            val result = read(input)
            require(input.checksum.value == entry.crc) { "Backup entry failed its integrity check." }
            result
        }
    }

    internal fun escapeCsvField(field: String): String {
        // LLM-generated food names can contain quotes and line breaks; quotes are
        // doubled (RFC-4180) and newlines stripped so each record stays on one row.
        val flattened = field.replace("\r", " ").replace("\n", " ").replace("\"", "\"\"")
        // foodName comes back from the model, so it is attacker-influencable via the
        // photo. Neutralize spreadsheet formula injection (including with leading whitespace)
        // with a leading apostrophe.
        val trimmedSpaces = field.dropWhile { it.isWhitespace() || it.isISOControl() || it == '\ufeff' }
        return if (flattened.isNotEmpty() && (field[0] in FORMULA_TRIGGERS || (trimmedSpaces.isNotEmpty() && trimmedSpaces[0] in FORMULA_TRIGGERS))) {
            "'$flattened"
        } else {
            flattened
        }
    }
}
