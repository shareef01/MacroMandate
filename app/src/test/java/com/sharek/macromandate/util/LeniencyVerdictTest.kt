package com.sharek.macromandate.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeniencyVerdictTest {

    private fun response(decision: String, message: String = "State Message") =
        """Assistant: { "decision": "$decision", "response": "$message" }"""

    @Test
    fun grantedIsParsed() {
        val verdict = LeniencyVerdict.parse(response("GRANTED", "Mandate reset."))
        assertEquals(LeniencyVerdict.Granted("Mandate reset."), verdict)
    }

    @Test
    fun deniedIsParsed() {
        val verdict = LeniencyVerdict.parse(response("DENIED", "Terminal warning."))
        assertEquals(LeniencyVerdict.Denied("Terminal warning."), verdict)
    }

    @Test
    fun decisionIsCaseAndWhitespaceInsensitive() {
        // A response of "Granted" previously fell through the equality check and
        // permanently locked the user out of their own data.
        assertTrue(LeniencyVerdict.parse(response(" Granted ")) is LeniencyVerdict.Granted)
        assertTrue(LeniencyVerdict.parse(response("denied")) is LeniencyVerdict.Denied)
    }

    @Test
    fun unrecognizedDecisionDoesNotLockOut() {
        // Anything the model invents must NOT be treated as a denial.
        listOf("MAYBE", "", "GRANTED_WITH_CONDITIONS", "0").forEach { decision ->
            val verdict = LeniencyVerdict.parse(response(decision))
            assertTrue(
                "decision '$decision' should be unparsable, was $verdict",
                verdict is LeniencyVerdict.Unparsable
            )
        }
    }

    @Test
    fun responseWithoutJsonIsUnparsable() {
        assertTrue(
            LeniencyVerdict.parse("I cannot comply with that request.")
                is LeniencyVerdict.Unparsable
        )
    }

    @Test
    fun malformedJsonIsUnparsable() {
        assertTrue(
            LeniencyVerdict.parse("""{ "decision": "GRANTED", """)
                is LeniencyVerdict.Unparsable
        )
    }

    @Test
    fun emptyResponseIsUnparsable() {
        assertTrue(LeniencyVerdict.parse("") is LeniencyVerdict.Unparsable)
    }

    @Test
    fun surroundingProseIsTolerated() {
        val verdict = LeniencyVerdict.parse(
            """Here is my verdict: { "decision": "DENIED", "response": "No." } Thank you."""
        )
        assertEquals(LeniencyVerdict.Denied("No."), verdict)
    }

    @Test
    fun missingMessageFallsBackToPlaceholder() {
        val verdict = LeniencyVerdict.parse("""{ "decision": "GRANTED" }""")
        assertEquals(LeniencyVerdict.Granted("NO STATEMENT ISSUED."), verdict)
    }
}
