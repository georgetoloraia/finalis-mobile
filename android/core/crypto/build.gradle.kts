plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.bouncycastle.bcprov)
    testImplementation(kotlin("test"))
}
