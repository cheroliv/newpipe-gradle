package com.cheroliv.newpipe

import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks runtime statistics for each session during a Gradle run.
 * Used by [SessionStatusTask] to display the state of sessions.
 *
 * Thread-safe — updated by [SessionManager] on each request.
 */
class SessionMonitor {

    private val logger = LoggerFactory.getLogger(SessionMonitor::class.java)

    data class SessionStats(
        val id: String,
        val credentials: SessionCredentials,
        var accessTokenExpiry: String = "",
        var requestCount: Int = 0,
        var lastUsed: Instant? = null,
        var isValid: Boolean = true
    )

    private val stats = ConcurrentHashMap<String, SessionStats>()

    init {
        logger.info("SessionMonitor initialised")
    }

    /**
     * Registers a session for monitoring.
     * Called by [SessionManager] at startup.
     */
    fun register(session: Session) {
        stats[session.id] = SessionStats(
            id = session.id,
            credentials = session.credentials
        )
        logger.debug("Session '${session.id}' registered for monitoring")
    }

    /**
     * Records that a session was used for a request.
     * Called by [SessionManager.next()] after successful token refresh.
     */
    fun recordUsage(sessionId: String, accessTokenExpiry: String) {
        stats[sessionId]?.let { s ->
            s.requestCount++
            s.lastUsed = Instant.now()
            s.accessTokenExpiry = accessTokenExpiry
            s.isValid = true
        }
    }

    /**
     * Marks a session as invalid (e.g., HTTP 401/403).
     * Called by [SessionManager.markInvalid()].
     */
    fun markInvalid(sessionId: String) {
        stats[sessionId]?.let { s ->
            s.isValid = false
        }
        logger.debug("Session '$sessionId' marked invalid in monitor")
    }

    /**
     * Updates the access token expiry for a session.
     * Called after token refresh.
     */
    fun updateTokenExpiry(sessionId: String, expiry: String) {
        stats[sessionId]?.let { s ->
            s.accessTokenExpiry = expiry
        }
    }

    /**
     * Returns all session statistics for display.
     */
    fun getAllStats(): Collection<SessionStats> = stats.values.toList()

    /**
     * Returns the number of requests made today (since midnight).
     */
    fun getTodayRequestCount(sessionId: String): Int {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val todayStart = now.toLocalDate().atStartOfDay(now.zone).toInstant()
        
        return stats[sessionId]?.let { s ->
            if (s.lastUsed != null && s.lastUsed!! >= todayStart) s.requestCount else 0
        } ?: 0
    }

    /**
     * Returns a formatted string showing the time since last use.
     */
    fun getLastUsedString(sessionId: String): String {
        val lastUsed = stats[sessionId]?.lastUsed ?: return "Never"
        
        val now = Instant.now()
        val seconds = now.epochSecond - lastUsed.epochSecond
        
        return when {
            seconds < 60 -> "just now"
            seconds < 3600 -> "${seconds / 60} minutes ago"
            seconds < 86400 -> "${seconds / 3600} hours ago"
            else -> "${seconds / 86400} days ago"
        }
    }

    /**
     * Returns the expiry date formatted for display.
     */
    fun getExpiryDateString(sessionId: String): String {
        val expiry = stats[sessionId]?.accessTokenExpiry ?: return "Unknown"
        return try {
            val instant = Instant.parse(expiry)
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).format(formatter)
        } catch (e: Exception) {
            expiry
        }
    }

    /**
     * Returns true if the session's token is still valid.
     */
    fun isTokenValid(sessionId: String): Boolean {
        val expiry = stats[sessionId]?.accessTokenExpiry ?: return false
        return try {
            Instant.parse(expiry).isAfter(Instant.now())
        } catch (e: Exception) {
            false
        }
    }
}
