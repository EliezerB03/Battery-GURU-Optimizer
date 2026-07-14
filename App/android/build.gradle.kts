buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.2.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}