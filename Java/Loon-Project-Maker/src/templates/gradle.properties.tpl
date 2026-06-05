# Gradle runtime and project settings
org.gradle.jvmargs=-Xmx2048m -XX:+UseParallelGC -Dfile.encoding=UTF-8
org.gradle.logging.level=quiet
org.gradle.caching=true
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.configureondemand=true
# Versions and feature flags (injected by generator)
lwjgl3Version=@{LwjglVersion}
graalHelperVersion=@{GraalHelperVersion}
enableGraalNative=@{EnableGraalNative}
robovmVersion=@{RoboVMVersion}
loonVersion=@{LoonVersion}
projectVersion=@{ProjectVersion}
