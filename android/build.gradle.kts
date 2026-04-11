import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

val requiredJavaLanguageVersion = JavaLanguageVersion.of(17)

plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val verifyJava17Toolchain = tasks.register("verifyJava17Toolchain") {
    group = "verification"
    description = "Verifies that a Java 17 toolchain is available for Finalis Android and unit-test tasks."

    doLast {
        val currentJavaVersion = JavaVersion.current()
        if (currentJavaVersion != JavaVersion.VERSION_17) {
            throw GradleException(
                "finalis-mobile requires Gradle and Android tasks to run on Java 17. " +
                    "Current Gradle JVM: $currentJavaVersion. " +
                    "Install JDK 17 and point Gradle at it with JAVA_HOME or Android Studio Gradle JDK settings. " +
                    "Verify with `java -version` and `./gradlew -version`.",
            )
        }
        logger.lifecycle("Using Java 17 Gradle JVM at ${System.getProperty("java.home")}")
    }
}

subprojects {
    plugins.withId("java-base") {
        extensions.configure<JavaPluginExtension>("java") {
            toolchain {
                languageVersion.set(requiredJavaLanguageVersion)
            }
        }
    }

    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension>("android") {
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }

    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension>("android") {
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }

    plugins.withId("org.jetbrains.kotlin.android") {
        extensions.configure<KotlinAndroidProjectExtension>("kotlin") {
            jvmToolchain(17)
        }
        tasks.withType<KotlinJvmCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }

    tasks.withType<Test>().configureEach {
        dependsOn(verifyJava17Toolchain)
    }
}
