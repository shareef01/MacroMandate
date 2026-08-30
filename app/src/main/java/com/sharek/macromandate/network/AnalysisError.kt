package com.sharek.macromandate.network

import androidx.annotation.StringRes
import com.sharek.macromandate.R
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
 * what to do next.
 *
 * Each case carries a **string resource id**, not a string. A domain class has
 * no business holding English, and resolving the text here would also mean
 * resolving it at throw time rather than at display time — which is wrong for a
 * value that may be shown after a locale change.
 */
sealed class AnalysisError(@StringRes val messageRes: Int) {

    /** No credential configured; analysis cannot run at all. */
    object NoApiKey : AnalysisError(R.string.error_no_api_key)

    /** Device is offline or DNS failed. Manual logging still works. */
    object Offline : AnalysisError(R.string.error_offline)

    /** The request was accepted but took too long. Worth retrying. */
    object Timeout : AnalysisError(R.string.error_timeout)

    /** The provider rejected the credential (401/403). */
    object CredentialRejected : AnalysisError(R.string.error_credential_rejected)

    /** Rate limited (429). Retrying immediately will fail again. */
    object RateLimited : AnalysisError(R.string.error_rate_limited)

    /** Provider-side failure (5xx), or an HTML error page instead of JSON. */
    data class ProviderUnavailable(val statusCode: Int?) :
        AnalysisError(R.string.error_provider_unavailable)

    /** The image could not be read, decoded, or was empty. */
    object ImageUnreadable : AnalysisError(R.string.error_image_unreadable)

    /** A reply arrived but carried no nutrition object we could read. */
    object UnreadableResult : AnalysisError(R.string.error_unreadable_result)

    /** TLS negotiation failed — often a captive portal or intercepting proxy. */
    object SecureConnectionFailed : AnalysisError(R.string.error_secure_connection_failed)

    /** Anything not otherwise classified. Detail stays in logs, not in the UI. */
    object Unknown : AnalysisError(R.string.error_unknown)

    /** True when retrying the same request has a reasonable chance of succeeding. */
    val isRetryable: Boolean
        get() = when (this) {
            Timeout, Offline, RateLimited, is ProviderUnavailable, Unknown -> true
            NoApiKey, CredentialRejected, ImageUnreadable, UnreadableResult,
            SecureConnectionFailed -> false
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
         * before [IOException] because it is a subclass of it — reversing them
         * would report every slow model as "no connection".
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
