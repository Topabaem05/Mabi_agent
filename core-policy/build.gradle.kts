plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-dsl"))

    testImplementation("junit:junit:4.13.2")
}
