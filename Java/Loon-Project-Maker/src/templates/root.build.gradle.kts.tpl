import java.io.File

plugins {
    id("java")
    id("application")
}

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
        google()
        maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
    }
}

allprojects {
    apply(plugin = "eclipse")
    apply(plugin = "idea")
    configure<org.gradle.plugins.ide.idea.model.IdeaModel> {
        module {
            outputDir = file("build/classes/java/main")
            testOutputDir = file("build/classes/java/test")
        }
    }
}
subprojects {

    apply(plugin = "java-library")

    val projectVersion: String = findProperty("projectVersion")?.toString() ?: "1.0.0"
    version = projectVersion

    extra["appName"] = "@{ProjectName}"

    repositories {
        mavenCentral()
        mavenLocal()
        maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
        maven { url = uri("https://jitpack.io") }
    }
    
    val assetsDir = project.rootDir.resolve("assets")
    
    tasks.register("generateAssetList") {
        doLast {
            if (assetsDir.exists()) {
                println("Assets directory: ${assetsDir.absolutePath}")
                assetsDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    println("Found asset: ${file.relativeTo(assetsDir)}")
                }
            } else {
                println("Assets directory not found.")
            }
        }
    }
   
    tasks.named("processResources") {
        dependsOn("generateAssetList")
    }
}

