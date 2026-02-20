package com.cheroliv.newpipe

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.gradle.api.Project
import java.io.File

object NewpipeManager {

    const val NEWPIPE_GROUP = "newpipe"
    const val CONFIG_PATH_EXCEPTION_MESSAGE =
        """configPath should exists like this : plugins { alias(libs.plugins.bakery) }

bakery { configPath = file("site.yml").absolutePath }
"""
    val yamlMapper: ObjectMapper
        get() = YAMLFactory()
            .let(::ObjectMapper)
            .disable(WRITE_DATES_AS_TIMESTAMPS)
            .registerKotlinModule()

// ==================== Publish Site Task ====================

    internal fun Project.registerDownloadTask(
        newpipeExtension: NewpipeExtension,
        selection: Selection
    ) {
        tasks.register("download",DownloadMusicTask::class.java) { task ->
            task.apply {
                group = NEWPIPE_GROUP
                description = newpipeExtension
                    .configPath
                    .get()
                    .run(::File)
                    .name
                    .let { "Download files from selection in $it" }
                // Configuration par défaut si besoin
                outputPath = "${project.projectDir}/downloads"
                url = selection.artistes.first().tunes.first()
            }
        }
    }
}