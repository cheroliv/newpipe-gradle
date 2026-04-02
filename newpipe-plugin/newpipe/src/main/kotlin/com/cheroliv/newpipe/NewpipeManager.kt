package com.cheroliv.newpipe

import org.gradle.api.Project

object NewpipeManager {

    fun configure(project: Project) {
        val extension = project.extensions.getByType(NewpipeExtension::class.java)

        val buildSessions = project.tasks.register("buildSessions", BuildSessionsTask::class.java) {
            clientSecretsDir.set(extension.clientSecretsDir)
            sessionsPath.set(extension.sessionsPath)
        }

        val authSessions = project.tasks.register("authSessions", AuthSessionTask::class.java) {
            sessionsPath.set(extension.sessionsPath)
            dependsOn(buildSessions)
        }

        project.tasks.register("downloadMusic", DownloadMusicTask::class.java) {
            outputPath.set(extension.outputPath)
            ffmpegDockerImage.set(extension.ffmpegDockerImage)
            forceDocker.set(extension.forceDocker)
            sessionsPath.set(extension.sessionsPath)
            tuneEntries.set(extension.tuneEntries)
            playlistUrls.set(extension.playlistUrls)
            dependsOn(authSessions)
        }
    }
}