package com.cheroliv.newpipe

import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Distributes authenticated [Session]s across requests using Round-Robin.
 *
 * Constructed after [AuthSessionTask] has ensured every session holds a
 * valid [Session.accessToken] — no token refresh happens here.
 *
 * Thread-safe — shared across all concurrent coroutines in [DownloadMusicTask]
 * via [DownloaderImpl].
 *
 * A session marked invalid via [markInvalid] (HTTP 401/403 at request time)
 * is permanently excluded for the lifetime of this Gradle run.
 * If all sessions become invalid, [next] returns null → anonymous fallback.
 */
class SessionManager(sessions: List<Session>) {

    private val logger = LoggerFactory.getLogger(SessionManager::class.java)

    private val all: List<Session> = sessions.toList()

    @Volatile
    private var invalidIds: Set<String> = emptySet()

    private val counter = AtomicInteger(0)

    init {
        if (all.isEmpty()) {
            logger.warn("SessionManager initialised with no sessions — anonymous mode")
        } else {
            logger.info("SessionManager ready — ${all.size} session(s): ${all.map { it.id }}")
        }
    }

    /**
     * Returns the next valid [Session] in Round-Robin order.
     * Returns null if no valid session is available → caller uses anonymous mode.
     */
    fun next(): Session? {
        val valid = all.filter { it.id !in invalidIds }
        if (valid.isEmpty()) return null
        return valid[counter.getAndIncrement() % valid.size]
    }

    /**
     * Permanently marks [sessionId] as invalid for this Gradle run.
     * Called by [DownloaderImpl] on HTTP 401/403.
     */
    fun markInvalid(sessionId: String) {
        invalidIds = invalidIds + sessionId
        val remaining = all.size - invalidIds.size
        logger.warn("Session '$sessionId' marked invalid — $remaining session(s) remaining")
        if (remaining == 0) logger.warn("All sessions invalid — falling back to anonymous mode")
    }

    /** True if at least one session is still valid. */
    fun hasValidSession(): Boolean = all.any { it.id !in invalidIds }
}