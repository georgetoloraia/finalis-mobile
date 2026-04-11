plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    buildToolsVersion = "36.0.0"
    namespace = "com.finalis.mobile.data.wallet"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:storage"))
    implementation(libs.androidx.security.crypto)
    testImplementation(kotlin("test"))
}
