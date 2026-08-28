package com.sharek.macromandate.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * which produced strings like "FAILED TO CONNECT TO ROUTER.HUGGINGFACE.CO/…"
 * — infrastructure detail, unlocalizable, and no indication of what to do.
 */
class AnalysisErrorTest {

    @Test
    fun timeoutIsClassifiedBeforeItsIOExceptionSupertype() {
        // SocketTimeoutException extends IOException; ordering in the `when`
        // decides whether a slow model reads as "offline".
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
    fun unrecognizedFailuresDoNotLeakTheirMessage() {
        val error = AnalysisError.fromThrowable(IllegalStateException("internal detail /data/user/0/..."))
        assertEquals(AnalysisError.Unknown, error)
        assertFalse(error.message.contains("/data/user"))
    }

    @Test
    fun authFailuresPointAtTheKeyRatherThanTheNetwork() {
        assertEquals(AnalysisError.CredentialRejected, AnalysisError.fromHttpStatus(401))
        assertEquals(AnalysisError.CredentialRejected, AnalysisError.fromHttpStatus(403))
        assertTrue(AnalysisError.fromHttpStatus(401).message.contains("Settings"))
    }

    @Test
    fun rateLimitingIsNotPresentedAsAnOutage() {
        assertEquals(AnalysisError.RateLimited, AnalysisError.fromHttpStatus(429))
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
        // Retrying these just wastes another billable request.
        assertFalse(AnalysisError.NoApiKey.isRetryable)
        assertFalse(AnalysisError.CredentialRejected.isRetryable)
        assertFalse(AnalysisError.ImageUnreadable.isRetryable)
    }

    @Test
    fun everyMessageIsAReadableSentence() {
        val all = listOf(
            AnalysisError.NoApiKey, AnalysisError.Offline, AnalysisError.Timeout,
            AnalysisError.CredentialRejected, AnalysisError.RateLimited,
            AnalysisError.ProviderUnavailable(500), AnalysisError.ImageUnreadable,
            AnalysisError.UnreadableResult, AnalysisError.SecureConnectionFailed,
            AnalysisError.Unknown
        )
        all.forEach { error ->
            assertTrue("empty message for $error", error.message.isNotBlank())
            assertTrue("$error should end in a full stop", error.message.endsWith("."))
            // No status codes, hostnames or stack frames in user-facing copy.
            assertFalse("$error leaks a hostname", error.message.contains("http"))
            assertFalse("$error shouts", error.message == error.message.uppercase())
        }
    }

    @Test
    fun offlineFailuresPointAtManualLoggingAsAWayForward() {
        // The app is fully usable without the network; the copy has to say so.
        assertTrue(AnalysisError.Offline.message.contains("manually"))
        assertTrue(AnalysisError.Timeout.message.contains("manually"))
    }
}
