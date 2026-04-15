package com.cheroliv.newpipe

import org.slf4j.LoggerFactory

/**
 * Handles age-restricted video detection and verification.
 *
 * Detects when a video requires age verification and provides appropriate
 * error messages and recovery actions based on the authentication state.
 */
class AgeVerificationHandler {

    private val logger = LoggerFactory.getLogger(AgeVerificationHandler::class.java)

    /**
     * Result of age verification check.
     */
    data class AgeVerificationResult(
        val isAgeRestricted: Boolean,
        val reason: AgeRestrictionReason,
        val message: String,
        val action: String,
        val shouldRetryWithAnotherSession: Boolean = false
    )

    /**
     * Checks if an exception indicates an age-restricted video.
     *
     * @param error the exception thrown during video info extraction or download
     * @return AgeVerificationResult with detection details
     */
    fun checkAgeRestriction(error: Throwable): AgeVerificationResult {
        val message = error.message?.lowercase() ?: ""
        val causeMessage = error.cause?.message?.lowercase() ?: ""
        val fullMessage = "$message $causeMessage"

        return when {
            // Age-gate that cannot be circumvented (must be checked first - more specific)
            fullMessage.contains("age-gate") ||
            (fullMessage.contains("unavailable in your country") && fullMessage.contains("age")) -> {
                AgeVerificationResult(
                    isAgeRestricted = true,
                    reason = AgeRestrictionReason.AGE_GATE_UNCIRCUMVENTABLE,
                    message = "🚫 Vidéo inaccessible (restriction géographique + âge)",
                    action = "Essayez avec un autre compte ou un VPN",
                    shouldRetryWithAnotherSession = false
                )
            }

            // Sign-in required (must be checked before general age check)
            fullMessage.contains("sign in") ||
            fullMessage.contains("sign-in") ||
            fullMessage.contains("signin") ||
            fullMessage.contains("log in") ||
            fullMessage.contains("login") -> {
                AgeVerificationResult(
                    isAgeRestricted = true,
                    reason = AgeRestrictionReason.SIGN_IN_REQUIRED,
                    message = "🔐 Connexion requise pour accéder à cette vidéo",
                    action = "Exécutez ./gradlew authSessions pour authentifier un compte",
                    shouldRetryWithAnotherSession = true
                )
            }

            // Minor account detected (age limit, under 18, not old enough)
            fullMessage.contains("under 18") ||
            fullMessage.contains("age limit") ||
            fullMessage.contains("not old enough") ||
            (fullMessage.contains("must be 18") && fullMessage.contains("older")) -> {
                AgeVerificationResult(
                    isAgeRestricted = true,
                    reason = AgeRestrictionReason.MINOR_ACCOUNT,
                    message = "⚠️  Compte mineur détecté - Accès refusé aux contenus 18+",
                    action = "Utilisez un compte Google majeur pour ce contenu",
                    shouldRetryWithAnotherSession = true
                )
            }

            // Age-restricted video detected (general case)
            fullMessage.contains("age") && (
                fullMessage.contains("restrict") ||
                fullMessage.contains("verification")
            ) -> {
                AgeVerificationResult(
                    isAgeRestricted = true,
                    reason = AgeRestrictionReason.AGE_VERIFICATION_REQUIRED,
                    message = "🔞 Vidéo avec restriction d'âge détectée",
                    action = "Authentifiez un compte Google majeur avec ./gradlew authSessions",
                    shouldRetryWithAnotherSession = true
                )
            }

            // Not age-restricted
            else -> {
                AgeVerificationResult(
                    isAgeRestricted = false,
                    reason = AgeRestrictionReason.NOT_AGE_RESTRICTED,
                    message = "",
                    action = "",
                    shouldRetryWithAnotherSession = false
                )
            }
        }
    }

    /**
     * Handles age-restricted video errors and returns appropriate response.
     *
     * @param videoUrl the URL of the video that failed
     * @param session the current session (null if not authenticated)
     * @param error the exception that was thrown
     * @return AgeVerificationResult with handling details
     */
    fun handleAgeRestrictedError(
        videoUrl: String,
        session: Session?,
        error: Throwable
    ): AgeVerificationResult {
        val result = checkAgeRestriction(error)

        if (!result.isAgeRestricted) {
            return result
        }

        // If we have a session, check if it might be a minor account
        // (this is a simplification - in reality, we'd need to check account age)
        if (session != null && isPossiblyMinorAccount(error)) {
            return AgeVerificationResult(
                isAgeRestricted = true,
                reason = AgeRestrictionReason.MINOR_ACCOUNT,
                message = "⚠️  Compte mineur détecté - Accès refusé aux contenus 18+",
                action = "Utilisez un compte Google majeur pour ce contenu",
                shouldRetryWithAnotherSession = true
            )
        }

        return result
    }

    /**
     * Logs the age verification result with appropriate formatting.
     *
     * @param result the verification result to log
     * @param videoUrl the URL of the video being processed
     */
    fun logAgeVerificationResult(result: AgeVerificationResult, videoUrl: String) {
        if (!result.isAgeRestricted) return

        logger.warn("\n" + "=".repeat(60))
        logger.warn(result.message)
        logger.warn("  URL: $videoUrl")
        logger.warn("  → ${result.action}")
        logger.warn("=".repeat(60))
    }

    /**
     * Checks if a video URL might be age-restricted before attempting download.
     *
     * This is a preemptive check that can be used to set expectations.
     * Note: This doesn't actually verify the video's status, just checks
     * if the URL pattern suggests it might need age verification.
     *
     * @param videoUrl the YouTube video URL to check
     * @return true if the video might be age-restricted (heuristic only)
     */
    fun mightBeAgeRestricted(videoUrl: String): Boolean {
        // This is a placeholder - in reality, age restriction can only be
        // determined by attempting to access the video
        // Future enhancement: maintain a cache of known age-restricted videos
        return false
    }

    /**
     * Heuristic to detect if an error might be from a minor account.
     *
     * This is a simplification. In practice, YouTube doesn't explicitly
     * state "minor account" in errors, so we use contextual clues.
     */
    private fun isPossiblyMinorAccount(error: Throwable): Boolean {
        val message = error.message?.lowercase() ?: ""
        val causeMessage = error.cause?.message?.lowercase() ?: ""
        val fullMessage = "$message $causeMessage"

        // Heuristics for minor account detection
        return fullMessage.contains("under 18") ||
               fullMessage.contains("age limit") ||
               fullMessage.contains("not old enough")
    }

    /**
     * Creates an AgeRestrictedVideoException from a verification result.
     *
     * @param result the verification result
     * @param videoUrl the URL of the video
     * @param originalError the original exception (optional)
     * @throws AgeRestrictedVideoException always throws
     */
    fun throwAgeRestrictedException(
        result: AgeVerificationResult,
        videoUrl: String,
        originalError: Throwable? = null
    ): Nothing {
        throw AgeRestrictedVideoException(
            reason = result.reason,
            videoUrl = videoUrl,
            message = result.message,
            cause = originalError
        )
    }
}
