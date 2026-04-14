package com.cheroliv.newpipe

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import java.time.Instant

data class Selection(
    val artistes: List<Artist> = emptyList()
) {
    data class Artist(
        val name: String,
        val tunes: List<String> = emptyList(),
        val playlists: List<String> = emptyList()
    )
}

/**
 * Persisted credentials for a single Google account.
 * Serialised to/from sessions.yml — only clientId, clientSecret
 * and refreshToken are persisted. Everything else lives in memory.
 */
data class SessionCredentials(
    val id: String,
    val clientId: String,
    val clientSecret: String,
    var refreshToken: String = ""
) {
    fun toSession(): Session? {
        if (refreshToken.isBlank()) return null
        return Session(this)
    }
}

/**
 * YAML root — maps directly to sessions.yml.
 */
data class SessionConfig(
    @field:JsonSetter(nulls = Nulls.AS_EMPTY)
    val sessions: MutableList<SessionCredentials> = mutableListOf()
)

/**
 * Runtime-only representation of an authenticated session.
 * Never serialised — lives only for the duration of [DownloadMusicTask].
 * Built by [NewpipeManager.buildSessionManager] from [SessionCredentials]
 * after a silent access token refresh.
 */
data class Session(
    val credentials: SessionCredentials,
    var accessToken: String = "",
    var accessTokenExpiry: String = ""
) {
    val id:           String get() = credentials.id
    val clientId:     String get() = credentials.clientId
    val clientSecret: String get() = credentials.clientSecret
    val refreshToken: String get() = credentials.refreshToken

    fun isAccessTokenValid(): Boolean {
        if (accessToken.isBlank() || accessTokenExpiry.isBlank()) return false
        return try {
            Instant.parse(accessTokenExpiry).isAfter(Instant.now().plusSeconds(60))
        } catch (_: Exception) { false }
    }
}