# Loon Project Maker

A Swing-based project generator that creates Gradle KTS projects for multiple targets:
desktop, android, ios, teavm-c, teavm-js, teavm-wasm, other-jvm.

## Run
./gradlew run

## Usage
- Fill project name, package, main class, output path.
- Add local JARs and (optionally) Maven coordinates in code (mavenDeps).
- Select targets and click "Generate Project".
- Generated project will be in the output directory.

## Notes
- Android builds require Android SDK installed and templates may need adjustment for AGP versions.
- iOS builds require macOS and Kotlin Multiplatform toolchain.
- TeaVM templates assume `org.teavm` plugin availability.
