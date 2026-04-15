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
    const val CONFIG_PATH_EXCEPTION_MESSAGE = "Configuration file not found or is a directory"
    const val REGEX_CLEAN_TUNE_NAME = "[^a-zA-Z0-9\\s\\-_]"

    val yamlMapper: ObjectMapper = YAMLFactory()
        .let(::ObjectMapper)
        .disable(WRITE_DATES_AS_TIMESTAMPS)
        .registerKotlinModule()

    fun Project.registerDownloadTask(extension: NewpipeExtension, config: Selection) {
        tasks.register("downloadMusic", DownloadMusicTask::class.java) {
            this.outputPath = extension.outputPath.get()
            this.ffmpegDockerImage = extension.ffmpegDockerImage.get()
            this.forceDocker = extension.forceDocker.get()
            this.sessionsPath = extension.sessionsPath.get()
            this.tuneEntries = config.artistes.flatMap { artist ->
                artist.tunes.map { tune -> "${artist.name}|$tune" }
            }
            this.playlistEntries = config.artistes.flatMap { artist ->
                artist.playlists
            }
        }
    }

    fun configure(project: Project) {
        val extension = project.extensions.getByType(NewpipeExtension::class.java)

        project.tasks.register("buildSessions", BuildSessionsTask::class.java) {
            clientSecretsDir = extension.clientSecretsDir.get()
            sessionsPath = extension.sessionsPath.get()
        }

        val authSessions = project.tasks.register("authSessions", AuthSessionTask::class.java) {
            sessionsPath = extension.sessionsPath.get()
            dependsOn("buildSessions")
        }

        project.tasks.register("sessionStatus", SessionStatusTask::class.java) {
            sessionsPath = extension.sessionsPath.get()
        }

        project.tasks.register("download", DownloadMusicTask::class.java) {
            outputPath = extension.outputPath.get()
            ffmpegDockerImage = extension.ffmpegDockerImage.get()
            forceDocker = extension.forceDocker.get()
            sessionsPath = extension.sessionsPath.get()
            dependsOn(authSessions)
        }
    }

    fun buildSessionManager(sessionsPath: String): SessionManager {
        val file = File(sessionsPath)
        if (!file.exists()) return SessionManager(emptyList())
        
        val config: SessionConfig = try {
            yamlMapper.readValue(file)
        } catch (e: Exception) {
            return SessionManager(emptyList())
        }
        
        val sessions = config.sessions
            .filter { it.refreshToken.isNotBlank() }
            .map { Session(it) }
        return SessionManager(sessions, TokenRefresher())
    }

    /**
     * Returns the current active session from the SessionManager.
     * Used for age verification and error handling.
     */
    fun getCurrentSession(sessionsPath: String): Session? {
        val manager = buildSessionManager(sessionsPath)
        return manager.next()
    }
}
