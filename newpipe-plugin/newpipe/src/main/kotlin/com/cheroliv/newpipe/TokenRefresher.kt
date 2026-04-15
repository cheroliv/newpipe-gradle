package com.cheroliv.newpipe

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Handles OAuth2 token refresh for YouTube API sessions.
 *
 * Refreshes access tokens silently before they expire, using the
 * refresh token flow. Refreshed tokens live only in memory and
 * are never written to sessions.yml.
 *
 * Thread-safe — can be called from multiple coroutines concurrently.
 */
class TokenRefresher(
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val logger = LoggerFactory.getLogger(TokenRefresher::class.java)

    /**
     * Refreshes the access token for a session if needed.
     *
     * @param session The session to refresh
     * @return true if refresh succeeded or was not needed, false if refresh failed
     */
    fun refreshIfNeeded(session: Session): Boolean {
        if (session.isAccessTokenValid()) {
            logger.debug("[${session.id}] Access token still valid — skipping refresh")
            return true
        }

        logger.info("[${session.id}] Access token expired or missing — refreshing…")
        return refreshAccessToken(session)
    }

    /**
     * Forces a token refresh regardless of current validity.
     * Useful for testing or when a token is known to be revoked.
     */
    fun forceRefresh(session: Session): Boolean {
        logger.info("[${session.id}] Forcing token refresh…")
        return refreshAccessToken(session)
    }

    private fun refreshAccessToken(session: Session): Boolean {
        return try {
            runBlocking {
                val refreshToken = session.refreshToken
                if (refreshToken.isBlank()) {
                    logger.warn("[${session.id}] No refresh token available — cannot refresh")
                    return@runBlocking false
                }

                val body = FormBody.Builder()
                    .add("client_id", session.clientId)
                    .add("client_secret", session.clientSecret)
                    .add("refresh_token", refreshToken)
                    .add("grant_type", "refresh_token")
                    .build()

                val request = Request.Builder()
                    .url("https://oauth2.googleapis.com/token")
                    .post(body)
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful) {
                    logger.warn("[${session.id}] Token refresh failed (HTTP ${response.code}): $responseBody")
                    return@runBlocking false
                }

                val responseJson = NewpipeManager.yamlMapper.readTree(responseBody)
                val newAccessToken = responseJson["access_token"]?.asText() ?: ""
                val expiresIn = responseJson["expires_in"]?.asLong() ?: 3600L

                if (newAccessToken.isBlank()) {
                    logger.warn("[${session.id}] No access_token in refresh response")
                    return@runBlocking false
                }

                session.accessToken = newAccessToken
                session.accessTokenExpiry = Instant.now()
                    .plusSeconds(expiresIn - 60)
                    .toString()

                logger.info("[${session.id}] ✓ Token refreshed — expires in ${expiresIn}s")
                return@runBlocking true
            }
        } catch (e: Exception) {
            logger.error("[${session.id}] Token refresh failed: ${e.message}")
            false
        }
    }
}
