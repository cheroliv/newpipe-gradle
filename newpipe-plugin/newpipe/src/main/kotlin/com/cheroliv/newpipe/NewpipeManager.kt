package com.cheroliv.newpipe

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.gradle.api.Project

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

    internal fun Project.registerDownloadTask(selection: Selection) {
        tasks.register("download") { task ->
            task.apply {
                group = NEWPIPE_GROUP
                description =
                    "Download files from selection in ${extensions.findByType(NewpipeExtension::class.java)?.configPath}"
                println(selection)

            }
        }
    }
}