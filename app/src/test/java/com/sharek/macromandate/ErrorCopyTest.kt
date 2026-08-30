package com.sharek.macromandate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Enforces the copy rules on `strings.xml` itself.
 *
 * The wording of an error is not decoration — it is the entire remedy the user
 * gets. This app previously showed uppercased exception text with hostnames and
 * IP addresses in it, so the rules that replaced that are worth holding
 * mechanically rather than by review.
 *
 * Reads the resource file directly. A unit test has no `Context`, and putting
 * these behind the instrumented suite would mean they only ran on the device
 * pass that has not happened yet.
 */
class ErrorCopyTest {

    private lateinit var strings: Map<String, String>

    @Before
    fun loadStrings() {
        // Resolves from the module directory whether the test is run from the
        // project root or from app/.
        val candidates = listOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("strings.xml not found; looked in ${candidates.map { it.absolutePath }}")

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        strings = (0 until nodes.length).associate { i ->
            val node = nodes.item(i)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    private fun errorStrings() = strings.filterKeys {
        it.startsWith("error_") || it.startsWith("restore_error_")
    }

    @Test
    fun theErrorStringsExist() {
        // Guards against the whole set being renamed out from under these rules.
        assertTrue("no error strings found", errorStrings().size >= 10)
    }

    @Test
    fun noErrorLeaksAHostnameOrUrl() {
        errorStrings().forEach { (key, value) ->
            assertFalse("$key leaks a URL: $value", value.contains("http", ignoreCase = true))
            assertFalse("$key leaks a hostname: $value", value.contains(".co"))
        }
    }

    @Test
    fun noErrorShoutsAtTheUser() {
        // "[ MANDATE VIOLATION ] SERVER EMBARGO" was a real message in this app.
        // The chrome can be theatrical; a failure explanation cannot.
        errorStrings().forEach { (key, value) ->
            val letters = value.filter { it.isLetter() }
            assertFalse("$key is all caps: $value", letters.isNotEmpty() && letters.all { it.isUpperCase() })
        }
    }

    @Test
    fun everyErrorIsACompleteSentence() {
        errorStrings().forEach { (key, value) ->
            assertTrue("$key is blank", value.isNotBlank())
            assertTrue("$key does not end in a full stop: $value", value.trimEnd().endsWith("."))
            assertTrue(
                "$key does not start with a capital: $value",
                value.first().isUpperCase()
            )
        }
    }

    @Test
    fun noErrorExposesAStatusCodeOrStackFrame() {
        val forbidden = listOf("Exception", "null", "at com.", "HTTP ", "code:")
        errorStrings().forEach { (key, value) ->
            forbidden.forEach { token ->
                assertFalse("$key contains \"$token\": $value", value.contains(token))
            }
            assertFalse("$key contains a bare status code: $value", Regex("\\b[45]\\d\\d\\b").containsMatchIn(value))
        }
    }

    @Test
    fun failuresThatStillLeaveTheAppUsableSaySo() {
        // Manual logging needs no key and no network. When analysis fails for a
        // reason that does not affect it, the message has to point there —
        // otherwise a network blip reads as "the app is broken".
        listOf("error_offline", "error_timeout", "error_unknown").forEach { key ->
            val value = strings.getValue(key)
            assertTrue(
                "$key should mention the manual fallback: $value",
                value.contains("manual", ignoreCase = true)
            )
        }
    }

    @Test
    fun failuresTheUserCanFixSayWhereToFixThem() {
        listOf("error_no_api_key", "error_credential_rejected").forEach { key ->
            val value = strings.getValue(key)
            assertTrue(
                "$key should point at Settings: $value",
                value.contains("Settings")
            )
        }
    }

    @Test
    fun theEstimateFramingIsPresentWhereItMatters() {
        // The one place a number crosses from "the model said" to "my log".
        val review = strings.getValue("analysis_review_subtitle")
        assertTrue("the review sheet must name AI: $review", review.contains("AI"))
        assertTrue("the review sheet must say estimate: $review", review.contains("Estimated"))
    }

    @Test
    fun theNetworkBoundaryIsStatedInBothPlacesItIsCrossed() {
        val capture = strings.getValue("capture_network_notice")
        assertTrue("capture notice must name the provider: $capture", capture.contains("provider"))

        val summary = strings.getValue("trends_summary_notice")
        assertTrue("summary notice must name the provider: $summary", summary.contains("provider"))
    }

    @Test
    fun noStatusLineClaimsAFeatureIsLocked() {
        // The compliance status is a label. If a string here starts promising
        // that something is locked, the gating has come back.
        strings.filterKeys { it.startsWith("status_") }.forEach { (key, value) ->
            assertFalse("$key mentions locking: $value", value.contains("lock", ignoreCase = true))
        }
    }
}
