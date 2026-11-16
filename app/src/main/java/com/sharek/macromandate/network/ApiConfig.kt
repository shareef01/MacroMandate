package com.sharek.macromandate.network

import com.sharek.macromandate.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
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

    val baseUrl: String = sanitizeAndValidateBaseUrl(
        rawUrl = BuildConfig.MANDATE_API_BASE_URL,
        requireHttps = !BuildConfig.DEBUG
    )

    val model: String = BuildConfig.MANDATE_MODEL_ID

    private const val LEGACY_HUGGING_FACE_URL = "https://router.huggingface.co/"
    private const val OPENROUTER_HOST = "openrouter.ai"

    val providerName: String = when (baseUrl.toHttpUrl().host) {
        OPENROUTER_HOST -> "OpenRouter"
        "router.huggingface.co" -> "Hugging Face"
        else -> baseUrl.toHttpUrl().host
    }

    /** The original build-time token was configured for Hugging Face only. */
    val buildTimeKey: String = BuildConfig.HUGGINGFACE_API_KEY
        .takeIf { baseUrl == LEGACY_HUGGING_FACE_URL }
        .orEmpty()

    internal fun credentialPreferenceName(endpoint: String): String =
        if (endpoint == LEGACY_HUGGING_FACE_URL) "api_key" else "analysis_key:" + endpoint

    internal fun acceptsKey(endpoint: String, key: String): Boolean {
        val trimmed = key.trim()
        return when {
            trimmed.isEmpty() -> true
            trimmed.startsWith("hf_") -> endpoint == LEGACY_HUGGING_FACE_URL
            trimmed.startsWith("sk-or-") -> endpoint.toHttpUrl().host == OPENROUTER_HOST
            else -> endpoint.toHttpUrl().host != OPENROUTER_HOST
        }
    }

    fun authHeader(apiKey: String): String = "Bearer ${apiKey.trim()}"

    const val NOT_CONFIGURED_MESSAGE: String =
        "No API key set. Add one in Settings to enable meal analysis."

    /**
     * Validates that [rawUrl] is a syntactically valid HTTP(S) URL, enforces HTTPS
     * for release builds, and ensures a trailing slash required by Retrofit.
     */
    internal fun sanitizeAndValidateBaseUrl(rawUrl: String, requireHttps: Boolean): String {
        val trimmed = rawUrl.trim()
        val parsed = trimmed.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("MANDATE_API_BASE_URL is not a valid HTTP(S) URL: '$rawUrl'")

        if (requireHttps && parsed.scheme != "https") {
            throw IllegalArgumentException("MANDATE_API_BASE_URL must use HTTPS in release builds: '$rawUrl'")
        }

        require(parsed.username.isEmpty() && parsed.password.isEmpty() && parsed.query == null && parsed.fragment == null) {
            "MANDATE_API_BASE_URL cannot contain credentials, a query, or a fragment."
        }

        val normalized = parsed.toString()
        return if (normalized.endsWith("/")) normalized else "$normalized/"
    }
}
