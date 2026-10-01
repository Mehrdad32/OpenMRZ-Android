plugins {
    id("com.android.application")
}

android {
    namespace = "ir.mehrdad32.openmrz.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "ir.mehrdad32.openmrz.sample"
        minSdk = 23
        targetSdk = 36
        versionCode = 3
        versionName = (System.getenv("VERSION") ?: "v0.2.0-beta.1-dev").removePrefix("v")
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
    implementation(project(":openmrz-android"))
    implementation(libs.activity)
    implementation(libs.androidx.core)
}
