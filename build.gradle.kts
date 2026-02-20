plugins { alias(libs.plugins.newpipe) }

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

newpipe { configPath = file("musics.yml").absolutePath }