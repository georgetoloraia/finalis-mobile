plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    buildToolsVersion = "36.0.0"
    namespace = "com.finalis.mobile.data.lightserver"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:crypto"))
    implementation(project(":core:wallet"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
}
