package com.guribbong.phoneappagent.runtime.litertlm

import android.content.Context
import android.content.ContextWrapper
import com.guribbong.phoneappagent.core.policy.PolicyGate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class OpenRouterRuntimeFailureTest {
    @Test
    fun missingApiKeyMarksRuntimeUnsupportedWithoutLocalFallback() = runBlocking {
        val runtime = OpenRouterLocalAgentRuntime(
            context = RuntimeTestContext(),
            policyGate = PolicyGate(),
            config = OpenRouterRuntimeConfig(
                apiKey = "",
                modelName = "google/gemma-4-26b-a4b-it",
                endpoint = "https://openrouter.ai/api/v1/chat/completions",
                appReferer = "https://example.com",
                appTitle = "Phone App Agent Tests",
            ),
        )

        val profile = runtime.probeDeviceCapability()
        val preparation = runtime.prepare(profile)

        assertFalse(profile.supported)
        assertEquals("OPENROUTER_API_KEY is missing.", profile.reason)
        assertFalse(preparation.ready)
        assertEquals("OPENROUTER_API_KEY is missing.", preparation.detail)
    }

    private class RuntimeTestContext : ContextWrapper(null) {
        private val testFilesDir = File(System.getProperty("java.io.tmpdir"), "openrouter-runtime-test").also {
            it.mkdirs()
        }

        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File = testFilesDir

        override fun getSystemService(name: String): Any? = null
    }
}
