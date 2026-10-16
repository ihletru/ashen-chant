plugins {
    // jak w referencyjnym module touchcontrols: bez kotlin-android (AGP 9 built-in)
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.beloko.touchcontrols"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
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
    // Beloko/nvllsvm Java layer (GPL) vendored z referencji manuelsiuro —
    // wymagany kontrakt: libtouchcontrols woła te klasy po imieniu z JNI.
    // Produktowe sterowanie dotykowe Heretic NIE korzysta z tego modułu.
    // Blob MOGA usunięty 14.10.2026 po decyzji Milosza: GamePadFragment.kt,
    // MogaHack.kt i ścieżki com.bda.controller.* wycięte, jar skasowany.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
}
