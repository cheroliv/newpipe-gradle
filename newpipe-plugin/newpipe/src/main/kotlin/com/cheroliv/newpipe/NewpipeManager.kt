package com.cheroliv.newpipe

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.gradle.api.Project
import java.io.File

object NewpipeManager {

    const val NEWPIPE_GROUP = "newpipe"
    const val CONFIG_PATH_EXCEPTION_MESSAGE =
        """configPath should exist — example:
plugins { id("com.cheroliv.newpipe") }
newpipe { configPath = file("musics.yml").absolutePath }
"""
    const val REGEX_CLEAN_TUNE_NAME = "[^a-zA-Z0-9àâäéèêëïîôùûüÿçÀÂÄÉÈÊËÏÎÔÙÛÜŸÇ \\-_]"

    val yamlMapper: ObjectMapper
        get() = YAMLFactory()
            .let(::ObjectMapper)
            .disable(WRITE_DATES_AS_TIMESTAMPS)
            .registerKotlinModule()

    internal fun Project.registerDownloadTask(
        extension: NewpipeExtension,
        selection: Selection
    ) {
        tasks.register("download", DownloadMusicTask::class.java) { task ->
            task.apply {
                group = NEWPIPE_GROUP
                description = File(extension.configPath.get()).name
                    .let { "Download files from selection in $it" }

                outputPath = "${project.projectDir}/downloads"
                ffmpegDockerImage = extension.ffmpegDockerImage.get()

                // Flatten YAML into (artistHint, url) pairs
                tuneEntries = selection.artistes.flatMap { artist ->
                    artist.tunes.map { url -> artist.name to url }
                }
                playlistUrls = selection.artistes.flatMap { it.playlists }
            }
        }
    }
}
