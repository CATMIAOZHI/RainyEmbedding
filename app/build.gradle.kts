import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ★ 从 local.properties 或环境变量读取签名凭据（不硬编码）
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
fun signingProp(name: String): String {
    return localProperties.getProperty(name)
        ?: System.getenv(name) ?: ""
}

android {
    namespace = "com.rainyembedding.app"
    compileSdk = 35

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("release.jks")
            storePassword = signingProp("RELEASE_STORE_PASSWORD")
            keyAlias = signingProp("RELEASE_KEY_ALIAS")
            keyPassword = signingProp("RELEASE_KEY_PASSWORD")
        }
    }

    defaultConfig {
        applicationId = "com.rainyembedding.app"
        minSdk = 31  // NPU requires API 31+
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // NPU only supports arm64-v8a
        ndk { abiFilters.add("arm64-v8a") }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isJniDebuggable = true
        }
        release {
            signingConfig = signingConfigs.getByName("release")
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
        // minSdk=31，java.time 原生支持；desugaring 保留以兼容潜在降级
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Needed for Qualcomm NPU runtime libraries
        jniLibs {
            useLegacyPackaging = true
            // ★ Proot 环境下 AGP strip 工具链不兼容，禁止 strip native 库
            keepDebugSymbols.add("**/*.so")
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// Force use of ARM64 binaries for AAPT2 in Proot environment (only on aarch64)
if (System.getProperty("os.arch") == "aarch64") {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.android.tools.build" && requested.name == "aapt2") {
                useTarget("com.android.tools.build:aapt2:${requested.version}:linux-aarch64")
            }
        }
    }
}

dependencies {
    // Desugaring (for java.time on API < 26)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    
    // LiteRT CompiledModel API（Embedding 推理）
    implementation(libs.litert)
    // HTTP 服务器
    implementation(libs.nanohttpd)
    // 协程
    implementation(libs.kotlinx.coroutines.android)
    // Navigation Compose
    implementation(libs.androidx.navigation.compose)
    // DataStore
    implementation(libs.androidx.datastore.preferences)
    // 图片加载
    implementation(libs.coil.compose)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
