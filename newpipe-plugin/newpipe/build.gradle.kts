plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("maven-publish")
    id("signing")
    alias(libs.plugins.publish)
}

group = "com.cheroliv.newpipe"
version = libs.plugins.newpipe.get().version

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
            displayName = "NewPipe Downloader Plugin"
            description = "Gradle plugin for downloading music from YouTube and converting to MP3."
            tags.set(listOf("newpipe", "downloader", "m4a", "ffmpeg", "docker", "mp3", "kotlin-dsl", "youtube"))
        }
    }
    website = "https://cheroliv.com"
    vcsUrl = "https://github.com/cheroliv/newpipe-gradle.git"
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
        withType<MavenPublication> {
            if (name == "pluginMaven") {
                pom {
                    name.set(gradlePlugin.plugins.getByName("newpipePlugin").displayName)
                    description.set(gradlePlugin.plugins.getByName("newpipePlugin").description)
                    url.set(gradlePlugin.website.get())
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("cheroliv")
                            name.set("cheroliv")
                            email.set("cheroliv.developer@gmail.com")
                        }
                    }
                    scm {
                        connection.set(gradlePlugin.vcsUrl.get())
                        developerConnection.set(gradlePlugin.vcsUrl.get())
                        url.set(gradlePlugin.vcsUrl.get())
                    }
                }
            }
        }
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "sonatype"
            url = if (version.toString().endsWith("-SNAPSHOT")) {
                uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            } else {
                uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            }
            credentials {
                username = project.findProperty("ossrhUsername") as? String
                password = project.findProperty("ossrhPassword") as? String
            }
        }
        mavenCentral()
    }
}

signing {
    val isReleaseVersion = !version.toString().endsWith("-SNAPSHOT")
    if (isReleaseVersion) sign(publishing.publications)
    useGpgCmd()
}