plugins { alias(libs.plugins.newpipe) }

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

newpipe {
    configPath = file("musics.yml").absolutePath
    // optionnel — défaut : "jrottenberg/ffmpeg:4.4-alpine"
    ffmpegDockerImage = "jrottenberg/ffmpeg:8-scratch"
}
