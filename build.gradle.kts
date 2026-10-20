plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.18.2"
}

version = property("mod_version")!!
group = property("maven_group")!!
base.archivesName = property("archives_base_name") as String

java {
    // Gradle and Loom must run on a Java 25 JVM, but the mod itself targets Java 21 like Minecraft 1.21.11 does.
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")

    // Shader translation: GLSL -> SPIR-V (shaderc) -> MSL (SPIRV-Cross). Same LWJGL version Minecraft ships,
    // bundled jar-in-jar so the player needs nothing extra.
    val lwjgl = property("lwjgl_version")
    for (module in listOf("lwjgl-shaderc", "lwjgl-spvc")) {
        implementation("org.lwjgl:$module:$lwjgl")
        include("org.lwjgl:$module:$lwjgl")
        runtimeOnly("org.lwjgl:$module:$lwjgl:natives-macos-arm64")
        include("org.lwjgl:$module:$lwjgl:natives-macos-arm64")
    }

    // Sodium compatibility is optional: compiled against, loaded at runtime only when Sodium is installed.
    val sodium = "maven.modrinth:sodium:${property("sodium_version")}"
    modCompileOnly(sodium)
    testRuntimeOnly(sodium) // only its shader assets are read by tests
    if (providers.gradleProperty("sodium").isPresent) modLocalRuntime(sodium)

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.compileTestJava {
    options.release = 21
}

tasks.test {
    useJUnitPlatform()
}

val jniHeaders = layout.buildDirectory.dir("generated/jni")
val nativeLib = layout.buildDirectory.file("native/libmetalcraft.dylib")

tasks.compileJava {
    options.release = 21
    options.headerOutputDirectory = jniHeaders
}

// The native layer is a single Objective-C++ file; compiled with clang directly so no CMake/Xcode project is needed.
val buildNative = tasks.register<Exec>("buildNative") {
    val sources = fileTree("src/main/native")
    val javaHome = System.getProperty("java.home")
    dependsOn(tasks.compileJava)
    inputs.files(sources, jniHeaders)
    outputs.file(nativeLib)
    doFirst {
        nativeLib.get().asFile.parentFile.mkdirs()
        commandLine(
            "xcrun", "clang++", "-std=c++20", "-fobjc-arc", "-O2",
            "-arch", "arm64", "-mmacosx-version-min=14.0", "-shared",
            "-framework", "Metal", "-framework", "QuartzCore", "-framework", "Cocoa",
            "-I${javaHome}/include", "-I${javaHome}/include/darwin",
            "-I${jniHeaders.get().asFile}",
            "-o", nativeLib.get().asFile.absolutePath,
            *sources.files.filter { it.extension == "mm" }.map { it.absolutePath }.toTypedArray(),
        )
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
    from(buildNative) { into("natives") }
}

loom {
    runs.named("client") {
        // Metal API validation turns silent GPU driver crashes into readable assertion messages during development.
        // -Pbench drops it, since validation costs frame time.
        if (!providers.gradleProperty("bench").isPresent) environmentVariable("MTL_DEBUG_LAYER", "1")
        // ./gradlew runClient -PopenGL runs vanilla OpenGL with the same build, for A/B comparisons.
        if (providers.gradleProperty("openGL").isPresent) property("metalcraft.disable", "true")
        // ./gradlew runClient -PgpuProfile logs GPU time per render pass.
        if (providers.gradleProperty("gpuProfile").isPresent) property("metalcraft.gpuProfile", "true")
    }
}
