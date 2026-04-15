package com.cheroliv.newpipe

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PrivatePlaylistHandlerTest {

    private val handler = PrivatePlaylistHandler()

    @Test
    fun `detects private playlist error from exception message`() {
        val exception = Exception("Private playlist: Access denied")
        val result = handler.isPrivatePlaylistError(exception)

        assertThat(result).isTrue()
    }

    @Test
    fun `detects 403 forbidden error as private playlist`() {
        val exception = Exception("403 Forbidden: Playlist access denied")
        val result = handler.isPrivatePlaylistError(exception)

        assertThat(result).isTrue()
    }

    @Test
    fun `detects unauthorized error as private playlist`() {
        val exception = Exception("Unauthorized: You do not have access to this playlist")
        val result = handler.isPrivatePlaylistError(exception)

        assertThat(result).isTrue()
    }

    @Test
    fun `returns false for normal playlist error`() {
        val exception = Exception("Playlist not found")
        val result = handler.isPrivatePlaylistError(exception)

        assertThat(result).isFalse()
    }

    @Test
    fun `returns false for geo-restricted error`() {
        val exception = Exception("This video is not available in your country")
        val result = handler.isPrivatePlaylistError(exception)

        assertThat(result).isFalse()
    }

    @Test
    fun `handles private playlist error without session`() {
        val exception = Exception("Private playlist: Access denied")
        val result = handler.handlePrivatePlaylistError("https://youtube.com/playlist?list=test", null, exception)

        assertThat(result.isPrivate).isTrue()
        assertThat(result.reason).isEqualTo(PrivatePlaylistReason.AUTHENTICATION_REQUIRED)
        assertThat(result.message).contains("authentication")
        assertThat(result.action).contains("authSessions")
        assertThat(result.shouldSkip).isTrue()
    }

    @Test
    fun `handles private playlist error with invalid session`() {
        val session = Session(
            credentials = SessionCredentials(
                id = "test-account",
                clientId = "client-id",
                clientSecret = "client-secret",
                refreshToken = "refresh-token"
            ),
            accessToken = "",
            accessTokenExpiry = ""
        )
        val exception = Exception("403 Forbidden")
        val result = handler.handlePrivatePlaylistError("https://youtube.com/playlist?list=test", session, exception)

        assertThat(result.isPrivate).isTrue()
        assertThat(result.reason).isEqualTo(PrivatePlaylistReason.SIGN_IN_REQUIRED)
        assertThat(result.shouldSkip).isTrue()
    }

    @Test
    fun `handles private playlist error with valid session as wrong account`() {
        val session = Session(
            credentials = SessionCredentials(
                id = "test-account",
                clientId = "client-id",
                clientSecret = "client-secret",
                refreshToken = "refresh-token"
            ),
            accessToken = "access-token",
            accessTokenExpiry = "2099-12-31T23:59:59Z"
        )
        val exception = Exception("Private playlist: Access denied")
        val result = handler.handlePrivatePlaylistError("https://youtube.com/playlist?list=test", session, exception)

        assertThat(result.isPrivate).isTrue()
        assertThat(result.reason).isEqualTo(PrivatePlaylistReason.WRONG_ACCOUNT)
        assertThat(result.message).contains("not accessible with this account")
        assertThat(result.shouldRetryWithAnotherSession).isTrue()
    }

    @Test
    fun `handles access forbidden error`() {
        val exception = Exception("403 Forbidden: Access denied")
        val result = handler.handlePrivatePlaylistError("https://youtube.com/playlist?list=test", null, exception)

        assertThat(result.isPrivate).isTrue()
        assertThat(result.reason).isEqualTo(PrivatePlaylistReason.AUTHENTICATION_REQUIRED)
    }

    @Test
    fun `returns not private for non-private errors`() {
        val exception = Exception("Playlist not found")
        val result = handler.handlePrivatePlaylistError("https://youtube.com/playlist?list=test", null, exception)

        assertThat(result.isPrivate).isFalse()
        assertThat(result.reason).isEqualTo(PrivatePlaylistReason.NOT_PRIVATE)
        assertThat(result.shouldSkip).isFalse()
    }

    @Test
    fun `creates authentication required result`() {
        val result = PrivatePlaylistHandler.PrivatePlaylistResult.authenticationRequired("https://youtube.com/playlist?list=test")

        assertThat(result.isPrivate).isTrue()
        assertThat(result.reason).isEqualTo(PrivatePlaylistReason.AUTHENTICATION_REQUIRED)
        assertThat(result.message).contains("authentication")
        assertThat(result.shouldSkip).isTrue()
        assertThat(result.shouldRetryWithAnotherSession).isFalse()
    }

    @Test
    fun `creates wrong account result`() {
        val result = PrivatePlaylistHandler.PrivatePlaylistResult.wrongAccount("https://youtube.com/playlist?list=test")

        assertThat(result.isPrivate).isTrue()
        assertThat(result.reason).isEqualTo(PrivatePlaylistReason.WRONG_ACCOUNT)
        assertThat(result.message).contains("not accessible")
        assertThat(result.shouldSkip).isTrue()
        assertThat(result.shouldRetryWithAnotherSession).isTrue()
    }

    @Test
    fun `creates accessible result`() {
        val result = PrivatePlaylistHandler.PrivatePlaylistResult.accessible()

        assertThat(result.isPrivate).isTrue()
        assertThat(result.reason).isEqualTo(PrivatePlaylistReason.PRIVATE_ACCESSIBLE)
        assertThat(result.shouldSkip).isFalse()
    }

    @Test
    fun `creates not private result`() {
        val result = PrivatePlaylistHandler.PrivatePlaylistResult.notPrivate()

        assertThat(result.isPrivate).isFalse()
        assertThat(result.reason).isEqualTo(PrivatePlaylistReason.NOT_PRIVATE)
        assertThat(result.shouldSkip).isFalse()
    }

    @Test
    fun `creates access forbidden result`() {
        val result = PrivatePlaylistHandler.PrivatePlaylistResult.accessForbidden("https://youtube.com/playlist?list=test")

        assertThat(result.isPrivate).isTrue()
        assertThat(result.reason).isEqualTo(PrivatePlaylistReason.ACCESS_FORBIDDEN)
        assertThat(result.shouldSkip).isTrue()
    }

    @Test
    fun `creates sign in required result`() {
        val result = PrivatePlaylistHandler.PrivatePlaylistResult.signInRequired("https://youtube.com/playlist?list=test")

        assertThat(result.isPrivate).isTrue()
        assertThat(result.reason).isEqualTo(PrivatePlaylistReason.SIGN_IN_REQUIRED)
        assertThat(result.shouldSkip).isTrue()
    }

    @Test
    fun `logs private playlist result with warning`() {
        val result = PrivatePlaylistHandler.PrivatePlaylistResult.authenticationRequired("https://youtube.com/playlist?list=test")

        handler.logPrivatePlaylistResult(result, "https://youtube.com/playlist?list=test")
    }

    @Test
    fun `logs accessible private playlist with info`() {
        val result = PrivatePlaylistHandler.PrivatePlaylistResult.accessible()

        handler.logPrivatePlaylistResult(result, "https://youtube.com/playlist?list=test")
    }

    @Test
    fun `does not log for public playlist`() {
        val result = PrivatePlaylistHandler.PrivatePlaylistResult.notPrivate()

        handler.logPrivatePlaylistResult(result, "https://youtube.com/playlist?list=test")
    }

    @Test
    fun `detects private playlist error from cause`() {
        val cause = Exception("Private playlist: Access denied")
        val exception = Exception("Failed to fetch playlist", cause)
        val result = handler.isPrivatePlaylistError(exception)

        assertThat(result).isTrue()
    }

    @Test
    fun `handles exception with access denied in message`() {
        val exception = Exception("Access denied to playlist")
        val result = handler.isPrivatePlaylistError(exception)

        assertThat(result).isTrue()
    }
}
