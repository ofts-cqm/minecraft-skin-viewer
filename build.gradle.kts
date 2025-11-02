plugins {
    kotlin("jvm") version Versions.KOTLIN
    kotlin("plugin.serialization") version Versions.KOTLIN
    id("com.github.johnrengelman.shadow") version "7.1.2"
    application
}

kotlin {
    jvmToolchain(11)
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

allprojects {
    apply(plugin = "org.gradle.maven-publish")
    apply(plugin = "org.gradle.java-library")
    apply(plugin = "org.gradle.application")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "com.github.johnrengelman.shadow")

    group = Versions.GROUP
    version = Versions.VERSION

    repositories {
        mavenCentral()
    }

    dependencies {
        if (!name.startsWith("http-server-")) return@dependencies
        val os = name.removePrefix("http-server-")
        // impl
        implementation(project(":http-server")) {
            exclude("org.openjfx")
        }
        // skiko
        implementation(skiko(
            when (os) {
                "mac" -> "macos-x64"
                "win" -> "windows-x64"
                else -> "linux-x64"
            }
        ))
        // javafx
        implementation(javafx("controls", os))
        implementation(javafx("graphics", os))
        implementation(javafx("base", os))
    }

    application {
        mainClass.set("top.e404.skin.server.App")
        applicationDefaultJvmArgs = listOf(
            "-Dio.netty.tryReflectionSetAccessible=true",
            "--add-opens",
            "java.base/jdk.internal.misc=ALL-UNNAMED"
        )
    }

    tasks {
        runShadow {
            workingDir = rootDir.resolve("run")
            doFirst {
                if (workingDir.isFile) workingDir.delete()
                workingDir.mkdirs()
            }
        }

        shadowJar {
            archiveFileName.set("${project.name}.jar")
        }

        build {
            if (project.name.startsWith("http-server-")) {
                dependsOn(shadowJar)
            }
        }

        test {
            enabled = false
            useJUnitPlatform()
            workingDir = rootDir.resolve("run")
        }
    }
}
