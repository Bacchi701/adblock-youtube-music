plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.adblocker.ytmusic"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.adblocker.ytmusic"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Extiende AndroidJUnitRunner: las pruebas instrumentadas normales siguen funcionando.
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        // El emulador no da cifras estables; se permite medir en el a cambio de leerlas
        // como ordenes de magnitud comparativas, no como rendimiento del dispositivo real.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] =
            "EMULATOR,LOW-BATTERY,UNLOCKED,NOT-PROFILEABLE"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        /**
         * Variante para medir: no depurable (obligatorio para benchmark) y firmada con la
         * clave de debug para poder instalarla. Se deja sin R8 porque los microbenchmarks
         * referencian clases desde el APK de pruebas, que R8 no ve y eliminaria.
         */
        create("benchmark") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    // Los microbenchmarks deben correr sobre una variante no depurable.
    testBuildType = "benchmark"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    // La version que arrastra Compose no reconoce API 36 y Macrobenchmark no puede
    // instalar el perfil de arranque.
    implementation(libs.androidx.profileinstaller)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.benchmark.junit4)
    // Solo en la variante de medicion: `IsolationActivity` debe declararse en el APK de la
    // app bajo prueba, no en el de test. Nunca llega a release.
    "benchmarkImplementation"(libs.androidx.benchmark.junit4)
}
