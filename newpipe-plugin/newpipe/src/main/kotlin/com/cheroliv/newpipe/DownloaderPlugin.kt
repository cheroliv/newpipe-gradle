@file:Suppress("unused")

package com.cheroliv.newpipe

import com.cheroliv.newpipe.NewpipeManager.CONFIG_PATH_EXCEPTION_MESSAGE
import com.cheroliv.newpipe.NewpipeManager.NEWPIPE_GROUP
import com.cheroliv.newpipe.NewpipeManager.registerDownloadTask
import com.cheroliv.newpipe.NewpipeManager.yamlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.gradle.api.Plugin
import org.gradle.api.Project


class DownloaderPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val newpipeExtension = project.extensions.create(
            NEWPIPE_GROUP,
            NewpipeExtension::class.java
        )
        project.afterEvaluate {
            project
                .layout
                .projectDirectory
                .asFile
                .resolve(newpipeExtension.configPath.get())
                .run {
                    if (!exists() || isDirectory)
                        throw Exception(CONFIG_PATH_EXCEPTION_MESSAGE)
                }
            project.registerDownloadTask(
                newpipeExtension,
                yamlMapper.readValue(
                    project
                        .layout
                        .projectDirectory
                        .asFile
                        .resolve(newpipeExtension.configPath.get())
                )

            )
        }
    }
}