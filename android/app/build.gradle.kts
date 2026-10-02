import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersion = file("../version.txt").readText().trim()
val sherpaVersion = "1.13.8"
val sherpaAar = layout.projectDirectory.file("libs/sherpa-onnx.aar").asFile

// sherpa-onnx 안드로이드 AAR (온디바이스 음성 인식 / 화자 구분) — 저장소에 넣지 않고 빌드 때 내려받는다.
val downloadSherpa by tasks.registering {
    outputs.file(sherpaAar)
    onlyIf { !sherpaAar.exists() || sherpaAar.length() < 1_000_000 }
    doLast {
        sherpaAar.parentFile.mkdirs()
        val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar"
        logger.lifecycle("Downloading $url")
        URI(url).toURL().openStream().use { input ->
            sherpaAar.outputStream().use { input.copyTo(it) }
        }
    }
}
tasks.named("preBuild") { dependsOn(downloadSherpa) }
// AAR 이 소비되는 첫 작업보다 먼저 내려받도록 보장
tasks.matching { it.name.endsWith("AarMetadata") || it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }
    .configureEach { dependsOn(downloadSherpa) }

android {
    namespace = "com.docvoice.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.docvoice.app"
        minSdk = 29
        targetSdk = 35
        versionName = appVersion
        versionCode = appVersion.split('.').let { (a, b, c) -> a.toInt() * 10000 + b.toInt() * 100 + c.toInt() }
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/docvoice.jks")
            storePassword = "docvoice"
            keyAlias = "docvoice"
            keyPassword = "docvoice"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    sourceSets["main"].java.srcDir("src/main/kotlin")
    sourceSets["test"].java.srcDir("src/test/kotlin")

    packaging {
        resources.excludes += setOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        jniLibs.useLegacyPackaging = true
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(files("libs/sherpa-onnx.aar"))

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("org.apache.commons:commons-compress:1.27.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
