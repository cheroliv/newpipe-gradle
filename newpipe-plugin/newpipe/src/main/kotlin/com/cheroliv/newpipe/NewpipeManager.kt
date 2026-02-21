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
        """configPath must point to an existing file, e.g.:

plugins { alias(libs.plugins.newpipe) }

newpipe { configPath = file("musics.yml").absolutePath }
"""
    const val REGEX_CLEAN_TUNE_NAME = "[^a-zA-Z0-9àâäéèêëïîôùûüÿçÀÂÄÉÈÊËÏÎÔÙÛÜŸÇ \\-_]"

    val yamlMapper: ObjectMapper
        get() = YAMLFactory()
            .let(::ObjectMapper)
            .disable(WRITE_DATES_AS_TIMESTAMPS)
            .registerKotlinModule()

    // ==================== Download Task Registration ====================

    internal fun Project.registerDownloadTask(
        newpipeExtension: NewpipeExtension,
        selection: Selection
    ) {
        tasks.register("download", DownloadMusicTask::class.java) { task ->
            task.apply {
                group = NEWPIPE_GROUP
                description = newpipeExtension
                    .configPath
                    .get()
                    .run(::File)
                    .name
                    .let { "Download files from selection defined in $it" }
                outputPath = "${project.projectDir}/downloads"
                url = selection.artistes.first().tunes.first()
            }
        }
    }
}