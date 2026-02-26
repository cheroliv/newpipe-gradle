plugins { alias(libs.plugins.newpipe) }

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

newpipe {
    configPath = file("musics.yml").absolutePath
    ffmpegDockerImage = "jrottenberg/ffmpeg:8-scratch"
    forceDocker = true   // ← force Docker even if ffmpeg is locally installed
}