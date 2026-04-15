package com.cheroliv.newpipe

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

open class NewpipeExtension @Inject constructor(objects: ObjectFactory) {

    val configPath: Property<String> = objects.property(String::class.java)

    val clientSecretsDir: Property<String> = objects
        .property(String::class.java)
        .convention("")

    val sessionsPath: Property<String> = objects
        .property(String::class.java)
        .convention("")

    val outputPath: Property<String> = objects
        .property(String::class.java)
        .convention("")

    val tuneEntries: ListProperty<String> = objects.listProperty(String::class.java)
        .convention(emptyList())

    val playlistUrls: ListProperty<String> = objects.listProperty(String::class.java)
        .convention(emptyList())

    val ffmpegDockerImage: Property<String> = objects
        .property(String::class.java)
        .convention(DEFAULT_FFMPEG_IMAGE)

    val forceDocker: Property<Boolean> = objects
        .property(Boolean::class.java)
        .convention(false)

    companion object {
        const val DEFAULT_FFMPEG_IMAGE = "jrottenberg/ffmpeg:4.4-alpine"
    }
}