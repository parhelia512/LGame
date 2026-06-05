plugins {
    application
    id("org.jetbrains.kotlin.jvm") version "1.9.0" apply false
}

repositories {
    mavenCentral()
}

dependencies {
    // runtime dependencies for the tool (FlatLaf + MigLayout)
    implementation("com.formdev:flatlaf:3.0")
    implementation("com.miglayout:miglayout-swing:5.3")
}

application {
    mainClass.set("com.example.projectmaker.MainWindow")
}
