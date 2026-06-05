import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Zip
import java.io.File

plugins {
    id("org.teavm") version "@{TeavmVersion}"
    kotlin("jvm")
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

val teavmVersion: String = "@{TeavmVersion}"

repositories {
    mavenCentral()
}

configurations {
    create("teavmLibs")
    create("teavmTooling")
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(fileTree(mapOf("dir" to "../core/libs", "include" to listOf("*.jar"))))
    implementation(project(":core"))

    implementation("org.teavm:teavm-classlib:$teavmVersion")
    implementation("org.teavm:teavm-jso:$teavmVersion")
    implementation("org.teavm:teavm-runtime:$teavmVersion")
    implementation("org.teavm:teavm-c:$teavmVersion")

    @{TeavmDependencies}

    add("teavmLibs", "org.teavm:teavm-classlib:$teavmVersion")
    add("teavmLibs", "org.teavm:teavm-jso:$teavmVersion")
    add("teavmLibs", "org.teavm:teavm-runtime:$teavmVersion")
    add("teavmLibs", "org.teavm:teavm-c:$teavmVersion")

    add("teavmTooling", "org.teavm:teavm-tooling:$teavmVersion")
    add("teavmTooling", "org.teavm:teavm-core:$teavmVersion")
}

tasks.register<Copy>("downloadTeavmLibs") {
    val outDir = file("libs/teavm")
    from(configurations.getByName("teavmLibs"))
    into(outDir)
    doFirst { if (!outDir.exists()) outDir.mkdirs() }
}

tasks.register<Copy>("downloadTeavmTooling") {
    val outDir = file("libs/teavm/tooling")
    from(configurations.getByName("teavmTooling"))
    into(outDir)
    doFirst { if (!outDir.exists()) outDir.mkdirs() }
}

tasks.matching { it.name == "classes" || it.name == "compileKotlin" || it.name == "compileJava" }.configureEach {
    dependsOn("downloadTeavmLibs")
}

teavm {
    target = "c"
    mainClass = project.findProperty("teavmMainClass")?.toString() ?: "@{ProjectPackage}.@{MainClass}"
    val defaultTarget = file("${buildDir}/teavm/c")
    targetDirectory = file(project.findProperty("teavmTargetDir")?.toString() ?: defaultTarget.absolutePath)
    minifying = false
    sourceMaps = false
    debugInformationGenerated = true
    optimizationLevel = "ADVANCED"
}

fun findJavaCmd(): String {
    val javaHome = System.getenv("JAVA_HOME")
    if (!javaHome.isNullOrBlank()) {
        val j = File(javaHome, "bin/java")
        if (j.exists()) return j.absolutePath
        val jexe = File(javaHome, "bin/java.exe")
        if (jexe.exists()) return jexe.absolutePath
    }
    return "java"
}

fun findGradleCmd(): String {
    val wrapper = File(project.rootDir, "gradlew")
    if (wrapper.exists()) return if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "./gradlew"
    return "gradle"
}

fun findExecutable(name: String): String? {
    val paths = System.getenv("PATH")?.split(File.pathSeparator) ?: return null
    for (p in paths) {
        val f = File(p, name)
        if (f.exists() && f.canExecute()) return f.absolutePath
        val fexe = File(p, "$name.exe")
        if (fexe.exists() && fexe.canExecute()) return fexe.absolutePath
    }
    return null
}

tasks.register("teavmCompile") {
    group = "teavm"
    description = "Compile project to TeaVM target (uses plugin task if available, otherwise runs TeaVM tooling)."
    dependsOn("downloadTeavmTooling", "downloadTeavmLibs")

    doLast {
        val pluginTask = tasks.findByName("teavm")
        if (pluginTask != null) {
            println("Invoking plugin-provided 'teavm' task...")
            project.exec {
                commandLine = listOf(findGradleCmd(), "teavm", "--no-daemon")
                workingDir = project.rootDir
            }.assertNormalExitValue()
            return@doLast
        }

        val toolingJars = configurations.getByName("teavmTooling").resolve()
        val cp = toolingJars.joinToString(File.pathSeparator) { it.absolutePath }
        val main = "org.teavm.tooling.TeaVMTool"
        val targetDir = project.findProperty("teavmTargetDir")?.toString() ?: "${buildDir}/teavm/c"
        val mainClass = project.findProperty("teavmMainClass")?.toString() ?: "@{ProjectPackage}.@{MainClass}"

        val args = listOf(
            "--target", "c",
            "--main-class", mainClass,
            "--target-dir", targetDir,
            "--optimization", (project.findProperty("teavmOptimization")?.toString() ?: "SIMPLE")
        )

        val javaCmd = listOf(findJavaCmd(), "-cp", cp, main) + args
        println("Running TeaVM tooling: ${javaCmd.joinToString(" ")}")
        val proc = ProcessBuilder(javaCmd).inheritIO().directory(project.rootDir).start()
        val exit = proc.waitFor()
        if (exit != 0) throw GradleException("TeaVM tooling failed with exit code $exit")
    }
}

tasks.register("buildNative") {
    group = "teavm"
    description = "Compile generated C sources into a native executable (uses gcc/clang)."
    dependsOn("teavmCompile")

    doLast {
        val nativeCompilerProp = project.findProperty("nativeCompiler")?.toString()
        val nativeCFlags = project.findProperty("nativeCFlags")?.toString() ?: "-O2 -std=c11"
        val targetDir = file(project.findProperty("teavmTargetDir")?.toString() ?: "${buildDir}/teavm/c")
        val outDir = file("${buildDir}/teavm/native")
        val outputName = project.findProperty("nativeOutputName")?.toString() ?: "teavm_app"

        if (!targetDir.exists()) throw GradleException("Teavm C sources not found in ${targetDir.absolutePath}. Run teavmCompile first.")
        if (!outDir.exists()) outDir.mkdirs()

        val compiler = when {
            !nativeCompilerProp.isNullOrBlank() -> nativeCompilerProp
            findExecutable("gcc") != null -> "gcc"
            findExecutable("clang") != null -> "clang"
            else -> null
        } ?: throw GradleException("No native C compiler found (gcc or clang). Set -PnativeCompiler to override.")

        val cFiles = targetDir.walkTopDown().filter { it.isFile && it.extension == "c" }.map { it.absolutePath }.toList()
        if (cFiles.isEmpty()) throw GradleException("No C source files found under ${targetDir.absolutePath}")

        val outputExe = File(outDir, outputName).absolutePath
        val cmd = mutableListOf<String>()
        cmd.add(compiler)
        cmd.addAll(nativeCFlags.split("\\s+".toRegex()).filter { it.isNotBlank() })
        cmd.addAll(cFiles)
        cmd.add("-o")
        cmd.add(outputExe)
        cmd.add("-lm")
        println("Compiling native executable: ${cmd.joinToString(" ")}")
        val proc = ProcessBuilder(cmd).inheritIO().directory(project.rootDir).start()
        val exit = proc.waitFor()
        if (exit != 0) throw GradleException("Native compilation failed with exit code $exit")
        println("Native executable created at $outputExe")
    }
}

tasks.register<Exec>("runNative") {
    group = "teavm"
    description = "Run the native executable produced by buildNative."
    dependsOn("buildNative")
    val outDir = file("${buildDir}/teavm/native")
    val outputName = project.findProperty("nativeOutputName")?.toString() ?: "teavm_app"
    val exe = File(outDir, outputName)
    doFirst {
        if (!exe.exists()) throw GradleException("Native executable not found at ${exe.absolutePath}. Run buildNative first.")
    }
    commandLine = listOf(exe.absolutePath)
    workingDir = project.rootDir
}

tasks.register<Zip>("packageNative") {
    group = "distribution"
    description = "Package native executable and teavm artifacts into a zip under build/distributions."
    dependsOn("buildNative")
    val outDir = file("$buildDir/distributions")
    destinationDirectory.set(outDir)
    archiveBaseName.set("${project.name}-native")
    from(file("${buildDir}/teavm/native")) { into("bin") }
    from(file("${buildDir}/teavm/c")) { into("csrc") }
    from(file("libs")) { into("libs") }
    doFirst { if (!outDir.exists()) outDir.mkdirs() }
}

tasks.register("testNative") {
    group = "verification"
    description = "Run native integration tests if available (placeholder)."
    dependsOn("buildNative")
    doLast {
        println("testNative: native executable built at ${buildDir}/teavm/native. Implement test harness as needed.")
    }
}

tasks.register<JavaExec>("runMainClass") {
    group = "application"
    description = "Run the project's main class on the JVM. Override with -PrunMainClass and -PjvmArgs."

    val mainProp = project.findProperty("runMainClass")?.toString()
    val defaultMain = project.findProperty("teavmMainClass")?.toString() ?: teavm.mainClass
    mainClass.set(mainProp ?: defaultMain)

    val sourceSets = the<org.gradle.api.tasks.SourceSetContainer>()
    classpath = sourceSets["main"].runtimeClasspath + files("libs")

    val jvmArgsProp = project.findProperty("jvmArgs")?.toString()
    if (!jvmArgsProp.isNullOrBlank()) {
        jvmArgs = jvmArgsProp.split("\\s+".toRegex()).filter { it.isNotBlank() }
    }

    val appArgsProp = project.findProperty("appArgs")?.toString()
    if (!appArgsProp.isNullOrBlank()) {
        args = appArgsProp.split("\\s+".toRegex()).filter { it.isNotBlank() }
    }

    dependsOn("downloadTeavmLibs")
}

tasks.register("buildAndRun") {
    group = "application"
    description = "Build project and run main class (depends on classes then runMainClass)."
    dependsOn("classes", "runMainClass")
}

