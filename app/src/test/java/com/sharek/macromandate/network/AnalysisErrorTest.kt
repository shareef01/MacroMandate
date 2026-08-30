package com.sharek.macromandate.network

import com.sharek.macromandate.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * Failures reach the user as one plain sentence, never as raw exception text.
 *
 * The previous code surfaced `e.localizedMessage?.uppercase()` in a snackbar,
 * producing strings like "FAILED TO CONNECT TO ROUTER.HUGGINGFACE.CO/…" —
 * infrastructure detail, unlocalizable, and no indication of what to do.
 *
 * These cover the classification. The wording itself is guarded by
 * [com.sharek.macromandate.ErrorCopyTest], which reads the resource file.
 */
class AnalysisErrorTest {

    @Test
    fun timeoutIsClassifiedBeforeItsIOExceptionSupertype() {
        // SocketTimeoutException extends IOException; the order of the branches
        // decides whether a slow model reads as "no connection".
        assertEquals(AnalysisError.Timeout, AnalysisError.fromThrowable(SocketTimeoutException()))
    }

    @Test
    fun dnsFailureReadsAsOffline() {
        assertEquals(AnalysisError.Offline, AnalysisError.fromThrowable(UnknownHostException()))
    }

    @Test
    fun genericIoFailureReadsAsOffline() {
        assertEquals(AnalysisError.Offline, AnalysisError.fromThrowable(IOException("broken pipe")))
    }

    @Test
    fun tlsFailureIsDistinctFromBeingOffline() {
        assertEquals(
            AnalysisError.SecureConnectionFailed,
            AnalysisError.fromThrowable(SSLHandshakeException("bad cert"))
        )
    }

    @Test
    fun unrecognizedFailuresAreNotGivenAMisleadingCause() {
        assertEquals(
            AnalysisError.Unknown,
            AnalysisError.fromThrowable(IllegalStateException("internal detail /data/user/0/..."))
        )
    }

    @Test
    fun authFailuresAreDistinctFromOutages() {
        assertEquals(AnalysisError.CredentialRejected, AnalysisError.fromHttpStatus(401))
        assertEquals(AnalysisError.CredentialRejected, AnalysisError.fromHttpStatus(403))
        assertNotEquals(
            AnalysisError.CredentialRejected.messageRes,
            AnalysisError.ProviderUnavailable(500).messageRes
        )
    }

    @Test
    fun rateLimitingIsNotPresentedAsAnOutage() {
        assertEquals(AnalysisError.RateLimited, AnalysisError.fromHttpStatus(429))
        assertNotEquals(
            AnalysisError.RateLimited.messageRes,
            AnalysisError.ProviderUnavailable(503).messageRes
        )
    }

    @Test
    fun serverErrorsBecomeProviderUnavailable() {
        assertEquals(AnalysisError.ProviderUnavailable(500), AnalysisError.fromHttpStatus(500))
        assertEquals(AnalysisError.ProviderUnavailable(502), AnalysisError.fromHttpStatus(502))
    }

    @Test
    fun retryableFailuresAreTheOnesWorthRetrying() {
        assertTrue(AnalysisError.Timeout.isRetryable)
        assertTrue(AnalysisError.RateLimited.isRetryable)
        assertTrue(AnalysisError.ProviderUnavailable(503).isRetryable)
        // Retrying these just spends another billable request to fail again.
        assertFalse(AnalysisError.NoApiKey.isRetryable)
        assertFalse(AnalysisError.CredentialRejected.isRetryable)
        assertFalse(AnalysisError.ImageUnreadable.isRetryable)
        assertFalse(AnalysisError.UnreadableResult.isRetryable)
    }

    @Test
    fun everyCaseNamesADistinctStringResource() {
        // A copy/paste that pointed two cases at the same resource would make the
        // taxonomy pointless without failing anywhere else.
        val cases = listOf(
            AnalysisError.NoApiKey, AnalysisError.Offline, AnalysisError.Timeout,
            AnalysisError.CredentialRejected, AnalysisError.RateLimited,
            AnalysisError.ProviderUnavailable(500), AnalysisError.ImageUnreadable,
            AnalysisError.UnreadableResult, AnalysisError.SecureConnectionFailed,
            AnalysisError.Unknown
        )
        val ids = cases.map { it.messageRes }
        assertEquals("two errors share a message resource", ids.size, ids.toSet().size)
        ids.forEach { assertNotEquals(0, it) }
    }

    @Test
    fun theStatusCodeIsCarriedForLoggingButDoesNotChangeTheMessage() {
        // The code is useful in a bug report; it has no business in the snackbar.
        assertEquals(503, (AnalysisError.fromHttpStatus(503) as AnalysisError.ProviderUnavailable).statusCode)
        assertEquals(
            AnalysisError.ProviderUnavailable(500).messageRes,
            AnalysisError.ProviderUnavailable(503).messageRes
        )
    }

    @Test
    fun resourceIdsAreRealEntriesInTheStringTable() {
        assertEquals(R.string.error_no_api_key, AnalysisError.NoApiKey.messageRes)
        assertEquals(R.string.error_offline, AnalysisError.Offline.messageRes)
        assertEquals(R.string.error_timeout, AnalysisError.Timeout.messageRes)
    }
}
