plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "vn.langkinhhukhong.tts"
    compileSdk = 34

    defaultConfig {
        applicationId = "vn.langkinhhukhong.tts"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        noCompress += listOf("onnx", "json", "txt")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Jetpack Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // AndroidX Media3 (ExoPlayer + Transformer + Effect)
    val media3Version = "1.4.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")
    implementation("androidx.media3:media3-transformer:$media3Version")
    implementation("androidx.media3:media3-effect:$media3Version")

    // Google AdMob SDK (Kiếm tiền từ Quảng cáo: Rewarded Video, Interstitial, Banner)
    implementation("com.google.android.gms:play-services-ads:23.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Thư mục libs chứa sherpa-onnx.aar
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
}

// Tự động đồng bộ 2 model mặc định (minhkhang, ngochuyenupdate) từ thư mục Voices/ vào assets/models/ khi build APK
val syncBuiltInVoices by tasks.registering(Copy::class) {
    val rootVoicesDir = rootProject.file("Voices")
    if (rootVoicesDir.exists()) {
        from(rootVoicesDir) {
            include("minhkhang/**")
            include("ngochuyenupdate/**")
            include("lacphi/*.json", "lacphi/*.txt")
            include("minhquang/*.json", "minhquang/*.txt")
            include("VPBank/*.json", "VPBank/*.txt")
        }
        into(project.file("src/main/assets/models"))
    }
}

tasks.named("preBuild") {
    dependsOn(syncBuiltInVoices)
}

