import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    kotlin("jvm") version "2.4.21"
    application
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}
group = "org.emberwood.terminal"
version = file("VERSION").readText().trim()
repositories { mavenCentral() }
dependencies {
    implementation("com.williamcallahan:tui4j:0.3.5")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
kotlin { jvmToolchain(25) }
application {
    mainClass.set("emberwood.MainKt")
    applicationDefaultJvmArgs = listOf("-Xms32m", "-Xmx256m", "-Djava.awt.headless=true", "--enable-native-access=ALL-UNNAMED")
}
tasks.test { useJUnitPlatform() }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors.set(true)
        // Large imported pixel-art methods trigger expensive bytecode data-flow passes.
        // HotSpot still optimizes the running game; keep clean builds practical.
        freeCompilerArgs.add("-Xno-optimize")
    }
}
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.register<Jar>("fatJar") {
    group = "build"
    archiveFileName.set("emberwood-tui-bundle.jar")
    val playableJar = layout.buildDirectory.file("libs/emberwood-tui.jar")
    outputs.file(playableJar)
    doLast {
        // Rebuilding must not truncate the JAR an existing game is reading.
        val target = playableJar.get().asFile.toPath()
        val candidate = target.resolveSibling("emberwood-tui.jar.tmp")
        Files.copy(archiveFile.get().asFile.toPath(), candidate, StandardCopyOption.REPLACE_EXISTING)
        try {
            Files.move(
                candidate,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(candidate, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
    manifest { attributes["Main-Class"] = application.mainClass.get() }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class", "META-INF/versions/**/module-info.class")
}
