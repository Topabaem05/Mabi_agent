fun loadDotEnv(): Map<String, String> {
    val envFile = rootProject.file(".env")
    if (!envFile.exists()) return emptyMap()
    return buildMap {
        envFile.forEachLine { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith("#") || "=" !in line) return@forEachLine
            val (key, value) = line.split("=", limit = 2)
            put(key.trim(), value.trim().removeSurrounding("\""))
        }
    }
}

fun escapeBuildConfig(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

val dotEnv = loadDotEnv()
val openRouterApiKey = dotEnv["OPENROUTER_API_KEY"] ?: System.getenv("OPENROUTER_API_KEY").orEmpty()
val openRouterModel = "google/gemma-4-26b-a4b-it"
val openRouterEndpoint = "https://openrouter.ai/api/v1/chat/completions"
val openRouterReferer = "https://phone-app-agent.local"
val openRouterTitle = "phone_app_agent"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.guribbong.phoneappagent"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.guribbong.phoneappagent"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "OPENROUTER_API_KEY", "\"${escapeBuildConfig(openRouterApiKey)}\"")
        buildConfigField("String", "OPENROUTER_MODEL", "\"${escapeBuildConfig(openRouterModel)}\"")
        buildConfigField("String", "OPENROUTER_ENDPOINT", "\"${escapeBuildConfig(openRouterEndpoint)}\"")
        buildConfigField("String", "OPENROUTER_REFERER", "\"${escapeBuildConfig(openRouterReferer)}\"")
        buildConfigField("String", "OPENROUTER_TITLE", "\"${escapeBuildConfig(openRouterTitle)}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-dsl"))
    implementation(project(":core-policy"))
    implementation(project(":core-runner"))
    implementation(project(":runtime-litertlm"))
    implementation(project(":driver-accessibility"))
    implementation(project(":overlay-ui"))
    implementation(project(":skill-registry"))
    implementation(project(":data-history"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation("androidx.test:core:1.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")

    debugImplementation(libs.androidx.compose.ui.tooling)
}
