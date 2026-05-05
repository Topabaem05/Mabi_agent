plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.guribbong.phoneappagent.runtime.litertlm"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-dsl"))
    implementation(project(":core-policy"))
    implementation(project(":core-runner"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
