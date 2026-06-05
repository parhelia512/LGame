plugins {
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

group = "@{ProjectPackage}"
version = "@{ProjectVersion}"

repositories {
    mavenCentral()
}

val gwtVersion = "@{GwtVersion}"

// 用于运行 Jetty Runner
configurations {
    create("gwt")
    create("jettyRunner")
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(fileTree(mapOf("dir" to "../core/libs", "include" to listOf("*.jar"))))
    implementation(project(":core"))
    "implementation"("com.google.gwt:gwt-user:$gwtVersion")
    "gwt"("com.google.gwt:gwt-dev:$gwtVersion")
    "jettyRunner"("org.eclipse.jetty:jetty-runner:9.4.55.v20240627")
}

tasks.register<JavaExec>("compileGwt") {
    group = "gwt"
    description = "Compile GWT module to JavaScript (output: build/gwt)"
    classpath = configurations.getByName("gwt")
    mainClass.set("com.google.gwt.dev.Compiler")
    args = listOf("@{GwtModule}", "-war", "$buildDir/gwt")
    jvmArgs = listOf("-Xmx1G")
}

tasks.register<Exec>("serveGwt") {
    group = "gwt"
    description = "Serve compiled GWT output on http://localhost:8000 (tries python/php/busybox)."
    dependsOn("compileGwt")

    val servePort = (project.findProperty("servePort") as? String)?.toIntOrNull() ?: 8000
    val serveHost = (project.findProperty("serveHost") as? String) ?: "0.0.0.0"
    val webRoot = file("$buildDir/gwt")

    doFirst {
        if (!webRoot.exists()) {
            throw GradleException("GWT output not found at ${webRoot.absolutePath}. Run compileGwt first.")
        }
        println("Serving ${webRoot.absolutePath} on http://$serveHost:$servePort (press Ctrl+C to stop)")
    }
   
    val adb = project.findProperty("adbPath") 

    fun findExecutable(name: String): String? {
        val paths = System.getenv("PATH")?.split(File.pathSeparator) ?: return null
        for (p in paths) {
            val f = File(p, name)
            if (f.exists() && f.canExecute()) return f.absolutePath
            // Windows .exe
            val fexe = File(p, "$name.exe")
            if (fexe.exists() && fexe.canExecute()) return fexe.absolutePath
        }
        return null
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
            throw GradleException(
                "No suitable static server found (python/php/busybox). " +
                        "Install python3 or php or busybox, or run your own HTTP server pointing to ${webRoot.absolutePath}."
            )
        }
    }
}

tasks.register<Exec>("runWebService") {
    group = "gwt"
    description = "Run compiled GWT output with embedded Jetty (jetty-runner). Use -PwebPort to override."
    dependsOn("compileGwt")

    val webPort = (project.findProperty("webPort") as? String)?.toIntOrNull() ?: 8080
    val webRoot = file("$buildDir/gwt")

    doFirst {
        if (!webRoot.exists()) {
            throw GradleException("GWT output not found at ${webRoot.absolutePath}. Run compileGwt first.")
        }
    }

    doLast {
        val runnerFiles = configurations.getByName("jettyRunner").resolvedConfiguration.resolvedArtifacts
        val runnerJar = runnerFiles.firstOrNull()?.file
        if (runnerJar == null || !runnerJar.exists()) {
            throw GradleException("jetty-runner not found in configuration 'jettyRunner'. Check network and dependencies.")
        }
        println("Starting Jetty Runner on http://localhost:$webPort serving ${webRoot.absolutePath}")
        val cmd = listOf("java", "-jar", runnerJar.absolutePath, "--port", webPort.toString(), webRoot.absolutePath)
        val pb = ProcessBuilder(cmd).inheritIO().directory(project.rootDir)
        val proc = pb.start()
        val exit = proc.waitFor()
        if (exit != 0) {
            throw GradleException("Jetty Runner exited with code $exit")
        }
    }
}

tasks.register("gwtTest") {
    group = "verification"
    description = "Run JVM tests (and GWT-related tests). Delegates to 'test' by default."
    dependsOn("test")
    doLast {
        println("gwtTest: delegated to 'test' task. Add GWT-specific test runner if needed.")
    }
}

tasks.register<Zip>("distGwt") {
    group = "distribution"
    description = "Package compiled GWT output into a zip (build/distributions)."
    dependsOn("compileGwt")
    val outDir = file("$buildDir/distributions")
    destinationDirectory.set(outDir)
    archiveBaseName.set("${project.name}-gwt")
    archiveVersion.set(project.version.toString())

    from(file("$buildDir/gwt")) { into("") }

    doFirst {
        if (!file("$buildDir/gwt").exists()) {
            throw GradleException("GWT output not found. Run compileGwt first.")
        }
        if (!outDir.exists()) outDir.mkdirs()
    }
}

tasks.register("serveAndOpen") {
    group = "gwt"
    description = "Compile GWT, serve it and optionally open the browser (use -PopenBrowser=true)."
    dependsOn("compileGwt", "serveGwt")
    doLast {
        val openBrowser = (project.findProperty("openBrowser") as? String)?.toBoolean() ?: false
        val port = (project.findProperty("servePort") as? String)?.toIntOrNull() ?: 8000
        if (openBrowser) {
            val url = "http://localhost:$port"
            println("Opening browser to $url")
            try {
                val os = System.getProperty("os.name").lowercase()
                when {
                    os.contains("mac") -> Runtime.getRuntime().exec(arrayOf("open", url))
                    os.contains("win") -> Runtime.getRuntime().exec(arrayOf("cmd", "/c", "start", url))
                    else -> Runtime.getRuntime().exec(arrayOf("xdg-open", url))
                }
            } catch (ex: Exception) {
                println("Failed to open browser automatically: ${ex.message}")
            }
        }
    }
}
