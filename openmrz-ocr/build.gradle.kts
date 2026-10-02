plugins {
    id("com.android.library")
    id("maven-publish")
}

group = System.getenv("GROUP") ?: "ir.mehrdad32.openmrz"
version = System.getenv("VERSION") ?: "0.2.0-SNAPSHOT"

android {
    namespace = "ir.mehrdad32.openmrz.ocr"
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

val fastTessdata = layout.projectDirectory.file(
    "src/main/assets/tessdata/mrz_fast.traineddata"
)
val bestTessdata = layout.projectDirectory.file(
    "src/main/assets/tessdata/mrz_best.traineddata"
)

tasks.register("prepareTessdata") {
    outputs.files(fastTessdata, bestTessdata)

    doLast {
        fun download(
            outputFile: File,
            sourceUrl: String,
            minBytes: Long,
        ) {
            if (!outputFile.exists() || outputFile.length() < minBytes) {
                outputFile.parentFile.mkdirs()
                uri(sourceUrl).toURL().openStream().use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }

        download(
            fastTessdata.asFile,
            "https://raw.githubusercontent.com/DoubangoTelecom/tesseractMRZ/master/tessdata_fast/mrz.traineddata",
            1_000_000L,
        )
        download(
            bestTessdata.asFile,
            "https://raw.githubusercontent.com/DoubangoTelecom/tesseractMRZ/master/tessdata_best/mrz.traineddata",
            10_000_000L,
        )
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
                    description.set("Offline dual-model MRZ OCR for Android.")
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
