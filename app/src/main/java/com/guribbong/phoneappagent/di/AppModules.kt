package com.guribbong.phoneappagent.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.guribbong.phoneappagent.accessibility.AccessibilityStatusRepository
import com.guribbong.phoneappagent.accessibility.AndroidAccessibilityStatusRepository
import com.guribbong.phoneappagent.agent.AgentOrchestrator
import com.guribbong.phoneappagent.agent.AgentPowerController
import com.guribbong.phoneappagent.agent.AgentServiceController
import com.guribbong.phoneappagent.BuildConfig
import com.guribbong.phoneappagent.agent.InstalledAppCatalog
import com.guribbong.phoneappagent.core.policy.PolicyGate
import com.guribbong.phoneappagent.core.runner.LocalAgentRuntime
import com.guribbong.phoneappagent.driver.accessibility.AccessibilityDriver
import com.guribbong.phoneappagent.execution.AndroidAccessibilityDriver
import com.guribbong.phoneappagent.execution.CanonicalPackageResolver
import com.guribbong.phoneappagent.execution.PlanExecutor
import com.guribbong.phoneappagent.runtime.litertlm.OpenRouterLocalAgentRuntime
import com.guribbong.phoneappagent.runtime.litertlm.OpenRouterRuntimeConfig
import com.guribbong.phoneappagent.skills.AssetAppSkillResolver
import com.guribbong.phoneappagent.skills.SkillResolver
import com.guribbong.phoneappagent.ui.MainViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

private const val AGENT_SETTINGS_FILE = "agent_settings.preferences_pb"

data class AgentSettings(
    val modelName: String = BuildConfig.OPENROUTER_MODEL,
    val backend: String = "OpenRouter / ${BuildConfig.OPENROUTER_MODEL}",
    val overlayEnabled: Boolean = true,
)

interface SettingsRepository {
    val settings: Flow<AgentSettings>
    suspend fun setOverlayEnabled(enabled: Boolean)
}

private class DefaultSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    private val modelKey = stringPreferencesKey("model_name")
    private val backendKey = stringPreferencesKey("backend_name")
    private val overlayKey = booleanPreferencesKey("overlay_enabled")

    override val settings: Flow<AgentSettings> =
        dataStore.data.map { prefs ->
            AgentSettings(
                modelName = prefs[modelKey] ?: BuildConfig.OPENROUTER_MODEL,
                backend = prefs[backendKey] ?: "OpenRouter / ${BuildConfig.OPENROUTER_MODEL}",
                overlayEnabled = prefs[overlayKey] ?: true,
            )
        }

    override suspend fun setOverlayEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[overlayKey] = enabled
        }
    }
}

private fun providePreferencesDataStore(context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(AGENT_SETTINGS_FILE) },
    )

val appModule = module {
    single<DataStore<Preferences>> { providePreferencesDataStore(androidContext()) }
    single<SettingsRepository> { DefaultSettingsRepository(get()) }
    single<AccessibilityStatusRepository> { AndroidAccessibilityStatusRepository(androidContext()) }
    single { AgentPowerController(androidContext()) }
    single { CanonicalPackageResolver() }
    single<AccessibilityDriver> { AndroidAccessibilityDriver(androidContext(), get(), get()) }
    single { PolicyGate() }
    single<LocalAgentRuntime> {
        OpenRouterLocalAgentRuntime(
            androidContext(),
            get(),
            OpenRouterRuntimeConfig(
                apiKey = BuildConfig.OPENROUTER_API_KEY,
                modelName = BuildConfig.OPENROUTER_MODEL,
                endpoint = BuildConfig.OPENROUTER_ENDPOINT,
                appReferer = BuildConfig.OPENROUTER_REFERER,
                appTitle = BuildConfig.OPENROUTER_TITLE,
            ),
        )
    }
    single { InstalledAppCatalog(androidContext()) }
    single<SkillResolver> { AssetAppSkillResolver(androidContext()) }
    single { PlanExecutor(get()) }
    single { AgentOrchestrator(androidContext(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { AgentServiceController(androidContext()) }
    viewModel { MainViewModel(get(), get(), get(), get(), get(), get(), get()) }
}
