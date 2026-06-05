plugins {
    id("com.android.application")
    kotlin("android")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

val androidGradlePluginVersion = "8.1.0"
val kotlinVersion = "1.9.2"

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
    }
}

android {
    compileSdk = @{AndroidTargetSdk}.toInt()

    defaultConfig {
        applicationId = "@{ProjectPackage}"
        minSdk = @{AndroidMinSdk}.toInt()
        targetSdk = @{AndroidTargetSdk}.toInt()
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
            res.srcDirs("src/main/res")
            manifest.srcFile("src/main/AndroidManifest.xml")
        }
    }
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(fileTree(mapOf("dir" to "../core/libs", "include" to listOf("*.jar"))))
    implementation(project(":core"))
    @{AndroidDependencies}
}

import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Copy
import java.io.File

// 读取用户传入的属性或使用默认值
val apkVariantProp = project.findProperty("apkVariant")?.toString()?.lowercase() ?: "debug"
val deviceSerialProp = project.findProperty("deviceSerial")?.toString()
val adbPathProp = project.findProperty("adbPath")?.toString()
val disableInstallProp = project.findProperty("disableInstallOnDevice")?.toString()?.toBoolean() ?: false

fun assembleTaskNameForVariant(variant: String): String {
    val cap = variant.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    return "assemble$cap"
}

fun apkFileForVariant(variant: String): File {
    val candidate1 = file("${buildDir}/outputs/apk/${variant}/app-${variant}.apk")
    if (candidate1.exists()) return candidate1
    // AGP 可能输出到 build/outputs/apk/<flavor>/<variant>/app-<flavor>-<variant>.apk
    // 尝试搜索匹配的 apk 文件
    val outputsDir = file("${buildDir}/outputs/apk")
    if (outputsDir.exists()) {
        outputsDir.walkTopDown().filter { it.isFile && it.extension == "apk" && it.name.contains(variant) }.forEach {
            return it
        }
    }
    // 尝试 build/outputs/apk/*.apk
    val fallback = file("${buildDir}/outputs/apk/${variant}")
    if (fallback.exists()) {
        val found = fallback.listFiles()?.firstOrNull { it.extension == "apk" }
        if (found != null) return found
    }
    // 最后返回预期路径，调用者需检查文件是否存在
    return candidate1
}

// 选择adb可执行路径
fun adbExecutable(): String {
    if (!adbPathProp.isNullOrBlank()) return adbPathProp
    val sdkRoot = System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME")
    if (!sdkRoot.isNullOrBlank()) {
        val adbFromSdk = File(sdkRoot, "platform-tools/adb")
        if (adbFromSdk.exists()) return adbFromSdk.absolutePath
        val adbFromSdkBat = File(sdkRoot, "platform-tools/adb.exe")
        if (adbFromSdkBat.exists()) return adbFromSdkBat.absolutePath
    }
    return "adb"
}

// 列出设备
tasks.register<Exec>("adbDevices") {
    group = "android"
    description = "List connected adb devices"
    commandLine(adbExecutable(), "devices")
}

// 安装APK到设备（可指定 deviceSerial）
tasks.register("installApk") {
    group = "android"
    description = "Install built APK to device. Use -PapkVariant=debug -PdeviceSerial=<serial> to control."
    val variant = apkVariantProp
    val assembleTask = assembleTaskNameForVariant(variant)
    dependsOn(assembleTask)

    doLast {
        val apk = apkFileForVariant(variant)
        if (!apk.exists()) {
            throw GradleException("APK not found at ${apk.absolutePath}. Build may have failed or output path differs.")
        }
        if (disableInstallProp) {
            logger.lifecycle("Installation disabled by -PdisableInstallOnDevice=true. APK located at: ${apk.absolutePath}")
            return@doLast
        }
        val adb = adbExecutable()
        val deviceArgs = if (!deviceSerialProp.isNullOrBlank()) listOf("-s", deviceSerialProp) else emptyList()
        val installCmd = listOf(adb) + deviceArgs + listOf("install", "-r", apk.absolutePath)
        logger.lifecycle("Running: ${installCmd.joinToString(" ")}")
        val proc = ProcessBuilder(installCmd).inheritIO().directory(project.rootDir).start()
        val exit = proc.waitFor()
        if (exit != 0) {
            throw GradleException("adb install failed with exit code $exit")
        } else {
            logger.lifecycle("APK installed successfully to device${if (!deviceSerialProp.isNullOrBlank()) " [$deviceSerialProp]" else ""}.")
        }
    }
}

// 卸载应用
tasks.register("uninstallApp") {
    group = "android"
    description = "Uninstall app from device. Use -PdeviceSerial=<serial> to target a specific device."
    doLast {
        val appId = android.defaultConfig.applicationId
        val adb = adbExecutable()
        val deviceArgs = if (!deviceSerialProp.isNullOrBlank()) listOf("-s", deviceSerialProp) else emptyList()
        val uninstallCmd = listOf(adb) + deviceArgs + listOf("uninstall", appId)
        logger.lifecycle("Running: ${uninstallCmd.joinToString(" ")}")
        val proc = ProcessBuilder(uninstallCmd).inheritIO().directory(project.rootDir).start()
        val exit = proc.waitFor()
        if (exit != 0) {
            logger.warn("adb uninstall returned exit code $exit (may mean app not installed).")
        } else {
            logger.lifecycle("App $appId uninstalled from device.")
        }
    }
}

// 启动应用
tasks.register("launchApp") {
    group = "android"
    description = "Launch the app on device using adb monkey. Use -PdeviceSerial=<serial> to target a specific device."
    dependsOn("installApk")
    doLast {
        val appId = android.defaultConfig.applicationId
        val adb = adbExecutable()
        val deviceArgs = if (!deviceSerialProp.isNullOrBlank()) listOf("-s", deviceSerialProp) else emptyList()
        val launchCmd = listOf(adb) + deviceArgs + listOf("shell", "monkey", "-p", appId, "-c", "android.intent.category.LAUNCHER", "1")
        logger.lifecycle("Running: ${launchCmd.joinToString(" ")}")
        val proc = ProcessBuilder(launchCmd).inheritIO().directory(project.rootDir).start()
        val exit = proc.waitFor()
        if (exit != 0) {
            throw GradleException("adb launch (monkey) failed with exit code $exit")
        } else {
            logger.lifecycle("App launched on device${if (!deviceSerialProp.isNullOrBlank()) " [$deviceSerialProp]" else ""}.")
        }
    }
}

// 一键打包并安装（assemble -> install）
tasks.register("assembleAndInstall") {
    group = "android"
    description = "Assemble APK for variant and install to device. Use -PapkVariant and -PdeviceSerial."
    dependsOn("installApk")
}

// 一键打包、安装并启动
tasks.register("assembleInstallAndLaunch") {
    group = "android"
    description = "Assemble APK, install to device and launch app. Use -PapkVariant and -PdeviceSerial."
    dependsOn("launchApp")
}


tasks.register("pushApkToDeviceStorage") {
    group = "android"
    description = "Push APK to device storage (e.g., /sdcard/Download). Use -PapkVariant and -PdeviceSerial."
    val variant = apkVariantProp
    val assembleTask = assembleTaskNameForVariant(variant)
    dependsOn(assembleTask)
    doLast {
        val apk = apkFileForVariant(variant)
        if (!apk.exists()) throw GradleException("APK not found at ${apk.absolutePath}")
        val adb = adbExecutable()
        val deviceArgs = if (!deviceSerialProp.isNullOrBlank()) listOf("-s", deviceSerialProp) else emptyList()
        val remotePath = "/sdcard/Download/${apk.name}"
        val pushCmd = listOf(adb) + deviceArgs + listOf("push", apk.absolutePath, remotePath)
        logger.lifecycle("Running: ${pushCmd.joinToString(" ")}")
        val proc = ProcessBuilder(pushCmd).inheritIO().directory(project.rootDir).start()
        val exit = proc.waitFor()
        if (exit != 0) throw GradleException("adb push failed with exit code $exit")
        logger.lifecycle("APK pushed to device at $remotePath")
    }
}

tasks.register("printApkInstallConfig") {
    group = "help"
    description = "Print current APK install configuration (apkVariant, deviceSerial, adbPath, disableInstallOnDevice)."
    doLast {
        logger.lifecycle("apkVariant = $apkVariantProp")
        logger.lifecycle("deviceSerial = ${deviceSerialProp ?: "<not specified>"}")
        logger.lifecycle("adbPath = ${if (!adbPathProp.isNullOrBlank()) adbPathProp else "<use PATH or ANDROID_SDK_ROOT>"}")
        logger.lifecycle("disableInstallOnDevice = $disableInstallProp")
        val apk = apkFileForVariant(apkVariantProp)
        logger.lifecycle("expected apk path = ${apk.absolutePath} (exists=${apk.exists()})")
    }
}
