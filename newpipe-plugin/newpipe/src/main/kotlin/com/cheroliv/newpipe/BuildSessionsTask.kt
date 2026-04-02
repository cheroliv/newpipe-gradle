package com.cheroliv.newpipe

import com.cheroliv.newpipe.NewpipeManager.NEWPIPE_GROUP
import com.cheroliv.newpipe.NewpipeManager.yamlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Scans [clientSecretsDir] for Google OAuth2 JSON files downloaded from
 * Google Cloud Console (type "installed" / TV & Limited Input devices)
 * and generates or updates [sessionsPath] with [SessionCredentials].
 *
 * Only clientId, clientSecret and id are written — refreshToken is
 * preserved from the existing sessions.yml if already present so
 * [AuthSessionTask] does not re-trigger Device Flow unnecessarily.
 *
 * Session id is derived from the filename:
 * client_secret_5284-q61ql0tbjqt8.apps.googleusercontent.com.json
 * → 5284-q61ql0tbjqt8
 */
open class BuildSessionsTask : DefaultTask() {

    private val logger = LoggerFactory.getLogger(BuildSessionsTask::class.java)

    @get:Input
    @get:Optional
    var clientSecretsDir: String = ""

    @get:Input
    @get:Optional
    var sessionsPath: String = ""

    init {
        group       = NEWPIPE_GROUP
        description = "Builds sessions.yml from JSON files in client_secrets/"
    }

    @TaskAction
    fun build() {
        if (clientSecretsDir.isBlank()) {
            logger.warn("clientSecretsDir not configured — skipping")
            return
        }
        if (sessionsPath.isBlank()) {
            logger.warn("sessionsPath not configured — skipping")
            return
        }

        val secretsDir = File(clientSecretsDir)
        if (!secretsDir.exists() || !secretsDir.isDirectory) {
            logger.warn("client_secrets/ not found at '$clientSecretsDir' — skipping")
            return
        }

        val jsonFiles = secretsDir
            .listFiles { f -> f.isFile && f.extension == "json" }
            ?.sortedBy { it.name }
            ?: emptyList()

        if (jsonFiles.isEmpty()) {
            logger.warn("No JSON files found in '$clientSecretsDir' — skipping")
            return
        }

        logger.info("\n" + "=".repeat(60))
        logger.info("Building sessions.yml from ${jsonFiles.size} file(s)")
        logger.info("=".repeat(60))

        // Preserve existing refreshTokens — Device Flow already ran for these
        val sessionsFile = File(sessionsPath)
        val existing: Map<String, SessionCredentials> = if (sessionsFile.exists()) {
            try {
                yamlMapper.readValue<SessionConfig>(sessionsFile)
                    .sessions.associateBy { it.id }
            } catch (e: Exception) {
                logger.warn("Could not read existing sessions.yml: ${e.message} — will overwrite")
                emptyMap()
            }
        } else emptyMap()

        val credentials = mutableListOf<SessionCredentials>()

        jsonFiles.forEach { file ->
            try {
                val installed = yamlMapper.readTree(file)["installed"]
                if (installed == null) {
                    logger.warn("Skipping ${file.name} — missing 'installed' key (wrong OAuth2 type?)")
                    return@forEach
                }

                val clientId     = installed["client_id"]?.asText()?.trim()     ?: ""
                val clientSecret = installed["client_secret"]?.asText()?.trim() ?: ""

                if (clientId.isBlank() || clientSecret.isBlank()) {
                    logger.warn("Skipping ${file.name} — client_id or client_secret is blank")
                    return@forEach
                }

                val id = file.nameWithoutExtension
                    .removePrefix("client_secret_")
                    .substringBefore(".apps.googleusercontent.com")
                    .ifBlank { file.nameWithoutExtension }

                val creds = SessionCredentials(
                    id           = id,
                    clientId     = clientId,
                    clientSecret = clientSecret,
                    refreshToken = existing[id]?.refreshToken ?: ""
                )

                val status = if (creds.refreshToken.isNotBlank()) "↻ refresh token present"
                else "⚠ Device Flow needed"
                logger.info("  [$id] $status")
                credentials += creds

            } catch (e: Exception) {
                logger.error("Failed to parse ${file.name}: ${e.message} — skipping")
            }
        }

        if (credentials.isEmpty()) {
            logger.warn("No valid credentials extracted — sessions.yml not written")
            return
        }

        sessionsFile.parentFile?.mkdirs()
        yamlMapper.writeValue(sessionsFile, SessionConfig(credentials.toMutableList()))
        logger.info("\n✓ sessions.yml written — ${credentials.size} session(s)")
        logger.info("=".repeat(60))
    }
}