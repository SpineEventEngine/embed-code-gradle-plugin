import io.spine.embedcode.gradle.BuildSettings
import io.spine.embedcode.gradle.dependency.JUnit
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    `java-library`
    kotlin("jvm")
}

fun jvmTarget(version: Int): JvmTarget = JvmTarget.fromTarget(
    if (version == 8) "1.$version" else version.toString(),
)

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(BuildSettings.javaVersion))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(jvmTarget(BuildSettings.productionBytecodeVersion))
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.named<JavaCompile>("compileJava") {
    options.release.set(BuildSettings.productionBytecodeVersion)
}

tasks.named<KotlinJvmCompile>("compileTestKotlin") {
    compilerOptions.jvmTarget.set(jvmTarget(BuildSettings.javaVersion))
}

dependencies {
    testImplementation(JUnit.Jupiter.lib)
    testRuntimeOnly(JUnit.PlatformLauncher.lib)
}

tasks.test {
    useJUnitPlatform()
}
