package com.cheroliv.newpipe

import org.slf4j.LoggerFactory

/**
 * Handler for detecting and managing private playlist errors.
 */
class PrivatePlaylistHandler {

    private val logger = LoggerFactory.getLogger(PrivatePlaylistHandler::class.java)

    /**
     * Result of handling a private playlist error.
     */
    data class PrivatePlaylistResult(
        val isPrivate: Boolean,
        val reason: PrivatePlaylistReason,
        val message: String,
        val action: String,
        val shouldSkip: Boolean = true,
        val shouldRetryWithAnotherSession: Boolean = false
    ) {
        companion object {
            fun authenticationRequired(playlistUrl: String) = PrivatePlaylistResult(
                isPrivate = true,
                reason = PrivatePlaylistReason.AUTHENTICATION_REQUIRED,
                message = "Private playlist requires authentication",
                action = "Authentifiez un compte avec ./gradlew authSessions",
                shouldSkip = true
            )

            fun wrongAccount(playlistUrl: String) = PrivatePlaylistResult(
                isPrivate = true,
                reason = PrivatePlaylistReason.WRONG_ACCOUNT,
                message = "Playlist not accessible with this account",
                action = "Utilisez la session du compte propriétaire de la playlist",
                shouldSkip = true,
                shouldRetryWithAnotherSession = true
            )

            fun accessible() = PrivatePlaylistResult(
                isPrivate = true,
                reason = PrivatePlaylistReason.PRIVATE_ACCESSIBLE,
                message = "Private playlist accessible with current session",
                action = "Continuing download",
                shouldSkip = false
            )

            fun notPrivate() = PrivatePlaylistResult(
                isPrivate = false,
                reason = PrivatePlaylistReason.NOT_PRIVATE,
                message = "Playlist is public",
                action = "Continuing download",
                shouldSkip = false
            )

            fun accessForbidden(playlistUrl: String) = PrivatePlaylistResult(
                isPrivate = true,
                reason = PrivatePlaylistReason.ACCESS_FORBIDDEN,
                message = "Access forbidden to playlist",
                action = "Verify playlist URL and authentication",
                shouldSkip = true
            )

            fun signInRequired(playlistUrl: String) = PrivatePlaylistResult(
                isPrivate = true,
                reason = PrivatePlaylistReason.SIGN_IN_REQUIRED,
                message = "Sign-in required to access playlist",
                action = "Authentifiez un compte avec ./gradlew authSessions",
                shouldSkip = true
            )
        }
    }

    /**
     * Checks if an error is related to a private playlist.
     */
    fun isPrivatePlaylistError(error: Throwable): Boolean {
        val msg = error.message?.lowercase() ?: ""
        val causeMsg = error.cause?.message?.lowercase() ?: ""
        val full = "$msg $causeMsg"

        return full.contains("private") && full.contains("playlist") ||
                full.contains("403") && full.contains("forbidden") ||
                full.contains("access denied") ||
                full.contains("unauthorized") ||
                error.cause?.let { isPrivatePlaylistError(it) } == true
    }

    /**
     * Handles a private playlist error and returns the appropriate result.
     */
    fun handlePrivatePlaylistError(
        playlistUrl: String,
        session: Session?,
        error: Throwable
    ): PrivatePlaylistResult {
        return when {
            error.isPrivatePlaylistError() && session == null ->
                PrivatePlaylistResult.authenticationRequired(playlistUrl)

            error.isPrivatePlaylistError() && session != null && !session.isAccessTokenValid() ->
                PrivatePlaylistResult.signInRequired(playlistUrl)

            error.isPrivatePlaylistError() && session != null ->
                PrivatePlaylistResult.wrongAccount(playlistUrl)

            error.isAccessForbidden() && session == null ->
                PrivatePlaylistResult.authenticationRequired(playlistUrl)

            error.isAccessForbidden() && session != null && !session.isAccessTokenValid() ->
                PrivatePlaylistResult.signInRequired(playlistUrl)

            error.isAccessForbidden() && session != null ->
                PrivatePlaylistResult.accessForbidden(playlistUrl)

            else ->
                PrivatePlaylistResult.notPrivate()
        }
    }

    /**
     * Logs the result of private playlist handling.
     */
    fun logPrivatePlaylistResult(result: PrivatePlaylistResult, playlistUrl: String) {
        when {
            result.reason == PrivatePlaylistReason.NOT_PRIVATE -> {
                logger.debug("Playlist public: $playlistUrl")
            }
            result.reason == PrivatePlaylistReason.PRIVATE_ACCESSIBLE -> {
                logger.info("🔓 Playlist privée accessible: $playlistUrl")
            }
            result.shouldSkip -> {
                logger.warn("⏭ Playlist privée non accessible: $playlistUrl")
                logger.warn("   Raison: ${result.message}")
                logger.warn("   Action: ${result.action}")
            }
        }
    }
}

private fun Throwable.isAccessForbidden(): Boolean {
    val msg = message?.lowercase() ?: return false
    return msg.contains("403") ||
            msg.contains("forbidden") ||
            msg.contains("access denied") ||
            msg.contains("unauthorized") ||
            cause?.isAccessForbidden() == true
}

private fun Throwable.isPrivatePlaylistError(): Boolean {
    val msg = message?.lowercase() ?: ""
    val causeMsg = cause?.message?.lowercase() ?: ""
    val full = "$msg $causeMsg"

    return full.contains("private") && (
            full.contains("playlist") ||
            full.contains("access") ||
            full.contains("permission")
            ) || cause?.isPrivatePlaylistError() == true
}
