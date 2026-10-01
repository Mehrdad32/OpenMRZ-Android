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
        versionCode = 2
        versionName = (System.getenv("VERSION") ?: "v0.1.0-alpha.2-dev").removePrefix("v")
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
