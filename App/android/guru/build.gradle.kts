plugins {
    id("com.android.application")
}
android {
    namespace = "com.guru.batteryoptimizer"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        applicationId = "com.guru.batteryoptimizer"
        minSdk = 33
        targetSdk = 35
        versionName = "4.0.1"
        versionCode = 40100
        ndk {
            abiFilters += "arm64-v8a"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    buildFeatures {
        viewBinding = false
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    project.afterEvaluate {
        tasks.named("assembleRelease") {
            doLast {
                val outputDir = file("${layout.buildDirectory.get()}/outputs/apk/release")
                val original = File(outputDir, "guru-release.apk").takeIf {it.exists()}?: outputDir.listFiles()?.firstOrNull {it.extension == "apk"}
                original?.renameTo(File(outputDir, "bgo-app-release.apk"))
            }
        }
        tasks.named("assembleDebug") {
            doLast {
                val outputDir = file("${layout.buildDirectory.get()}/outputs/apk/debug")
                val original = File(outputDir, "guru-debug.apk").takeIf {it.exists()}?: outputDir.listFiles()?.firstOrNull {it.extension == "apk"}
                original?.renameTo(File(outputDir, "bgo-app-debug.apk"))
            }
        }
    }
    signingConfigs {
        create("release") {
            val ksPath = System.getenv("KEYSTORE_PATH")
            if (!ksPath.isNullOrEmpty()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                println("✔️ APP will be signed for this BUILD!")
            } else {
                println("❌ APP will NOT be signed for this BUILD!")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles("proguard-debug.pro")
        }
    }
}
configurations.all {
    exclude(group = "sesl.androidx.picker", module = "picker-app")
    exclude(group = "sesl.androidx.picker", module = "picker-color")
}
dependencies {
    implementation("io.github.tribalfs:oneui-design:0.9.19+oneui8")
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    implementation("androidx.core:core-splashscreen:1.2.0") {
        exclude(group = "androidx.core", module = "core")
    }
}