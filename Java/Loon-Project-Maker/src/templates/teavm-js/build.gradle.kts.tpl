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
    create("jettyRunner")
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(fileTree(mapOf("dir" to "../core/libs", "include" to listOf("*.jar"))))
    implementation(project(":core"))

    implementation("org.teavm:teavm-classlib:$teavmVersion")
    implementation("org.teavm:teavm-jso:$teavmVersion")
    implementation("org.teavm:teavm-runtime:$teavmVersion")

    @{TeavmDependencies}

    compileOnly("org.teavm:teavm-tooling:$teavmVersion")
    compileOnly("org.teavm:teavm-core:$teavmVersion")
    compileOnly("org.teavm:teavm-jso-apis:$teavmVersion")
    compileOnly("org.teavm:teavm-jso-impl:$teavmVersion")
    compileOnly("jakarta.servlet:jakarta.servlet-api:6.0.0")

    add("teavmLibs", "org.teavm:teavm-classlib:$teavmVersion")
    add("teavmLibs", "org.teavm:teavm-jso:$teavmVersion")
    add("teavmLibs", "org.teavm:teavm-runtime:$teavmVersion")
    add("teavmLibs", "org.teavm:teavm-tooling:$teavmVersion")
    add("teavmLibs", "org.teavm:teavm-core:$teavmVersion")
    add("teavmLibs", "org.teavm:teavm-jso-apis:$teavmVersion")
    add("teavmLibs", "org.teavm:teavm-jso-impl:$teavmVersion")

    add("jettyRunner", "org.eclipse.jetty:jetty-runner:9.4.55.v20240627")
}

tasks.register<Copy>("downloadTeavmLibs") {
    val outDir = file("libs/teavm")
    from(configurations.getByName("teavmLibs"))
    into(outDir)
    doFirst { if (!outDir.exists()) outDir.mkdirs() }
}

tasks.matching { it.name == "classes" || it.name == "compileKotlin" || it.name == "compileJava" }.configureEach {
    dependsOn("downloadTeavmLibs")
}

teavm {
    target = "javascript"
    mainClass = project.findProperty("teavmMainClass")?.toString() ?: "@{ProjectPackage}.@{MainClass}"
    targetDirectory = file(project.findProperty("teavmTargetDir")?.toString() ?: "${buildDir}/generated/js/teavm")
    minifying = (project.findProperty("teavmMinify")?.toString()?.toBoolean() ?: false)
    incremental = (project.findProperty("teavmIncremental")?.toString()?.toBoolean() ?: false)
    debugInformationGenerated = true
    sourceMapsGenerated = true
    sourceFilesCopied = true
    optimizationLevel = project.findProperty("teavmOptimization")?.toString() ?: "SIMPLE"
    targetType = "JAVASCRIPT"
    properties["loon.genAssetsDirectory"] = "${buildDir}/generated/assets"
    properties["loon.warAssetsDirectory"] = "${projectDir}/src/main/webapp/assets"
    properties["loon.assetsPath"] = "${buildDir}/generated/assets.txt"
}

tasks.register("teavmCompile") {
    group = "teavm"
    description = "Run TeaVM compilation (plugin task if available, otherwise tooling)."
    dependsOn("downloadTeavmLibs")

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

        val toolingJars = configurations.getByName("teavmLibs").resolve()
        val cp = toolingJars.joinToString(File.pathSeparator) { it.absolutePath }
        val main = "org.teavm.tooling.TeaVMTool"
        val targetDir = project.findProperty("teavmTargetDir")?.toString() ?: "${buildDir}/generated/js/teavm"
        val mainClass = project.findProperty("teavmMainClass")?.toString() ?: "@{ProjectPackage}.@{MainClass}"

        val args = listOf(
            "--target", "javascript",
            "--main-class", mainClass,
            "--target-dir", targetDir,
            "--optimization", (project.findProperty("teavmOptimization")?.toString() ?: "SIMPLE"),
            "--source-maps", if ((project.findProperty("teavmSourceMaps")?.toString()?.toBoolean() ?: true)) "true" else "false"
        )

        val javaCmd = listOf(findJavaCmd(), "-cp", cp, main) + args
        println("Running TeaVM tooling: ${javaCmd.joinToString(" ")}")
        val proc = ProcessBuilder(javaCmd).inheritIO().directory(project.rootDir).start()
        val exit = proc.waitFor()
        if (exit != 0) throw GradleException("TeaVM tooling failed with exit code $exit")
    }
}

tasks.named("assemble") {
    dependsOn("teavmCompile")
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

tasks.register<Exec>("serveJs") {
    group = "teavm"
    description = "Serve compiled TeaVM JS output on http://localhost:8000 (tries python/php/busybox)."
    dependsOn("teavmCompile")

    val servePort = (project.findProperty("servePort") as? String)?.toIntOrNull() ?: 8000
    val serveHost = (project.findProperty("serveHost") as? String) ?: "0.0.0.0"
    val webRoot = file(project.findProperty("teavmTargetDir")?.toString() ?: "${buildDir}/generated/js/teavm")

    doFirst {
        if (!webRoot.exists()) throw GradleException("TeaVM output not found at ${webRoot.absolutePath}. Run teavmCompile first.")
        println("Serving ${webRoot.absolutePath} on http://$serveHost:$servePort (press Ctrl+C to stop)")
    }

    val python3 = findExecutable("python3")
    val python = findExecutable("python")
    val php = findExecutable("php")
    val busybox = findExecutable("busybox")

    if (python3 != null) {
        commandLine(python3, "-m", "http.server", servePort.toString(), "--bind", serveHost)
        workingDir = webRoot
    } else if (python != null) {
        commandLine(python, "-m", "SimpleHTTPServer", servePort.toString())
        workingDir = webRoot
    } else if (php != null) {
        commandLine(php, "-S", "$serveHost:$servePort", "-t", webRoot.absolutePath)
        workingDir = webRoot
    } else if (busybox != null) {
        commandLine(busybox, "httpd", "-f", "-p", servePort.toString(), "-h", webRoot.absolutePath)
        workingDir = webRoot
    } else {
        doFirst {
            throw GradleException("No suitable static server found (python/php/busybox). Install python3 or php or busybox.")
        }
    }
}

tasks.register("runWebService") {
    group = "teavm"
    description = "Run compiled TeaVM output with Jetty Runner (serves as webapp)."
    dependsOn("teavmCompile")

    doLast {
        val webRoot = file(project.findProperty("teavmTargetDir")?.toString() ?: "${buildDir}/generated/js/teavm")
        if (!webRoot.exists()) throw GradleException("TeaVM output not found at ${webRoot.absolutePath}. Run teavmCompile first.")

        val runnerJar = configurations.getByName("jettyRunner").resolvedConfiguration.resolvedArtifacts.firstOrNull()?.file
            ?: throw GradleException("jetty-runner not found in configuration 'jettyRunner'.")

        val webPort = (project.findProperty("webPort") as? String)?.toIntOrNull() ?: 8080
        println("Starting Jetty Runner on http://localhost:$webPort serving ${webRoot.absolutePath}")
        val cmd = listOf(findJavaCmd(), "-jar", runnerJar.absolutePath, "--port", webPort.toString(), webRoot.absolutePath)
        val pb = ProcessBuilder(cmd).inheritIO().directory(project.rootDir)
        val proc = pb.start()
        val exit = proc.waitFor()
        if (exit != 0) throw GradleException("Jetty Runner exited with code $exit")
    }
}

tasks.register<Zip>("packageWeb") {
    group = "distribution"
    description = "Package compiled TeaVM JS output and assets into a zip under build/distributions."
    dependsOn("teavmCompile")
    val outDir = file("$buildDir/distributions")
    destinationDirectory.set(outDir)
    archiveBaseName.set("${project.name}-web")
    archiveVersion.set(project.version.toString())
    from(file("${buildDir}/generated/js/teavm")) { into("web") }
    from(file("${buildDir}/generated/assets")) { into("web/assets") }
    doFirst { if (!outDir.exists()) outDir.mkdirs() }
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
    if (!jvmArgsProp.isNullOrBlank()) jvmArgs = jvmArgsProp.split("\\s+".toRegex()).filter { it.isNotBlank() }

    val appArgsProp = project.findProperty("appArgs")?.toString()
    if (!appArgsProp.isNullOrBlank()) args = appArgsProp.split("\\s+".toRegex()).filter { it.isNotBlank() }

    dependsOn("downloadTeavmLibs")
}

tasks.register("buildAndRun") {
    group = "application"
    description = "Build project and run main class (depends on classes then runMainClass)."
    dependsOn("classes", "runMainClass")
}
