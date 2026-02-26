package com.cheroliv.newpipe

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * DSL extension for the newpipe plugin.
 *
 * Usage in build.gradle.kts:
 * ```
 * newpipe {
 *     configPath = file("musics.yml").absolutePath
 *
 *     // Optional — defaults to "jrottenberg/ffmpeg:4.4-alpine"
 *     // Only used when FFmpeg is not installed locally but Docker is available.
 *     ffmpegDockerImage = "jrottenberg/ffmpeg:4.4-alpine"
 *
 *     // Optional — force Docker even if FFmpeg is installed locally. Default: false
 *     forceDocker = true
 * }
 * ```
 */
open class NewpipeExtension @Inject constructor(objects: ObjectFactory) {
    val configPath: Property<String> = objects.property(String::class.java)

    /**
     * Docker image to use for FFmpeg when FFmpeg is not installed on the host.
     * Ignored if FFmpeg is available locally AND [forceDocker] is false.
     * Defaults to [DEFAULT_FFMPEG_IMAGE].
     */
    val ffmpegDockerImage: Property<String> = objects
        .property(String::class.java)
        .convention(DEFAULT_FFMPEG_IMAGE)

    /**
     * When true, skips the local FFmpeg probe and goes straight to Docker,
     * even if `ffmpeg` is available on the system PATH.
     * Defaults to false.
     */
    val forceDocker: Property<Boolean> = objects
        .property(Boolean::class.java)
        .convention(false)

    companion object {
        const val DEFAULT_FFMPEG_IMAGE = "jrottenberg/ffmpeg:4.4-alpine"
    }
}