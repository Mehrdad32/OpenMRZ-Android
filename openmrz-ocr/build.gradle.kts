plugins {
    id("com.android.library")
    id("maven-publish")
}

group = System.getenv("GROUP") ?: "io.github.mehrdad32.openmrz"
version = System.getenv("VERSION") ?: "0.1.0-SNAPSHOT"

android {
    namespace = "io.github.mehrdad32.openmrz.ocr"
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
    api(project(":openmrz-core"))
    implementation(libs.tesseract4android)

    testImplementation("junit:junit:4.13.2")
}

val tessdataFile = layout.projectDirectory.file("src/main/assets/tessdata/mrz.traineddata")

tasks.register("prepareTessdata") {
    outputs.file(tessdataFile)
    doLast {
        val output = tessdataFile.asFile
        if (!output.exists() || output.length() < 10_000_000L) {
            output.parentFile.mkdirs()
            val url = uri(
                "https://raw.githubusercontent.com/DoubangoTelecom/tesseractMRZ/master/tessdata_best/mrz.traineddata"
            ).toURL()
            url.openStream().use { input ->
                output.outputStream().use { outputStream ->
                    input.copyTo(outputStream)
                }
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn("prepareTessdata")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "openmrz-ocr"

                pom {
                    name.set("OpenMRZ OCR")
                    description.set("Offline MRZ-trained Tesseract OCR for Android.")
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
