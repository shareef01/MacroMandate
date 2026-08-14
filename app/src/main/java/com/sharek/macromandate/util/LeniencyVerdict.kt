package com.sharek.macromandate.util

import org.json.JSONObject

/**
 * The model's verdict on a leniency plea.
 *
 * Both outcomes are irreversible — GRANTED wipes the entire meal log, DENIED locks
 * the app permanently — so anything that is not an explicit, recognized decision
 * must surface as [Unparsable] rather than falling through into the lockdown
 * branch. A response of "Granted" in the wrong case previously locked the user out.
 */
sealed class LeniencyVerdict {
    data class Granted(val message: String) : LeniencyVerdict()
    data class Denied(val message: String) : LeniencyVerdict()
    data class Unparsable(val reason: String) : LeniencyVerdict()

    companion object {
        private const val NO_STATEMENT = "NO STATEMENT ISSUED."

        fun parse(responseText: String): LeniencyVerdict {
            val start = responseText.indexOf('{')
            val end = responseText.lastIndexOf('}')
            if (start == -1 || end <= start) {
                return Unparsable("NO VERDICT FOUND IN RESPONSE.")
            }

            return try {
                val json = JSONObject(responseText.substring(start, end + 1))
                val message = json.optString("response").ifBlank { NO_STATEMENT }
                when (json.optString("decision").trim().uppercase()) {
                    "GRANTED" -> Granted(message)
                    "DENIED" -> Denied(message)
                    else -> Unparsable("UNRECOGNIZED VERDICT.")
                }
            } catch (e: Exception) {
                Unparsable("MALFORMED VERDICT.")
            }
        }
    }
}
