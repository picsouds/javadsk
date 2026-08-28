plugins {
    java
    application
    jacoco
    id("org.sonarqube") version "7.4.0.8496"
}

group = "dsk"
version = "1.0.3"

java {
    toolchain {
        // 21 (LTS) fait tourner Gradle/les tests/JUnit 6 (qui exige 17+) ; le jar produit reste
        // Java 11 grâce à compileJava.options.release ci-dessous, indépendamment du JDK utilisé
        // pour compiler.
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.compileJava {
    options.release.set(11)
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
    implementation("info.picocli:picocli:4.7.7")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.tukaani:xz:1.12")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
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
        // Vue Swing, contrôleur et point d'entrée (MainWindow) : orchestration de fenêtres/dialogues,
        // non testable sans écran (Frame/JOptionPane lèvent HeadlessException en CI). La logique
        // testable est dans model/service, qui restent couverts normalement.
        property("sonar.coverage.exclusions", listOf(
            "gui/src/main/java/dsk/gui/*.java",
            "gui/src/main/java/dsk/gui/view/**",
            "gui/src/main/java/dsk/gui/controller/**"
        ).joinToString(","))
    }
}
