plugins {
    id("org.jetbrains.kotlin.jvm")
    id("maven-publish")
}

group = "io.github.mehrdad32"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "openmrz-core"
        }
    }
}
