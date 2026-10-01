plugins {
    id("org.jetbrains.kotlin.jvm")
    id("maven-publish")
}

group = System.getenv("GROUP") ?: "io.github.mehrdad32.openmrz"
version = System.getenv("VERSION") ?: "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.14.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "openmrz-core"

            pom {
                name.set("OpenMRZ Core")
                description.set("Pure JVM ICAO MRZ parser and check-digit validator.")
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
