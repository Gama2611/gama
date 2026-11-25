import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// GAMA MDM: administrador de las tabletas de planta (propietario del dispositivo).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val versionGama = (findProperty("gamaVersion") as String?) ?: "0.1.0"
val partesVersion = versionGama.split(".").map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)

android {
    namespace = "com.gama.mdm"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gama.mdm"
        minSdk = 26
        targetSdk = 35
        versionCode = partesVersion[0] * 10000 + partesVersion[1] * 100 + partesVersion[2]
        versionName = versionGama
    }

    buildTypes {
        // Sin sufijo en debug: el nombre del paquete forma parte del comando de propietario del dispositivo.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation(libs.androidx.work.runtime.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.android.embedded)

    testImplementation(libs.junit)
}
