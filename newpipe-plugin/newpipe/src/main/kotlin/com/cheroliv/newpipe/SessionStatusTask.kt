package com.cheroliv.newpipe

import com.fasterxml.jackson.module.kotlin.readValue
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Gradle task that displays the current state of all YouTube sessions.
 *
 * Usage:
 *   ./gradlew sessionStatus
 *   ./gradlew sessionStatus --sessions=/path/to/sessions.yml
 *
 * Displays:
 *   - Session ID
 *   - Status (Active/Invalid)
 *   - Token expiry date
 *   - Requests made today
 *   - Last used time
 */
open class SessionStatusTask : DefaultTask() {

    private val logger = LoggerFactory.getLogger(SessionStatusTask::class.java)

    @get:Input
    @get:Optional
    @set:Option(option = "sessions", description = "Path to sessions.yml file (default: ./sessions.yml)")
    var sessionsPath: String = ""

    init {
        group = NewpipeManager.NEWPIPE_GROUP
        description = "Displays the current state of all YouTube sessions"
    }

    @TaskAction
    fun showStatus() {
        val sessionsFile = if (sessionsPath.isNotBlank()) {
            File(sessionsPath)
        } else {
            File(project.projectDir, "sessions.yml")
        }

        if (!sessionsFile.exists()) {
            logger.warn("Sessions file not found: ${sessionsFile.absolutePath}")
            logger.info("Run './gradlew buildSessions' to create sessions.yml")
            return
        }

        val config: SessionConfig = try {
            NewpipeManager.yamlMapper.readerFor(SessionConfig::class.java)
                .without(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(sessionsFile)
        } catch (e: Exception) {
            logger.warn("sessions.yml could not be parsed: ${e.message} — skipping")
            return
        }

        val sessions = config.sessions.map { credentials ->
            Session(credentials)
        }

        val monitor = SessionMonitor()
        sessions.forEach { monitor.register(it) }

        displayStatus(sessions, monitor)
    }

    private fun displayStatus(sessions: List<Session>, monitor: SessionMonitor) {
        val width = 70
        val separator = "─".repeat(width)
        val doubleSeparator = "═".repeat(width)

        logger.info("")
        logger.info("╔$doubleSeparator╗")
        logger.info("║  ${padRight("État des Sessions YouTube", width - 4)}  ║")
        logger.info("╠$doubleSeparator╣")

        if (sessions.isEmpty()) {
            logger.info("║  ${padRight("Aucune session configurée", width - 4)}  ║")
            logger.info("║  ${padRight("Exécutez './gradlew buildSessions' pour créer des sessions", width - 4)}  ║")
            logger.info("╚$doubleSeparator╝")
            logger.info("")
            return
        }

        sessions.forEachIndexed { index, session ->
            val isLast = index == sessions.size - 1
            val stats = monitor.getAllStats().find { it.id == session.id }
            val isValid = stats?.isValid == true
            val tokenValid = monitor.isTokenValid(session.id)

            val boxVert = "│"
            val boxBranch = "├"
            val boxCorner = "└"

            logger.info("║${if (isLast) "└" else "├"}${"─".repeat(width - 2)}$boxVert")
            logger.info("║  $boxVert ${padRight("[${session.id}]", width - 6)}  ║")
            logger.info("║  $boxVert")

            val statusIcon = if (isValid && tokenValid) "✓" else "✗"
            val statusText = if (isValid && tokenValid) "Actif" else "Inactif"
            logger.info("║  $boxVert  $boxBranch Statut      : $statusIcon $statusText")
            
            val expiryDate = monitor.getExpiryDateString(session.id)
            logger.info("║  $boxVert  $boxBranch Token expire : $expiryDate")

            val todayCount = monitor.getTodayRequestCount(session.id)
            logger.info("║  $boxVert  $boxBranch Requêtes ajd: $todayCount/1000")

            val lastUsed = monitor.getLastUsedString(session.id)
            logger.info("║  $boxVert  $boxCorner Dernière util: $lastUsed")

            if (!isLast) {
                logger.info("║  $boxVert")
            }
        }

        logger.info("║└${"─".repeat(width - 2)}│")
        logger.info("╚$doubleSeparator╝")
        logger.info("")

        val summary = buildString {
            val total = sessions.size
            val active = sessions.count { s ->
                monitor.getAllStats().find { it.id == s.id }?.isValid == true
            }
            append("Résumé: $active/$total session(s) active(s)")
            
            if (active < total) {
                append(" | ${total - active} session(s) inactive(s)")
            }
        }
        logger.info(summary)
        logger.info("")
    }

    private fun padRight(text: String, length: Int): String {
        return if (text.length >= length) text.substring(0, length) else text + " ".repeat(length - text.length)
    }
}
