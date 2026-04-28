plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-dsl"))
    implementation(project(":core-policy"))
    implementation(libs.kotlinx.coroutines.core)
}
