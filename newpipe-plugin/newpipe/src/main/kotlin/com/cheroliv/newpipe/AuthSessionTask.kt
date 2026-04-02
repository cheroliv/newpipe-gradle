package com.cheroliv.newpipe

import com.cheroliv.newpipe.NewpipeManager.NEWPIPE_GROUP
import com.cheroliv.newpipe.NewpipeManager.yamlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant

/**
 * Ensures every [SessionCredentials] in sessions.yml has a [SessionCredentials.refreshToken].
 *
 * For each account:
 * - refreshToken already present → skip (Device Flow already done).
 * - refreshToken blank → full Device Flow → writes refreshToken to sessions.yml.
 *
 * accessToken and accessTokenExpiry are NOT written to disk —
 * they are obtained at runtime by [DownloadMusicTask] via silent refresh.
 */
open class AuthSessionTask : DefaultTask() {

    private val logger = LoggerFactory.getLogger(AuthSessionTask::class.java)

    @get:Input
    @get:Optional
    var sessionsPath: String = ""

    companion object {
        private const val DEVICE_CODE_URL = "https://oauth2.googleapis.com/device/code"
        private const val TOKEN_URL       = "https://oauth2.googleapis.com/token"
        private const val SCOPE           = "https://www.googleapis.com/auth/youtube.readonly"
        private const val GRANT_DEVICE    = "urn:ietf:params:oauth:grant-type:device_code"
    }

    init {
        group       = NEWPIPE_GROUP
        description = "Authenticates Google accounts in sessions.yml via OAuth2 Device Flow"
    }

    private val http = OkHttpClient()

    @TaskAction
    fun authenticate() {
        if (sessionsPath.isBlank()) {
            logger.info("No sessionsPath configured — skipping (anonymous mode)")
            return
        }
        val file = File(sessionsPath)
        if (!file.exists()) {
            logger.warn("sessions.yml not found at '$sessionsPath' — skipping (anonymous mode)")
            return
        }
        val config: SessionConfig = try {
            yamlMapper.readValue(file)
        } catch (e: Exception) {
            logger.warn("sessions.yml could not be parsed: ${e.message} — skipping (anonymous mode)")
            return
        }
        if (config.sessions.isEmpty()) {
            logger.warn("sessions.yml contains no sessions — skipping (anonymous mode)")
            return
        }

        logger.info("\n" + "=".repeat(60))
        logger.info("Checking ${config.sessions.size} session(s)…")
        logger.info("=".repeat(60))

        var updated = false
        config.sessions.forEach { creds ->
            logger.info("\n[${creds.id}] Checking…")
            if (creds.refreshToken.isNotBlank()) {
                logger.info("[${creds.id}] ✓ Refresh token present — skipping Device Flow")
            } else {
                runBlocking { runDeviceFlow(creds) }
                if (creds.refreshToken.isNotBlank()) updated = true
            }
        }

        // Only write back if a new refreshToken was obtained
        if (updated) {
            yamlMapper.writeValue(file, config)
            logger.info("\n✓ sessions.yml updated with new refresh token(s)")
        }
        logger.info("=".repeat(60))
    }

    private suspend fun runDeviceFlow(creds: SessionCredentials) {
        val deviceBody = FormBody.Builder()
            .add("client_id", creds.clientId)
            .add("scope",     SCOPE)
            .build()

        val deviceResponse = http.newCall(
            Request.Builder().url(DEVICE_CODE_URL).post(deviceBody).build()
        ).execute()

        if (!deviceResponse.isSuccessful) {
            logger.error("[${creds.id}] Device code request failed (HTTP ${deviceResponse.code}) — skipping")
            return
        }

        val deviceJson      = yamlMapper.readTree(deviceResponse.body?.string())
        val deviceCode      = deviceJson["device_code"].asText()
        val userCode        = deviceJson["user_code"].asText()
        val verificationUrl = deviceJson["verification_url"].asText()
        val expiresIn       = deviceJson["expires_in"]?.asLong() ?: 300L
        val intervalSec     = deviceJson["interval"]?.asLong()   ?: 5L

        logger.info("""

┌─────────────────────────────────────────────────────────┐
│  Compte : ${creds.id.padEnd(47)}│
│                                                         │
│  1. Allez sur  : $verificationUrl
│  2. Entrez le code : $userCode
│                                                         │
│  En attente de l'authentification…                      │
└─────────────────────────────────────────────────────────┘
        """.trimIndent())

        val deadline = Instant.now().plusSeconds(expiresIn)
        while (Instant.now().isBefore(deadline)) {
            delay(intervalSec * 1_000)

            val pollBody = FormBody.Builder()
                .add("client_id",     creds.clientId)
                .add("client_secret", creds.clientSecret)
                .add("device_code",   deviceCode)
                .add("grant_type",    GRANT_DEVICE)
                .build()

            val pollJson = yamlMapper.readTree(
                http.newCall(Request.Builder().url(TOKEN_URL).post(pollBody).build())
                    .execute().body?.string()
            )

            when (pollJson["error"]?.asText()) {
                null -> {
                    val refreshToken = pollJson["refresh_token"]?.asText() ?: ""
                    if (refreshToken.isNotBlank()) {
                        creds.refreshToken = refreshToken
                        logger.info("[${creds.id}] ✓ Authenticated — refresh token obtained")
                        return
                    }
                }
                "authorization_pending" -> logger.debug("[${creds.id}] Waiting for user…")
                "slow_down"             -> delay(intervalSec * 1_000)
                else -> {
                    logger.error("[${creds.id}] Device Flow error: ${pollJson["error"]?.asText()} — skipping")
                    return
                }
            }
        }
        logger.error("[${creds.id}] Device Flow timed out — re-run authSessions to retry")
    }
}