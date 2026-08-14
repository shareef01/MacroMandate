package com.sharek.macromandate.network

import com.sharek.macromandate.BuildConfig

/**
 * Where the intelligence uplink points and how it authenticates.
 *
 * A credential placed in HUGGINGFACE_API_KEY is compiled into BuildConfig as a
 * string constant, which means it ships inside the APK and is recoverable from
 * any installed copy — R8 does not obfuscate string literals. Treat that path as
 * development-only.
 *
 * For anything distributed, stand up a backend that holds the credential and set
 * MANDATE_API_BASE_URL in local.properties to point at it; the proxy supplies its
 * own upstream auth and this client sends nothing sensitive.
 */
object ApiConfig {

    val baseUrl: String = BuildConfig.MANDATE_API_BASE_URL

    /** False when no credential was provisioned at build time. */
    val isConfigured: Boolean = BuildConfig.HUGGINGFACE_API_KEY.isNotBlank()

    val authHeader: String = "Bearer ${BuildConfig.HUGGINGFACE_API_KEY}"

    /**
     * Shown instead of letting an unauthenticated request fail as a bare 401,
     * which reads as a server outage rather than a missing local setup step.
     */
    const val NOT_CONFIGURED_MESSAGE: String =
        "UPLINK OFFLINE: NO INTELLIGENCE CREDENTIAL PROVISIONED. " +
            "SET HUGGINGFACE_API_KEY OR MANDATE_API_BASE_URL IN LOCAL.PROPERTIES."
}
