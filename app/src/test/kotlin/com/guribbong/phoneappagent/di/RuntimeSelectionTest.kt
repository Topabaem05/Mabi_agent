package com.guribbong.phoneappagent.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.guribbong.phoneappagent.core.runner.LocalAgentRuntime
import com.guribbong.phoneappagent.runtime.litertlm.OpenRouterLocalAgentRuntime
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RuntimeSelectionTest {
    @Before
    fun resetKoinBefore() {
        stopKoin()
    }

    @After
    fun resetKoinAfter() {
        stopKoin()
    }

    @Test
    fun appModuleBindsEveryAgentSessionToOpenRouterRuntime() {
        startKoin {
            androidContext(ApplicationProvider.getApplicationContext<Context>())
            modules(appModule)
        }

        val runtime = GlobalContext.get().get<LocalAgentRuntime>()

        assertTrue(
            "LocalAgentRuntime must be OpenRouter-only; no LiteRT-LM fallback is allowed.",
            runtime is OpenRouterLocalAgentRuntime,
        )
    }
}
