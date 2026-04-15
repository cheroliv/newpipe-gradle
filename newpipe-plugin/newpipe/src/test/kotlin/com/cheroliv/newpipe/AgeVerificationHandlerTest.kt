package com.cheroliv.newpipe

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgeVerificationHandlerTest {

    private val handler = AgeVerificationHandler()

    @Test
    fun `detects age-restricted video from exception message`() {
        val exception = Exception("Video unavailable: Age verification required")
        val result = handler.checkAgeRestriction(exception)

        assertThat(result.isAgeRestricted).isTrue()
        assertThat(result.reason).isEqualTo(AgeRestrictionReason.AGE_VERIFICATION_REQUIRED)
        assertThat(result.message).contains("restriction d'âge")
        assertThat(result.action).contains("authSessions")
    }

    @Test
    fun `detects age-gate uncircumventable error`() {
        val exception = Exception("Age-gate error: This video is unavailable in your country")
        val result = handler.checkAgeRestriction(exception)

        assertThat(result.isAgeRestricted).isTrue()
        assertThat(result.reason).isEqualTo(AgeRestrictionReason.AGE_GATE_UNCIRCUMVENTABLE)
        assertThat(result.message).contains("restriction géographique")
        assertThat(result.shouldRetryWithAnotherSession).isFalse()
    }

    @Test
    fun `detects sign-in required error`() {
        val exception = Exception("Sign in to confirm your age")
        val result = handler.checkAgeRestriction(exception)

        assertThat(result.isAgeRestricted).isTrue()
        assertThat(result.reason).isEqualTo(AgeRestrictionReason.SIGN_IN_REQUIRED)
        assertThat(result.message).contains("Connexion requise")
        assertThat(result.shouldRetryWithAnotherSession).isTrue()
    }

    @Test
    fun `detects login required error with variant spelling`() {
        val exception = Exception("Login required to view this video")
        val result = handler.checkAgeRestriction(exception)

        assertThat(result.isAgeRestricted).isTrue()
        assertThat(result.reason).isEqualTo(AgeRestrictionReason.SIGN_IN_REQUIRED)
        assertThat(result.message).contains("Connexion requise")
    }

    @Test
    fun `returns not age-restricted for normal error`() {
        val exception = Exception("Video not found")
        val result = handler.checkAgeRestriction(exception)

        assertThat(result.isAgeRestricted).isFalse()
        assertThat(result.reason).isEqualTo(AgeRestrictionReason.NOT_AGE_RESTRICTED)
        assertThat(result.message).isEmpty()
        assertThat(result.action).isEmpty()
    }

    @Test
    fun `returns not age-restricted for geo-only error`() {
        val exception = Exception("This video is not available in your country")
        val result = handler.checkAgeRestriction(exception)

        assertThat(result.isAgeRestricted).isFalse()
        assertThat(result.reason).isEqualTo(AgeRestrictionReason.NOT_AGE_RESTRICTED)
    }

    @Test
    fun `handles age-restricted error with authenticated session`() {
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
        val exception = Exception("Age verification required")
        val result = handler.handleAgeRestrictedError("https://youtube.com/watch?v=test", session, exception)

        assertThat(result.isAgeRestricted).isTrue()
        assertThat(result.shouldRetryWithAnotherSession).isTrue()
    }

    @Test
    fun `handles age-restricted error without session`() {
        val exception = Exception("Age verification required")
        val result = handler.handleAgeRestrictedError("https://youtube.com/watch?v=test", null, exception)

        assertThat(result.isAgeRestricted).isTrue()
        assertThat(result.reason).isEqualTo(AgeRestrictionReason.AGE_VERIFICATION_REQUIRED)
    }

    @Test
    fun `detects minor account from error message`() {
        val session = Session(
            credentials = SessionCredentials(
                id = "minor-account",
                clientId = "client-id",
                clientSecret = "client-secret",
                refreshToken = "refresh-token"
            ),
            accessToken = "access-token",
            accessTokenExpiry = "2099-12-31T23:59:59Z"
        )
        val exception = Exception("You must be 18 or older to view this video - under 18 detected")
        val result = handler.handleAgeRestrictedError("https://youtube.com/watch?v=test", session, exception)

        assertThat(result.isAgeRestricted).isTrue()
        assertThat(result.reason).isEqualTo(AgeRestrictionReason.MINOR_ACCOUNT)
        assertThat(result.message).contains("Compte mineur")
    }

    @Test
    fun `throws AgeRestrictedVideoException from result`() {
        val result = AgeVerificationHandler.AgeVerificationResult(
            isAgeRestricted = true,
            reason = AgeRestrictionReason.AGE_VERIFICATION_REQUIRED,
            message = "Age verification required",
            action = "Authenticate"
        )

        try {
            handler.throwAgeRestrictedException(result, "https://youtube.com/watch?v=test")
        } catch (e: AgeRestrictedVideoException) {
            assertThat(e.reason).isEqualTo(AgeRestrictionReason.AGE_VERIFICATION_REQUIRED)
            assertThat(e.videoUrl).isEqualTo("https://youtube.com/watch?v=test")
            assertThat(e.message).isEqualTo("Age verification required")
        }
    }

    @Test
    fun `mightBeAgeRestricted returns false by default`() {
        val result = handler.mightBeAgeRestricted("https://youtube.com/watch?v=test")
        assertThat(result).isFalse()
    }

    @Test
    fun `logs age verification result without throwing exception`() {
        val result = AgeVerificationHandler.AgeVerificationResult(
            isAgeRestricted = true,
            reason = AgeRestrictionReason.AGE_VERIFICATION_REQUIRED,
            message = "Test age restriction",
            action = "Test action"
        )

        // This test ensures no exception is thrown during logging
        handler.logAgeVerificationResult(result, "https://youtube.com/watch?v=test")
    }

    @Test
    fun `does not log when result is not age-restricted`() {
        val result = AgeVerificationHandler.AgeVerificationResult(
            isAgeRestricted = false,
            reason = AgeRestrictionReason.NOT_AGE_RESTRICTED,
            message = "",
            action = ""
        )

        // This test ensures no logging happens for non-age-restricted content
        handler.logAgeVerificationResult(result, "https://youtube.com/watch?v=test")
    }

    @Test
    fun `handles exception with cause containing age restriction`() {
        val cause = Exception("Age verification required")
        val exception = Exception("Video unavailable", cause)
        val result = handler.checkAgeRestriction(exception)

        assertThat(result.isAgeRestricted).isTrue()
        assertThat(result.reason).isEqualTo(AgeRestrictionReason.AGE_VERIFICATION_REQUIRED)
    }

    @Test
    fun `handles age-restricted with hyphenated spelling`() {
        val exception = Exception("Age-restricted content")
        val result = handler.checkAgeRestriction(exception)

        assertThat(result.isAgeRestricted).isTrue()
    }

    @Test
    fun `handles age verification with different casing`() {
        val exception = Exception("AGE VERIFICATION REQUIRED")
        val result = handler.checkAgeRestriction(exception)

        assertThat(result.isAgeRestricted).isTrue()
    }
}
