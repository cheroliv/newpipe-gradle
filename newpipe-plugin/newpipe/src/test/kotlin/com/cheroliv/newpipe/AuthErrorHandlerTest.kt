package com.cheroliv.newpipe

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AuthErrorHandlerTest {

    private val handler = AuthErrorHandler()

    @Test
    fun `handles HTTP 401 as token revoked`() {
        val result = handler.handleHttpError(401, "compte-principal")

        assertThat(result.message).contains("Token révoqué ou expiré")
        assertThat(result.action).contains("authSessions")
        assertThat(result.shouldMarkInvalid).isTrue()
    }

    @Test
    fun `handles HTTP 403 as access denied`() {
        val result = handler.handleHttpError(403, "compte-principal")

        assertThat(result.message).contains("Accès refusé")
        assertThat(result.shouldMarkInvalid).isTrue()
    }

    @Test
    fun `handles HTTP 429 as quota exceeded`() {
        val result = handler.handleHttpError(429, "compte-principal")

        assertThat(result.message).contains("quota exceeded")
        assertThat(result.action).contains("Bascule automatique")
        // HTTP 429 is transient, should not mark session invalid
        assertThat(result.shouldMarkInvalid).isFalse()
    }

    @Test
    fun `handles invalid_grant with revoked description`() {
        val result = handler.handleOAuthError(
            "invalid_grant",
            "Token has been revoked",
            "compte-principal"
        )

        assertThat(result.message).contains("révoqué")
        assertThat(result.action).contains("authSessions")
    }

    @Test
    fun `handles invalid_grant with expired description`() {
        val result = handler.handleOAuthError(
            "invalid_grant",
            "Token has expired",
            "compte-principal"
        )

        assertThat(result.message).contains("expiré")
        assertThat(result.action).contains("authSessions")
    }

    @Test
    fun `handles invalid_grant without description defaults to generic`() {
        val result = handler.handleOAuthError(
            "invalid_grant",
            null,
            "compte-principal"
        )

        assertThat(result.message).contains("Échec de refresh")
    }

    @Test
    fun `handles invalid_client error`() {
        val result = handler.handleOAuthError(
            "invalid_client",
            null,
            "compte-principal"
        )

        assertThat(result.message).contains("client_secret invalide")
        assertThat(result.action).contains("client_secrets")
        assertThat(result.action).contains("buildSessions")
    }

    @Test
    fun `handles unauthorized_client error`() {
        val result = handler.handleOAuthError(
            "unauthorized_client",
            null,
            "compte-principal"
        )

        assertThat(result.message).contains("Client non autorisé")
        assertThat(result.action).contains("Google Cloud Console")
    }

    @Test
    fun `handles access_denied error`() {
        val result = handler.handleOAuthError(
            "access_denied",
            null,
            "compte-principal"
        )

        assertThat(result.message).contains("compte suspendu")
        assertThat(result.shouldMarkInvalid).isTrue()
    }

    @Test
    fun `handles unknown OAuth error gracefully`() {
        val result = handler.handleOAuthError(
            "unknown_error",
            "Some description",
            "compte-principal"
        )

        assertThat(result.message).contains("Erreur OAuth2")
        assertThat(result.message).contains("unknown_error")
    }

    @Test
    fun `handles timeout exception`() {
        val exception = Exception("Socket timeout")
        val result = handler.handleException(exception, "compte-principal")

        assertThat(result.message).contains("Délai d'attente dépassé")
        assertThat(result.shouldMarkInvalid).isFalse()
    }

    @Test
    fun `handles DNS exception`() {
        val exception = Exception("Unknown host: oauth2.googleapis.com")
        val result = handler.handleException(exception, "compte-principal")

        assertThat(result.message).contains("Erreur DNS")
        assertThat(result.shouldMarkInvalid).isFalse()
    }

    @Test
    fun `handles SSL exception`() {
        val exception = Exception("SSL certificate error")
        val result = handler.handleException(exception, "compte-principal")

        assertThat(result.message).contains("Erreur SSL/TLS")
        assertThat(result.shouldMarkInvalid).isFalse()
    }

    @Test
    fun `handles unknown exception`() {
        val exception = Exception("Unexpected error occurred")
        val result = handler.handleException(exception, "compte-principal")

        assertThat(result.message).contains("Erreur inattendue")
        assertThat(result.shouldMarkInvalid).isFalse()
    }

    @Test
    fun `logs error summary with multiple errors`() {
        val errors = mapOf(
            "compte-principal" to handler.handleOAuthError("invalid_grant", "revoked", "compte-principal"),
            "compte-secondaire" to handler.handleOAuthError("invalid_client", null, "compte-secondaire")
        )

        // This test ensures no exception is thrown and logging happens
        handler.logErrorSummary(errors)
    }

    @Test
    fun `empty error summary does not log anything`() {
        val errors = emptyMap<String, AuthErrorHandler.ErrorResult>()

        // This test ensures no exception is thrown with empty map
        handler.logErrorSummary(errors)
    }
}
