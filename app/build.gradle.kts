plugins {
    // UWAGA: org.jetbrains.kotlin.android NIE jest aplikowane celowo — AGP 9 ma
    // wbudowane wsparcie Kotlina, a podwójna aplikacja plugina koliduje
    // ("Cannot add extension with name 'kotlin'"). Kompilację Kotlina włącza
    // plugin compose (jego aplikacja wymaga Kotlina). Wzorzec z referencji.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ashenchant.game"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ashenchant.game"
        // minSdk 33 = floor aplikacji (jak w stosie referencyjnym); native
        // APP_PLATFORM w jni/Application.mk jest android-23 — to osobna sprawa.
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Podpis release — klucz z app/release-keystore.jks (gitignorowany), dane z
    // keystore.properties (też gitignorowane). Konwencja 1:1 jak w Verbigemie.
    // Play App Signing przejmuje klucz podpisujący w Sklepie; ten klucz to tylko
    // klucz UPLOADU (można go zresetować w konsoli, ale bez niego nie wydasz
    // aktualizacji, dopóki tego nie zrobisz).
    signingConfigs {
        create("release") {
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) {
                val map = propsFile.readLines()
                    .filter { it.isNotBlank() && "=" in it }
                    .associate { line ->
                        val (k, v) = line.split("=", limit = 2)
                        k.trim() to v.trim()
                    }
                storeFile = rootProject.file(map["storeFile"]!!)
                storePassword = map["storePassword"]!!
                keyAlias = map["keyAlias"]!!
                keyPassword = map["keyPassword"]!!
            }
        }
    }

    buildTypes {
        release {
            // ⚠️ Świadomie BEZ `isMinifyEnabled` (inaczej niż w Verbigemie):
            // APK to w ~95% biblioteki natywne i WAD Blasphemera, więc R8 nie ma
            // tu czego urwać, a ryzyko jest realne — warstwa dotykowa wchodzi do
            // kodu przez JNI i odbicia. Włączymy, gdy będzie powód i czas na
            // przeklikanie ekranów na urządzeniu.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    sourceSets {
        getByName("main") {
            // .so nie są tu budowane — Gradle tylko pakuje wynik scripts/build-native.sh.
            jniLibs.srcDirs("src/main/libs")
        }
    }

    packaging {
        jniLibs {
            // Niekompresowany, stronicowany export .so — wymóg stron 16 KB (Android 15+).
            useLegacyPackaging = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":opentouch-input"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Wymagane przez vendored UtilsSAF (SAF adresacja plików) — tak jak w
    // referencyjnym subprojekcie SAFFAL (wersja 1.0.1).
    implementation(libs.androidx.documentfile)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
