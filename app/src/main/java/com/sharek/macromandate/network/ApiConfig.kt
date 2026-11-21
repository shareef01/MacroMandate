package com.sharek.macromandate.network

import com.sharek.macromandate.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Where the analysis service lives and how requests authenticate to it.
 *
 * The key is supplied by the user in Settings and kept in app-private storage.
 * A key placed in local.properties still works as a build-time fallback for
 * development, but it compiles into BuildConfig as a string constant and is
 * recoverable from any installed copy, so it must not be used for a build you
 * intend to distribute.
 *
 * For distribution, point [baseUrl] at a backend that holds the credential.
 */
object ApiConfig {

    private const val DEFAULT_HUGGING_FACE_URL = "https://router.huggingface.co/"

    private val configuredEndpoint by lazy {
        BuildConfig.MANDATE_API_BASE_URL.trim().toHttpUrlOrNull()?.takeIf {
            it.isHttps && it.username.isEmpty() && it.password.isEmpty() &&
                it.query == null && it.fragment == null && it.encodedPath.endsWith("/")
        }
    }

    val baseUrl
        get() = requireNotNull(configuredEndpoint) {
            "The analysis endpoint must be HTTPS, end with a slash, and contain no credentials, query, or fragment."
        }

    val providerName: String
        get() = when (val host = configuredEndpoint?.host) {
            "router.huggingface.co" -> "Hugging Face"
            "openrouter.ai" -> "OpenRouter"
            null -> "Analysis provider"
            else -> host
        }

    val credentialPreferenceName: String
        get() = credentialPreferenceName(configuredEndpoint)

    internal fun credentialPreferenceName(endpoint: HttpUrl?): String = when (val url = endpoint?.toString()) {
        DEFAULT_HUGGING_FACE_URL -> "api_key"
        null -> "analysis_key:unconfigured"
        else -> "analysis_key:$url"
    }

    internal fun acceptsKey(key: String, endpoint: HttpUrl? = configuredEndpoint): Boolean {
        val trimmed = key.trim()
        return trimmed.isEmpty() || endpoint?.host != "openrouter.ai" ||
            (trimmed.startsWith("sk-or-") && trimmed.length > "sk-or-".length && trimmed.none(Char::isWhitespace))
    }

    /** The selected model must support image input. */
    val model: String = BuildConfig.MANDATE_MODEL_ID

    /** Build-time fallback; blank unless set in local.properties. */
    val buildTimeKey: String
        get() = BuildConfig.HUGGINGFACE_API_KEY
            .takeIf { configuredEndpoint?.toString() == DEFAULT_HUGGING_FACE_URL }.orEmpty()

    fun authHeader(apiKey: String): String = "Bearer $apiKey"

    val NOT_CONFIGURED_MESSAGE: String
        get() = "Add a key for $providerName in Settings to enable meal analysis."
}
