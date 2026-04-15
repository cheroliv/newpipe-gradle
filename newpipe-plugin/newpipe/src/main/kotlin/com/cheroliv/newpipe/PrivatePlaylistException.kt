package com.cheroliv.newpipe

/**
 * Exception thrown when a playlist is private and cannot be accessed
 * without proper authentication or with the wrong account.
 */
class PrivatePlaylistException(
    val reason: PrivatePlaylistReason,
    val playlistUrl: String,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Reasons why a playlist is private or inaccessible.
 */
enum class PrivatePlaylistReason {
    /**
     * Playlist is private and requires authentication.
     */
    AUTHENTICATION_REQUIRED,

    /**
     * Playlist is private but user is authenticated with wrong account.
     */
    WRONG_ACCOUNT,

    /**
     * Playlist is private but accessible with current session.
     */
    PRIVATE_ACCESSIBLE,

    /**
     * Playlist is public (not private).
     */
    NOT_PRIVATE,

    /**
     * Playlist access forbidden (403) - could be private or removed.
     */
    ACCESS_FORBIDDEN,

    /**
     * Playlist requires sign-in but no session is available.
     */
    SIGN_IN_REQUIRED
}
