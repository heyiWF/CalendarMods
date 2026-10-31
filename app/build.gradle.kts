import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

android {
    namespace = "com.calendarmods"
    compileSdk = 37
    defaultConfig {
        applicationId = providers.gradleProperty("calendarmods.applicationId").getOrElse("com.calendarmods")
        minSdk = 14
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "com.calendarmods.CompatAcceptance"
        // Android 4.0 has no vector drawable support: rasterise the bundled icons at build time.
        vectorDrawables.generatedDensities("mdpi", "hdpi", "xhdpi")
    }
    buildFeatures {
        // The compat branch renders with framework views only.
        buildConfig = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
dependencies {
    implementation("cn.6tail:tyme4j:1.5.1")
    testImplementation("junit:junit:4.13.2")
}
