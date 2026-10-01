plugins {
    id("com.android.library")
    id("maven-publish")
}

group = System.getenv("GROUP") ?: "ir.mehrdad32.openmrz"
version = System.getenv("VERSION") ?: "0.1.0-SNAPSHOT"

android {
    namespace = "ir.mehrdad32.openmrz.android"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api(project(":openmrz-ocr"))
    api(libs.camera.view)
    api(libs.camera.lifecycle)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.androidx.core)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "openmrz-android"

                pom {
                    name.set("OpenMRZ Android")
                    description.set("CameraX MRZ scanner SDK for Android with offline OCR.")
                    url.set("https://github.com/Mehrdad32/OpenMRZ-Android")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    scm {
                        url.set("https://github.com/Mehrdad32/OpenMRZ-Android")
                    }
                }
            }
        }
    }
}
