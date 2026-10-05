plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Версия приходит из CI (счётчик), иначе 1.
val buildNumber: Int = (System.getenv("PSINA_BUILD_NUMBER") ?: "1").toIntOrNull() ?: 1

android {
    namespace = "ru.psina.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.psina.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        resourceConfigurations += listOf("ru", "en")
    }

    // Keystore лежит в корне проекта (android/keystore/...), поэтому путь
    // резолвим от rootProject, а не от каталога модуля app/.
    val keystoreFile = rootProject.file(System.getenv("PSINA_KEYSTORE") ?: "keystore/psina.jks")

    signingConfigs {
        create("release") {
            val ks = keystoreFile
            if (ks.exists()) {
                storeFile = ks
                storePassword = System.getenv("PSINA_KEYSTORE_PASSWORD") ?: "psina-launcher-2026"
                keyAlias = System.getenv("PSINA_KEY_ALIAS") ?: "psina"
                keyPassword = System.getenv("PSINA_KEY_PASSWORD") ?: "psina-launcher-2026"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "kotlin/**", "META-INF/DEPENDENCIES")
    }
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.documentfile:documentfile:1.0.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
