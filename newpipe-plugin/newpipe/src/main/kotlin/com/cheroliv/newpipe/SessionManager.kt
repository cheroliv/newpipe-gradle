package com.cheroliv.newpipe

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Distributes authenticated [Session]s across requests using Round-Robin.
 *
 * Constructed after [AuthSessionTask] has ensured every session holds a
 * valid refresh token. Access tokens are refreshed on-demand via [TokenRefresher]
 * before each request.
 *
 * Thread-safe — shared across all concurrent coroutines in [DownloadMusicTask]
 * via [DownloaderImpl].
 *
 * A session marked invalid via [markInvalid] (HTTP 401/403 at request time)
 * is permanently excluded for the lifetime of this Gradle run.
 * If all sessions become invalid, [next] returns null → anonymous fallback.
 *
 * Tracks authentication errors per session for end-of-download reporting.
 */
class SessionManager(
    sessions: List<Session>,
    private val tokenRefresher: TokenRefresher? = null
) {

    private val logger = LoggerFactory.getLogger(SessionManager::class.java)
    private val authErrorHandler = AuthErrorHandler()

    private val all: List<Session> = sessions.toList()

    @Volatile
    private var invalidIds: Set<String> = emptySet()

    private val counter = AtomicInteger(0)

    private val errorLog = ConcurrentHashMap<String, AuthErrorHandler.ErrorResult>()

    init {
        if (all.isEmpty()) {
            logger.warn("SessionManager initialised with no sessions — anonymous mode")
        } else {
            logger.info("SessionManager ready — ${all.size} session(s): ${all.map { it.id }}")
        }
    }

    /**
     * Returns the next valid [Session] in Round-Robin order.
     * Refreshes the access token if needed before returning.
     * Returns null if no valid session is available → caller uses anonymous mode.
     */
    fun next(): Session? {
        val valid = all.filter { it.id !in invalidIds }
        if (valid.isEmpty()) return null
        
        val session = valid[counter.getAndIncrement() % valid.size]
        
        tokenRefresher?.let { refresher ->
            if (!refresher.refreshIfNeeded(session)) {
                logger.warn("[${session.id}] Token refresh failed — marking session invalid")
                markInvalid(session.id)
                return next()
            }
        }
        
        return session
    }

    /**
     * Permanently marks [sessionId] as invalid for this Gradle run.
     * Called by [DownloaderImpl] on HTTP 401/403.
     *
     * @param sessionId The session ID to mark invalid
     * @param errorResult Optional error result for detailed logging
     */
    fun markInvalid(sessionId: String, errorResult: AuthErrorHandler.ErrorResult? = null) {
        invalidIds = invalidIds + sessionId
        errorResult?.let { errorLog[sessionId] = it }
        val remaining = all.size - invalidIds.size
        logger.warn("Session '$sessionId' marked invalid — $remaining session(s) remaining")
        if (remaining == 0) logger.warn("All sessions invalid — falling back to anonymous mode")
    }

    /**
     * Records an authentication error for later reporting without marking session invalid.
     * Useful for transient errors like quota exceeded.
     */
    fun recordError(sessionId: String, errorResult: AuthErrorHandler.ErrorResult) {
        errorLog[sessionId] = errorResult
    }

    /**
     * Returns all recorded errors for end-of-download reporting.
     */
    fun getErrors(): Map<String, AuthErrorHandler.ErrorResult> = errorLog.toMap()

    /**
     * Logs a summary of all authentication errors encountered.
     */
    fun logErrorSummary() {
        authErrorHandler.logErrorSummary(errorLog.toMap())
    }

    /** True if at least one session is still valid. */
    fun hasValidSession(): Boolean = all.any { it.id !in invalidIds }
}