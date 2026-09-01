plugins {
    java
    id("me.champeau.jmh") version "0.7.3"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    jmh(project(":"))
}

jmh {
    warmupIterations.set(3)
    iterations.set(5)
    fork.set(1)
    profilers.set(listOf("stack"))
}
