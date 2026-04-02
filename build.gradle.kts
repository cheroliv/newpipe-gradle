plugins {
    alias(libs.plugins.newpipe)
    alias(libs.plugins.readme)
}

repositories {
    mavenLocal()
    gradlePluginPortal()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

newpipe {
    configPath = file("musics.yml").absolutePath
    ffmpegDockerImage = "jrottenberg/ffmpeg:8-scratch"
    forceDocker = true   // ← force Docker even if ffmpeg is locally installed
    sessionsPath = file("sessions.yml").absolutePath
}
/*
newpipe {
    configPath       = file("musics.yml").absolutePath
    clientSecretsDir = file("client_secrets").absolutePath
    sessionsPath     = file("sessions.yml").absolutePath
}
*/