package com.sharek.macromandate.network

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Every way meal analysis can fail, as something the user can act on.
 *
 * Raw exception text used to be uppercased and shown in a snackbar, which meant
 * people read things like "FAILED TO CONNECT TO ROUTER.HUGGINGFACE.CO/2606:...".
 * That leaks infrastructure detail, cannot be localized, and never tells anyone
 * what to do next. Each case here maps to one plain sentence and, where it
 * applies, a fallback the user can take right now.
 */
sealed class AnalysisError {

    /** No credential configured; analysis cannot run at all. */
    object NoApiKey : AnalysisError()

    /** Device is offline or DNS failed. Manual logging still works. */
    object Offline : AnalysisError()

    /** The request was accepted but took too long. Worth retrying. */
    object Timeout : AnalysisError()

    /** The provider rejected the credential (401/403). */
    object CredentialRejected : AnalysisError()

    /** Rate limited (429). Retrying immediately will fail again. */
    object RateLimited : AnalysisError()

    /** Provider-side failure (5xx), or an HTML error page instead of JSON. */
    data class ProviderUnavailable(val statusCode: Int?) : AnalysisError()

    /** The image could not be read, decoded, or was empty. */
    object ImageUnreadable : AnalysisError()

    /** A reply arrived but carried no nutrition object we could read. */
    object UnreadableResult : AnalysisError()

    /** TLS negotiation failed — often a captive portal or intercepting proxy. */
    object SecureConnectionFailed : AnalysisError()

    /** Anything not otherwise classified. Detail stays in logs, not in the UI. */
    object Unknown : AnalysisError()

    /**
     * One sentence, sentence case, no jargon. The terminal voice lives in the
     * chrome around this text — the message itself has to stay legible.
     */
    val message: String
        get() = when (this) {
            NoApiKey -> "No API key set. Add one in Settings to analyse photos."
            Offline -> "No connection. You can still log this meal manually."
            Timeout -> "Analysis timed out. Try again, or log the meal manually."
            CredentialRejected -> "The analysis service rejected your API key. Check it in Settings."
            RateLimited -> "The analysis service is rate limiting requests. Try again shortly."
            is ProviderUnavailable -> "The analysis service is unavailable right now. Try again later."
            ImageUnreadable -> "That image could not be read. Try another photo."
            UnreadableResult -> "The analysis service returned no usable nutrition data."
            SecureConnectionFailed -> "Could not establish a secure connection to the analysis service."
            Unknown -> "Analysis failed. You can still log this meal manually."
        }

    /** True when retrying the same request has a reasonable chance of succeeding. */
    val isRetryable: Boolean
        get() = when (this) {
            Timeout, Offline, RateLimited, is ProviderUnavailable, Unknown -> true
            NoApiKey, CredentialRejected, ImageUnreadable, UnreadableResult, SecureConnectionFailed -> false
        }

    companion object {
        /** Maps an HTTP status code onto the closest domain error. */
        fun fromHttpStatus(code: Int): AnalysisError = when (code) {
            401, 403 -> CredentialRejected
            429 -> RateLimited
            else -> ProviderUnavailable(code)
        }

        /**
         * Maps a transport-layer exception. [SocketTimeoutException] is checked
         * before [IOException] because it is a subclass of it.
         */
        fun fromThrowable(throwable: Throwable): AnalysisError = when (throwable) {
            is SocketTimeoutException -> Timeout
            is UnknownHostException -> Offline
            is SSLException -> SecureConnectionFailed
            is IOException -> Offline
            else -> Unknown
        }
    }
}
