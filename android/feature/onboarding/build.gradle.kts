plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    buildToolsVersion = "36.0.0"
    namespace = "com.finalis.mobile.feature.onboarding"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}
