package com.sharek.macromandate.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiConfigTest {
    private val huggingFace = "https://router.huggingface.co/"
    private val openRouter = "https://openrouter.ai/api/"

    @Test
    fun legacyKeyIsOnlyReadForHuggingFace() {
        assertEquals("api_key", ApiConfig.credentialPreferenceName(huggingFace))
        assertNotEquals("api_key", ApiConfig.credentialPreferenceName(openRouter))
        assertNotEquals(
            ApiConfig.credentialPreferenceName(openRouter),
            ApiConfig.credentialPreferenceName("https://proxy.example/")
        )
    }

    @Test
    fun providerKeysCannotBeUsedWithAnotherEndpoint() {
        assertTrue(ApiConfig.acceptsKey(huggingFace, "hf_test"))
        assertFalse(ApiConfig.acceptsKey(openRouter, "hf_test"))
        assertTrue(ApiConfig.acceptsKey(openRouter, " sk-or-v1-test "))
        assertFalse(ApiConfig.acceptsKey(huggingFace, "sk-or-v1-test"))
        assertFalse(ApiConfig.acceptsKey("https://proxy.example/", "sk-or-v1-test"))
        assertFalse(ApiConfig.acceptsKey(openRouter, "wrong-provider"))
        assertTrue(ApiConfig.acceptsKey(openRouter, ""))
    }

    @Test
    fun endpointIsCanonicalForCredentialStorage() {
        assertEquals(openRouter, ApiConfig.sanitizeAndValidateBaseUrl("https://OPENROUTER.AI:443/api", true))
    }
}
