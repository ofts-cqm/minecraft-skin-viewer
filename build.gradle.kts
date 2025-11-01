plugins {
    kotlin("jvm") version Versions.KOTLIN
    kotlin("plugin.serialization") version Versions.KOTLIN
}

allprojects {
    apply(plugin = "org.gradle.maven-publish")
    apply(plugin = "org.gradle.java-library")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    group = Versions.GROUP
    version = Versions.VERSION

    kotlin {
        jvmToolchain(11)
    }

    tasks {
        test {
            enabled = false
            useJUnitPlatform()
            workingDir = projectDir.resolve("run")
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // slf4j
    implementation("org.slf4j:slf4j-api:2.0.17")
    // jfx
    api(javafx("controls", "win"))
    api(javafx("graphics", "win"))
    api(javafx("base", "win"))
    // test
    testImplementation(kotlin("test", Versions.KOTLIN))
}
