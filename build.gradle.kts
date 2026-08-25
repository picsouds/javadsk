plugins {
    java
    application
    jacoco
    id("org.sonarqube") version "7.4.0.8496"
}

group = "dsk"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

application {
    mainClass.set("dsk.cli.DskTool")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "dsk.cli.DskTool"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("info.picocli:picocli:4.7.6")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.tukaani:xz:1.9")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

sonar {
    properties {
        property("sonar.projectKey", "picsouds_javadsk")
        property("sonar.organization", "picsouds")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.projectName", "javadsk")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/test/jacocoTestReport.xml")
        property("sonar.java.binaries", "build/classes/java/main")
        property("sonar.sources", "src/main/java")
        property("sonar.tests", "src/test/java")
    }
}
