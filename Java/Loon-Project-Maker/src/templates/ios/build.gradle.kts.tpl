import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Copy
import java.io.File

plugins {
    kotlin("multiplatform")
    id("com.mobidevelop.robovm") version "@{RoboVMVersion}"
}

val roboVmVersion = "@{RoboVMVersion}"

kotlin {
    ios()
    sourceSets {
        val iosMain by getting {
            dependencies {
                @{IosDependencies}
            }
        }
    }
}

repositories {
    mavenCentral()
}

configurations {
    create("robovmLibs")
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(fileTree(mapOf("dir" to "../core/libs", "include" to listOf("*.jar"))))
    implementation(project(":core"))
    "robovmLibs"("com.mobidevelop.robovm:robovm-rt:$roboVmVersion")
    "robovmLibs"("com.mobidevelop.robovm:robovm-cocoatouch:$roboVmVersion")
    "robovmLibs"("com.mobidevelop.robovm:robovm-objc:$roboVmVersion")
    @{IosDependencies}
}

tasks.register<Copy>("downloadRoboVMLibs") {
    val outDir = file("libs/robovm")
    from(configurations.getByName("robovmLibs"))
    into(outDir)
    doFirst {
        if (!outDir.exists()) outDir.mkdirs()
    }
}

tasks.register<Copy>("extractRoboVMLibs") {
    val nativeOut = file("libs/robovm/native")
    val jars = fileTree("libs/robovm") { include("*.jar") }
    from(jars.map { zipTree(it) })
    into(nativeOut)
    doFirst {
        if (!nativeOut.exists()) nativeOut.mkdirs()
    }
}

tasks.register("cleanRobovm") {
    group = "robovm"
    description = "Clean RoboVM generated artifacts and downloaded libs"
    doLast {
        file("libs/robovm").takeIf { it.exists() }?.deleteRecursively()
        file("build/robovm").takeIf { it.exists() }?.deleteRecursively()
        println("cleanRobovm: cleaned libs/robovm and build/robovm")
    }
}

fun isMacOs(): Boolean = System.getProperty("os.name").lowercase().contains("mac")

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

tasks.register("runIosSimulator") {
    group = "robovm"
    description = "Build and run the app on iOS Simulator (macOS only). Use -PsimulatorName='iPhone 14' to override."
    dependsOn("downloadRoboVMLibs")
    dependsOn("extractRoboVMLibs")

    doLast {
        if (!isMacOs()) {
            throw GradleException("runIosSimulator is only supported on macOS.")
        }
        val simulatorName = project.findProperty("simulatorName")?.toString() ?: "iPhone 14"
        val robovmTaskName = if (tasks.findByName("robovmIosSimulator") != null) "robovmIosSimulator" else "robovm"
        println("Building for simulator using task: $robovmTaskName")
        val gradleCmd = if (File(project.rootDir, "gradlew").exists()) {
            if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "./gradlew"
        } else "gradle"
        val buildProc = ProcessBuilder(gradleCmd, robovmTaskName, "-x", "test")
            .directory(project.rootDir)
            .inheritIO()
            .start()
        val buildExit = buildProc.waitFor()
        if (buildExit != 0) throw GradleException("RoboVM build for simulator failed (exit $buildExit).")

        val simDir = file("build/robovm/ios-sim")
        val appBundle = simDir.walkTopDown().firstOrNull { it.isDirectory && it.name.endsWith(".app") }
        if (appBundle == null) {
            println("Warning: .app bundle not found under ${simDir.absolutePath}. You may need to adjust robovm output path.")
            return@doLast
        }

        // 启动或安装到指定模拟器
        val xcrun = findExecutable("xcrun") ?: throw GradleException("xcrun not found in PATH. Xcode command line tools required.")
        // 启动模拟器
        ProcessBuilder(xcrun, "simctl", "bootstatus", simulatorName)
            .directory(project.rootDir)
            .inheritIO()
            .start()
            .waitFor()

        // 安装并启动
        val installCmd = listOf(xcrun, "simctl", "install", "booted", appBundle.absolutePath)
        println("Installing to simulator: ${installCmd.joinToString(" ")}")
        val installProc = ProcessBuilder(installCmd).inheritIO().start()
        val installExit = installProc.waitFor()
        if (installExit != 0) throw GradleException("Failed to install app to simulator (exit $installExit).")

        // 启动主bundle id
        val plist = File(appBundle, "Info.plist")
        var bundleId: String? = null
        if (plist.exists()) {
            try {
                val text = plist.readText()
                val regex = Regex("<key>CFBundleIdentifier</key>\\s*<string>([^<]+)</string>")
                val match = regex.find(text)
                bundleId = match?.groups?.get(1)?.value
            } catch (_: Exception) { /* ignore */ }
        }
        if (bundleId != null) {
            val launchCmd = listOf(xcrun, "simctl", "launch", "booted", bundleId)
            println("Launching app on simulator: ${launchCmd.joinToString(" ")}")
            val launchProc = ProcessBuilder(launchCmd).inheritIO().start()
            val launchExit = launchProc.waitFor()
            if (launchExit != 0) println("Warning: failed to launch app on simulator (exit $launchExit).")
        } else {
            println("Bundle identifier not found; app installed but not launched.")
        }
    }
}

/* 运行到iOS真机 */
tasks.register("runIosDevice") {
    group = "robovm"
    description = "Build and install the app to a connected iOS device. Use -PdeviceId=<udid> to target a device."
    dependsOn("downloadRoboVMLibs")
    dependsOn("extractRoboVMLibs")

    doLast {
        if (!isMacOs()) {
            throw GradleException("runIosDevice is only supported on macOS.")
        }
        val deviceId = project.findProperty("deviceId")?.toString()
        val gradleCmd = if (File(project.rootDir, "gradlew").exists()) {
            if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "./gradlew"
        } else "gradle"
        val robovmTaskName = if (tasks.findByName("robovmIosDevice") != null) "robovmIosDevice" else "robovm"
        println("Building for device using task: $robovmTaskName")
        val buildProc = ProcessBuilder(gradleCmd, robovmTaskName, "-x", "test")
            .directory(project.rootDir)
            .inheritIO()
            .start()
        val buildExit = buildProc.waitFor()
        if (buildExit != 0) throw GradleException("RoboVM build for device failed (exit $buildExit).")

        // 查找生成的 ipa 或 app bundle
        val deviceOut = file("build/robovm/ios-device")
        val ipa = deviceOut.walkTopDown().firstOrNull { it.isFile && it.extension == "ipa" }
        val appBundle = deviceOut.walkTopDown().firstOrNull { it.isDirectory && it.name.endsWith(".app") }

        val iosDeploy = findExecutable("ios-deploy")
        val ideviceinstaller = findExecutable("ideviceinstaller")

        if (ipa != null && iosDeploy != null) {
            val cmd = mutableListOf(iosDeploy, "--bundle", ipa.absolutePath)
            if (!deviceId.isNullOrBlank()) cmd.addAll(listOf("--id", deviceId))
            println("Installing IPA using ios-deploy: ${cmd.joinToString(" ")}")
            val proc = ProcessBuilder(cmd).inheritIO().start()
            val exit = proc.waitFor()
            if (exit != 0) throw GradleException("ios-deploy failed with exit code $exit")
            println("IPA installed successfully.")
            return@doLast
        }

        if (appBundle != null && ideviceinstaller != null) {
            // ideviceinstaller expects an ipa; try to create a temporary ipa if necessary
            println("Found app bundle; attempting to install via ideviceinstaller requires an .ipa. Please build an .ipa or install via Xcode.")
            return@doLast
        }

        throw GradleException("No suitable installer found (ios-deploy or ideviceinstaller) or no ipa/app bundle produced.")
    }
}

/* 生成 .ipa */
tasks.register("buildIpa") {
    group = "robovm"
    description = "Build signed .ipa for device. Provide -PsigningIdentity and -PprovisioningProfile if needed."
    dependsOn("downloadRoboVMLibs")
    doLast {
        if (!isMacOs()) throw GradleException("buildIpa is only supported on macOS.")
        val signingIdentity = project.findProperty("signingIdentity")?.toString()
        val provisioningProfile = project.findProperty("provisioningProfile")?.toString()
        val gradleCmd = if (File(project.rootDir, "gradlew").exists()) {
            if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "./gradlew"
        } else "gradle"
        val robovmTaskName = "robovmIpa"
        val cmd = mutableListOf(gradleCmd, robovmTaskName)
        if (!signingIdentity.isNullOrBlank()) cmd.add("-PsigningIdentity=$signingIdentity")
        if (!provisioningProfile.isNullOrBlank()) cmd.add("-PprovisioningProfile=$provisioningProfile")
        println("Invoking: ${cmd.joinToString(" ")}")
        val proc = ProcessBuilder(cmd).inheritIO().directory(project.rootDir).start()
        val exit = proc.waitFor()
        if (exit != 0) throw GradleException("robovmIpa task failed with exit code $exit")
        println("buildIpa: completed.")
    }
}

/* 安装 .ipa 到设备 */
tasks.register("installIpa") {
    group = "robovm"
    description = "Install generated .ipa to device using ios-deploy. Use -PdeviceId=<udid> to target a device."
    dependsOn("buildIpa")
    doLast {
        if (!isMacOs()) throw GradleException("installIpa is only supported on macOS.")
        val deviceId = project.findProperty("deviceId")?.toString()
        val iosDeploy = findExecutable("ios-deploy") ?: throw GradleException("ios-deploy not found in PATH.")
        val ipa = fileTree("build/robovm").matching { include("**/*.ipa") }.files.firstOrNull()
            ?: throw GradleException("No .ipa found under build/robovm. Run buildIpa first.")
        val cmd = mutableListOf(iosDeploy, "--bundle", ipa.absolutePath)
        if (!deviceId.isNullOrBlank()) cmd.addAll(listOf("--id", deviceId))
        println("Installing IPA: ${cmd.joinToString(" ")}")
        val proc = ProcessBuilder(cmd).inheritIO().directory(project.rootDir).start()
        val exit = proc.waitFor()
        if (exit != 0) throw GradleException("ios-deploy failed with exit code $exit")
        println("installIpa: completed.")
    }
}

/* 运行 JVM 单元测试并提供 robovm 集成测试占位 */
tasks.register("robovmTest") {
    group = "verification"
    description = "Run JVM unit tests and (optionally) RoboVM integration tests."
    dependsOn("test")
    doLast {
        println("robovmTest: JVM unit tests executed via 'test'.")
        val runIntegration = project.findProperty("robovmIntegration")?.toString()?.toBoolean() ?: false
        if (runIntegration) {
            println("robovmTest: running RoboVM integration tests (this may require device/simulator).")
            val gradleCmd = if (File(project.rootDir, "gradlew").exists()) {
                if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "./gradlew"
            } else "gradle"
            val proc = ProcessBuilder(gradleCmd, "robovmTestRunner", "--no-daemon")
                .directory(project.rootDir)
                .inheritIO()
                .start()
            val exit = proc.waitFor()
            if (exit != 0) logger.warn("robovm integration tests (robovmTestRunner) exited with code $exit")
        }
    }
}

/* 将robovm打包为zip */
tasks.register<Zip>("distRobovm") {
    group = "distribution"
    description = "Package RoboVM artifacts and libs into a zip for distribution."
    dependsOn("downloadRoboVMLibs", "extractRoboVMLibs")
    val outDir = file("$buildDir/distributions")
    destinationDirectory.set(outDir)
    archiveBaseName.set("${project.name}-robovm")
    from(file("libs/robovm")) { into("libs/robovm") }
    from(file("build/robovm")) { into("robovm") }
    doFirst { if (!outDir.exists()) outDir.mkdirs() }
}

tasks.matching { it.name == "classes" || it.name == "compileKotlin" || it.name == "compileJava" }.configureEach {
    dependsOn("downloadRoboVMLibs")
}

