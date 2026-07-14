package io.spine.embedcode.gradle

/** Build-wide Java and bytecode targets. */
object BuildSettings {

    /** Java toolchain version used to build and test the project. */
    const val javaVersion = 17

    /** JVM bytecode version produced for published code. */
    const val productionBytecodeVersion = 8
}
