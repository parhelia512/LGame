import org.gradle.api.tasks.bundling.Jar
import java.io.File

plugins {
    java
    application
    distribution
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("@{ProjectPackage}.@{MainClass}")
}

val lwjglVersion: String = "@{LwjglVersion}"

repositories {
    mavenCentral()
}

dependencies {

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(fileTree(mapOf("dir" to "../core/libs", "include" to listOf("*.jar"))))
    implementation(project(":core"))

    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-opengl")
    implementation("org.lwjgl:lwjgl-stb")

    @{DesktopDependencies}

    runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-stb:$lwjglVersion:natives-windows")

    runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-stb:$lwjglVersion:natives-linux")

    runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:natives-macos")
    runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:natives-macos")
    runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:natives-macos")
    runtimeOnly("org.lwjgl:lwjgl-stb:$lwjglVersion:natives-macos")
}


tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Build executable fat jar that unpacks all runtime dependencies (including natives)"
    archiveClassifier.set("all")

    manifest {
        attributes(
            "Main-Class" to (project.findProperty("fatJarMainClass") ?: application.mainClass.get())
        )
    }

    from(sourceSets.main.get().output)

    from({
        configurations.runtimeClasspath.get().map { dependency ->
            if (dependency.isDirectory) dependency else zipTree(dependency)
        }
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}


tasks.register<JavaExec>("runMain") {
    group = "application"
    description = "Run the main class using runtimeClasspath (no explicit java.library.path set)"
    mainClass.set(project.findProperty("runMainClass")?.toString() ?: application.mainClass.get())
    classpath = sourceSets.main.get().runtimeClasspath + files("libs")
    workingDir = project.projectDir
}

distributions {
    main {
        distributionBaseName.set(project.name)
        contents {
            from(tasks.named("fatJar")) { into("lib") }
            from({
                configurations.runtimeClasspath.get().filter { it.exists() }
            }) { into("lib") }

            from(fileTree("libs").include("*.jar")) { into("lib") }
            from(fileTree("../core/libs").include("*.jar")) { into("lib") }
        }
    }
}

tasks.register("checkNativesInClasspath") {
    group = "verification"
    description = "Check whether LWJGL native jars for the current OS are present in the runtimeClasspath"

    doLast {
        val osName = System.getProperty("os.name").lowercase()
        val expectedClassifier = when {
            osName.contains("win") -> "natives-windows"
            osName.contains("mac") -> "natives-macos"
            osName.contains("nux") || osName.contains("nix") -> "natives-linux"
            else -> null
        }

        if (expectedClassifier == null) {
            println("Unsupported OS detected: $osName")
            return@doLast
        }

        val runtimeFiles = configurations.runtimeClasspath.get().files
        val found = runtimeFiles.any { it.name.contains(expectedClassifier) }

        println("Detected OS: $osName")
        println("Looking for native jars containing: $expectedClassifier")
        if (found) {
            println("Native jars for this OS are present on the runtimeClasspath.")
        } else {
            println("Native jars for this OS are NOT present on the runtimeClasspath.")
            println("Make sure runtimeOnly dependencies for $expectedClassifier are declared and resolved.")
        }
    }
}

tasks.register("buildFat") {
    group = "distribution"
    description = "Build the fat jar"
    dependsOn("fatJar")
}

tasks.register("packageAll") {
    group = "distribution"
    description = "Build distribution zip including lib/ with dependencies"
    dependsOn("fatJar", "distZip")
}

tasks.register("packageAndRun") {
    group = "application"
    description = "Package distribution and run the application"
    dependsOn("packageAll", "runMain")
}

tasks.clean {
    delete(file("build"))
}
