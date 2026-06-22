plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
}

android {
    namespace = "com.jtr.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jtr.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 79
        versionName = "7.1.25"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME : version affichée dynamiquement dans les Paramètres
        // (évite les divergences entre locales d'une valeur codée en dur).
        buildConfig = true
    }
    kotlinOptions { jvmTarget = "17" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        jniLibs {
            // Stocke les .so non compressés dans l'APK pour préserver
            // l'alignement 16 KB exigé par Android 15 (API 35+).
            useLegacyPackaging = false
        }
    }

    // v7.1.14 — LANGUE IN-APP FIABLE SUR AAB (audit M1). Le Play Store impose l'App
    // Bundle, qui par défaut SCINDE les ressources par langue : seules les langues de
    // l'appareil sont livrées à l'installation. Or JTR change de langue via
    // LocaleManager.wrap() (createConfigurationContext, mécanisme maison) et NON via
    // l'API système — Play ne télécharge donc pas le split d'une langue non installée
    // → une des 13 langues choisie in-app retombait silencieusement en anglais
    // (invisible en APK debug, qui embarque tout ; signalé par lint AppBundleLocaleChanges).
    // enableSplit = false force les 13 langues dans l'APK de base → la bascule in-app
    // fonctionne quelle que soit la langue de l'appareil.
    bundle {
        language {
            enableSplit = false
        }
    }

    // Les schémas Room exportés ($projectDir/schemas) sont packagés dans les assets
    // du test APK pour que MigrationTestHelper puisse les charger au runtime.
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }
}

// Exporte le schéma Room (JSON versionné) — requis par exportSchema = true et par
// le test de migration. Sans cet argument KSP, Room émet un warning de build.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    // lifecycle-process : ProcessLifecycleOwner pilote le re-verrouillage au niveau
    // PROCESS (un recreate()/changement de config ne déclenche pas son ON_STOP).
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Retrofit + OkHttp (API externe — géocodage Nominatim)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // WorkManager (vérifications périodiques)
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Google Play Services — Location & Geofencing
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // DataStore (préférences)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Sécurité locale (v6.2.0) : stockage chiffré des empreintes (hash salés) du
    // schéma + code de secours, et déverrouillage biométrique. 100 % local.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")

    // MapLibre (carte native) — 11.5.0 : premier release avec support 16 KB pages (PR #2852)
    implementation("org.maplibre.gl:android-sdk:11.5.0")

    // Réordonnancement par glisser fiable (drag-and-drop Compose éprouvé) — sections de notes
    implementation("sh.calvin.reorderable:reorderable:2.4.3")

    // Gson + Coil
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // EXIF (orientation réelle des photos importées — repli pré-API 28 du crop)
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Tests unitaires
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("app.cash.turbine:turbine:1.2.0")

    // Tests instrumentés (Room DAO)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
}
