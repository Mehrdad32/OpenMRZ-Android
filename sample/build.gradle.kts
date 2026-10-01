plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.mehrdad32.openmrz.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.mehrdad32.openmrz.sample"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-alpha.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":openmrz-ocr"))
    implementation(libs.activity)
    implementation(libs.androidx.core)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
}
