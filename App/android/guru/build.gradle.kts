plugins {
    id("com.android.application")
}
android {
    namespace = "com.guru.batteryoptimizer"
    ndkVersion = "30.0.16248370"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        applicationId = "com.guru.batteryoptimizer"
        minSdk = 33
        targetSdk = 36
        versionName = "4.1"
        versionCode = 41000
        ndk {
            abiFilters += "arm64-v8a"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_24
        targetCompatibility = JavaVersion.VERSION_24
    }
    buildFeatures {
        viewBinding = false
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    project.afterEvaluate {
        listOf("release", "debug").forEach { type ->
            tasks.named("assemble${type.replaceFirstChar { it.uppercase() }}") {
                doLast {
                    val outputDir = file("${layout.buildDirectory.get()}/outputs/apk/$type")
                    val original = File(outputDir, "guru-$type.apk").takeIf {it.exists()}?: outputDir.listFiles()?.firstOrNull {it.extension == "apk"}
                    original?.renameTo(File(outputDir, "bgo-app-$type.apk"))
                }
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
    exclude(group = "androidx.core", module = "core")
    exclude(group = "androidx.core", module = "core-ktx")
    exclude(group = "androidx.customview", module = "customview")
    exclude(group = "androidx.coordinatorlayout", module = "coordinatorlayout")
    exclude(group = "androidx.drawerlayout", module = "drawerlayout")
    exclude(group = "androidx.viewpager2", module = "viewpager2")
    exclude(group = "androidx.viewpager", module = "viewpager")
    exclude(group = "androidx.appcompat", module = "appcompat")
    exclude(group = "androidx.fragment", module = "fragment")
    exclude(group = "androidx.preference", module = "preference")
    exclude(group = "androidx.slidingpanelayout", module = "slidingpanelayout")
    exclude(group = "androidx.swiperefreshlayout", module = "swiperefreshlayout")
    exclude(group = "com.google.android.material", module = "material")
}
dependencies {
    //sesl material
    implementation("sesl.com.google.android.material:material:1.14.0+1.0.47-sesl9+rev8")
    //sesl androidx
    implementation("sesl.androidx.appcompat:appcompat:1.8.0+1.0.38-sesl9+rev4")
    implementation("sesl.androidx.core:core:1.19.0+1.0.29-sesl9+rev3")
    implementation("sesl.androidx.fragment:fragment:1.9.0+1.0.6-sesl9+rev1")
    //others
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
}