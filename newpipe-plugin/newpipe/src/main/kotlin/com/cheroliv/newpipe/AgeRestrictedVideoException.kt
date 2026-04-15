package com.cheroliv.newpipe

/**
 * Exception thrown when a video is age-restricted and cannot be accessed
 * without proper authentication or age verification.
 */
class AgeRestrictedVideoException(
    val reason: AgeRestrictionReason,
    val videoUrl: String,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Reasons why a video is age-restricted or inaccessible.
 */
enum class AgeRestrictionReason {
    /**
     * Video requires authentication to verify age (18+ content).
     */
    AGE_VERIFICATION_REQUIRED,

    /**
     * Video is age-restricted but user is authenticated with a minor account.
     */
    MINOR_ACCOUNT,

    /**
     * Video is age-gated and cannot be accessed even with authentication
     * (e.g., geographic restrictions + age verification).
     */
    AGE_GATE_UNCIRCUMVENTABLE,

    /**
     * Video requires sign-in but no session is available.
     */
    SIGN_IN_REQUIRED,

    /**
     * Video is not age-restricted (used for non-age-restricted results).
     */
    NOT_AGE_RESTRICTED
}
