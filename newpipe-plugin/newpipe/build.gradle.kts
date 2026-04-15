plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("maven-publish")
    id("signing")
}

group = "com.cheroliv.newpipe"
version = "0.1.0-SNAPSHOT"

// Utilisation de la toolchain comme dans ton modèle
kotlin.jvmToolchain(11)

java {
    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    gradlePluginPortal()
}

// Définition du SourceSet FunctionalTest
val functionalTest by sourceSets.creating {
    kotlin.srcDir("src/functionalTest/kotlin")
    resources.srcDir("src/functionalTest/resources")
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

// Configuration pour l'héritage des dépendances
configurations {
    val functionalTestImplementation by getting {
        extendsFrom(configurations.implementation.get())
        extendsFrom(configurations.testImplementation.get())
    }
}

dependencies {
    // Core (inspiré de ton modèle)
    implementation(kotlin("stdlib-jdk8"))
    implementation(gradleApi())

    // Dépendances spécifiques NewPipe
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    val jacksonVersion = "2.15.2"
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.24.0")
    implementation("net.jthink:jaudiotagger:3.0.1")

    // Tests (comme dans ton modèle)
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.assertj:assertj-core:3.24.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Correction du doublon logback-test.xml
tasks.named<ProcessResources>("processFunctionalTestResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

gradlePlugin {
    testSourceSets(functionalTest)
    plugins {
        create("newpipePlugin") {
            id = "com.cheroliv.newpipe"
            implementationClass = "com.cheroliv.newpipe.DownloaderPlugin"
        }
    }
}

val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs functional tests."
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    useJUnitPlatform()
}

tasks.check { dependsOn(functionalTestTask) }

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}