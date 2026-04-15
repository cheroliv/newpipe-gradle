package com.cheroliv.newpipe

import org.slf4j.LoggerFactory

/**
 * Maps OAuth2 error codes and HTTP status codes to user-friendly messages.
 *
 * Handles the following error scenarios:
 * - Token revoked by user
 * - Token expired (1 year validity)
 * - Invalid client_secret
 * - API quota exceeded
 * - Account suspended
 */
class AuthErrorHandler {

    private val logger = LoggerFactory.getLogger(AuthErrorHandler::class.java)

    /**
     * Represents the severity of an authentication error.
     */
    enum class Severity {
        WARNING,    // Can be retried with another session
        ERROR,      // Requires user action
        CRITICAL    // Session permanently invalid
    }

    /**
     * Result of handling an authentication error.
     */
    data class ErrorResult(
        val message: String,
        val action: String,
        val severity: Severity,
        val shouldMarkInvalid: Boolean = true
    )

    /**
     * Handles HTTP status codes related to authentication failures.
     */
    fun handleHttpError(statusCode: Int, sessionId: String): ErrorResult {
        return when (statusCode) {
            401 -> ErrorResult(
                message = "Session '$sessionId' : Token révoqué ou expiré",
                action = "Exécutez ./gradlew authSessions pour ré-authentifier",
                severity = Severity.WARNING
            )
            403 -> ErrorResult(
                message = "Session '$sessionId' : Accès refusé (quota dépassé ou compte suspendu)",
                action = if (isQuotaExceeded(sessionId)) {
                    "Bascule automatique sur une autre session"
                } else {
                    "Vérifiez l'état du compte Google"
                },
                severity = Severity.ERROR
            )
            429 -> ErrorResult(
                message = "YouTube API quota exceeded pour '$sessionId'",
                action = "Bascule automatique sur une autre session ou attendez 24h",
                severity = Severity.WARNING,
                shouldMarkInvalid = false
            )
            else -> ErrorResult(
                message = "Session '$sessionId' : Erreur HTTP $statusCode",
                action = "Vérifiez la connexion réseau",
                severity = Severity.ERROR,
                shouldMarkInvalid = false
            )
        }
    }

    /**
     * Handles OAuth2 error responses from token refresh or authentication.
     */
    fun handleOAuthError(error: String, errorDescription: String?, sessionId: String): ErrorResult {
        return when (error.lowercase()) {
            "invalid_grant" -> {
                when {
                    errorDescription?.contains("revoked", ignoreCase = true) == true ->
                        ErrorResult(
                            message = "Session '$sessionId' : Token révoqué par l'utilisateur",
                            action = "Exécutez ./gradlew authSessions pour ré-authentifier",
                            severity = Severity.WARNING
                        )
                    errorDescription?.contains("expired", ignoreCase = true) == true ->
                        ErrorResult(
                            message = "Session '$sessionId' : Token expiré (valable 1 an)",
                            action = "Exécutez ./gradlew authSessions pour renouveler",
                            severity = Severity.WARNING
                        )
                    else ->
                        ErrorResult(
                            message = "Session '$sessionId' : Échec de refresh du token",
                            action = "Exécutez ./gradlew authSessions",
                            severity = Severity.WARNING
                        )
                }
            }
            "invalid_client" -> {
                ErrorResult(
                    message = "Session '$sessionId' : client_secret invalide",
                    action = "Vérifiez client_secrets/$sessionId.json ou recréez avec ./gradlew buildSessions",
                    severity = Severity.CRITICAL
                )
            }
            "unauthorized_client" -> {
                ErrorResult(
                    message = "Session '$sessionId' : Client non autorisé",
                    action = "Vérifiez la configuration OAuth2 dans Google Cloud Console",
                    severity = Severity.CRITICAL
                )
            }
            "access_denied" -> {
                ErrorResult(
                    message = "Session '$sessionId' : Accès refusé (compte suspendu ou restrictions)",
                    action = "Session désactivée, utilisation des autres sessions",
                    severity = Severity.CRITICAL
                )
            }
            else -> {
                ErrorResult(
                    message = "Session '$sessionId' : Erreur OAuth2 ($error)",
                    action = "Vérifiez les logs pour plus de détails",
                    severity = Severity.ERROR
                )
            }
        }
    }

    /**
     * Handles exceptions during authentication or token refresh.
     */
    fun handleException(exception: Exception, sessionId: String): ErrorResult {
        val message = exception.message?.lowercase() ?: ""
        return when {
            message.contains("socket timeout") || message.contains("timeout") ->
                ErrorResult(
                    message = "Session '$sessionId' : Délai d'attente dépassé",
                    action = "Vérifiez la connexion réseau",
                    severity = Severity.ERROR,
                    shouldMarkInvalid = false
                )
            message.contains("unknown host") || message.contains("dns") ->
                ErrorResult(
                    message = "Session '$sessionId' : Erreur DNS",
                    action = "Vérifiez la connexion réseau",
                    severity = Severity.ERROR,
                    shouldMarkInvalid = false
                )
            message.contains("ssl") || message.contains("certificate") ->
                ErrorResult(
                    message = "Session '$sessionId' : Erreur SSL/TLS",
                    action = "Vérifiez la date système et les certificats",
                    severity = Severity.ERROR,
                    shouldMarkInvalid = false
                )
            else ->
                ErrorResult(
                    message = "Session '$sessionId' : Erreur inattendue (${exception.message})",
                    action = "Consultez les logs détaillés",
                    severity = Severity.ERROR,
                    shouldMarkInvalid = false
                )
        }
    }

    /**
     * Logs the error result with appropriate level based on severity.
     */
    fun logError(result: ErrorResult) {
        val emoji = when (result.severity) {
            Severity.WARNING -> "⚠️"
            Severity.ERROR -> "❌"
            Severity.CRITICAL -> "🚫"
        }

        when (result.severity) {
            Severity.WARNING -> logger.warn("$emoji ${result.message}")
            Severity.ERROR -> logger.error("$emoji ${result.message}")
            Severity.CRITICAL -> logger.error("$emoji ${result.message}")
        }

        logger.info("   → ${result.action}")
    }

    /**
     * Logs a summary of session errors at the end of a download.
     *
     * @param errors Map of session ID to error results
     */
    fun logErrorSummary(errors: Map<String, ErrorResult>) {
        if (errors.isEmpty()) return

        logger.info("\n" + "=".repeat(60))
        logger.info("  Résumé des Erreurs d'Authentification")
        logger.info("=".repeat(60))

        errors.forEach { (sessionId, result) ->
            val emoji = when (result.severity) {
                Severity.WARNING -> "⚠️"
                Severity.ERROR -> "❌"
                Severity.CRITICAL -> "🚫"
            }
            logger.info("$emoji [$sessionId] ${result.message}")
            logger.info("   → ${result.action}")
        }

        logger.info("=".repeat(60))
    }

    /**
     * Checks if a session should be marked as invalid based on error result.
     */
    fun shouldMarkInvalid(result: ErrorResult): Boolean = result.shouldMarkInvalid

    /**
     * Helper to detect quota exceeded errors from error descriptions.
     */
    private fun isQuotaExceeded(sessionId: String): Boolean {
        // This could be enhanced by tracking quota usage per session
        // For now, we assume 403 with specific patterns indicate quota
        return false
    }
}
