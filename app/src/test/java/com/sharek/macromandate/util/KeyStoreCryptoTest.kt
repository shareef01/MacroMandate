package com.sharek.macromandate.util

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyStoreCryptoTest {

    @Test
    fun encryptAndDecryptEmptyStringReturnsEmpty() {
        assertEquals("", KeyStoreCrypto.encrypt(""))
        assertEquals("", KeyStoreCrypto.decrypt(""))
    }

    @Test
    fun encryptBlankStringReturnsEmpty() {
        assertEquals("", KeyStoreCrypto.encrypt("   "))
    }

    @Test
    fun decryptLegacyPlaintextReturnsUnmodified() {
        val legacyKey = "hf_0123456789abcdefABCDEF"
        assertEquals(legacyKey, KeyStoreCrypto.decrypt(legacyKey))
    }
}
