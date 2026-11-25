import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Versión: el workflow de instaladores la pasa con -PgamaVersion=X.Y.Z (misma que el servidor).
val versionGama = (findProperty("gamaVersion") as String?) ?: "0.1.0"
val partesVersion = versionGama.split(".").map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)

android {
    namespace = "com.gama.suite"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gama.suite"
        minSdk = 26
        targetSdk = 35
        // X.Y.Z → X·10000 + Y·100 + Z: crece con cada versión, como exige Android para actualizar.
        versionCode = partesVersion[0] * 10000 + partesVersion[1] * 100 + partesVersion[2]
        versionName = versionGama
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "variante"
    productFlavors {
        create("movil") {
            dimension = "variante"
            buildConfigField("boolean", "TERMINAL", "false")
            resValue("string", "app_name", "GAMA")
        }
        create("terminal") {
            dimension = "variante"
            applicationIdSuffix = ".terminal"
            versionNameSuffix = "-terminal"
            buildConfigField("boolean", "TERMINAL", "true")
            resValue("string", "app_name", "GAMA Terminal")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Firma: el workflow inyecta el keystore desde los secretos de GitHub.
            val ruta = System.getenv("GAMA_KEYSTORE")
            if (ruta != null) {
                signingConfig = signingConfigs.create("gama") {
                    storeFile = file(ruta)
                    storePassword = System.getenv("GAMA_KEYSTORE_CLAVE")
                    keyAlias = System.getenv("GAMA_KEY_ALIAS")
                    keyPassword = System.getenv("GAMA_KEY_CLAVE")
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":comun"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
