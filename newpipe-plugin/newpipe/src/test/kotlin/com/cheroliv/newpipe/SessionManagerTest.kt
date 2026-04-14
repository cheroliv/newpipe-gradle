package com.cheroliv.newpipe

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionManagerTest {

    @Test
    fun `distributes requests in round-robin order`() {
        val sessions = listOf(
            Session(SessionCredentials("session-1", "cid-1", "cs-1", "rt-1")),
            Session(SessionCredentials("session-2", "cid-2", "cs-2", "rt-2")),
            Session(SessionCredentials("session-3", "cid-3", "cs-3", "rt-3"))
        )
        val manager = SessionManager(sessions)

        val s1 = manager.next()
        val s2 = manager.next()
        val s3 = manager.next()
        val s4 = manager.next()

        assertThat(s1?.id).isEqualTo("session-1")
        assertThat(s2?.id).isEqualTo("session-2")
        assertThat(s3?.id).isEqualTo("session-3")
        assertThat(s4?.id).isEqualTo("session-1")
    }

    @Test
    fun `excludes invalid sessions from round-robin`() {
        val sessions = listOf(
            Session(SessionCredentials("session-1", "cid-1", "cs-1", "rt-1")),
            Session(SessionCredentials("session-2", "cid-2", "cs-2", "rt-2"))
        )
        val manager = SessionManager(sessions)

        manager.markInvalid("session-1")
        val next = manager.next()

        assertThat(next?.id).isEqualTo("session-2")
    }

    @Test
    fun `returns null when all sessions are invalid`() {
        val sessions = listOf(
            Session(SessionCredentials("session-1", "cid-1", "cs-1", "rt-1")),
            Session(SessionCredentials("session-2", "cid-2", "cs-2", "rt-2"))
        )
        val manager = SessionManager(sessions)

        manager.markInvalid("session-1")
        manager.markInvalid("session-2")
        val next = manager.next()

        assertThat(next).isNull()
    }

    @Test
    fun `hasValidSession returns false when all sessions are invalid`() {
        val sessions = listOf(
            Session(SessionCredentials("session-1", "cid-1", "cs-1", "rt-1"))
        )
        val manager = SessionManager(sessions)

        assertThat(manager.hasValidSession()).isTrue

        manager.markInvalid("session-1")

        assertThat(manager.hasValidSession()).isFalse()
    }

    @Test
    fun `continues round-robin after marking session invalid`() {
        val sessions = listOf(
            Session(SessionCredentials("session-1", "cid-1", "cs-1", "rt-1")),
            Session(SessionCredentials("session-2", "cid-2", "cs-2", "rt-2")),
            Session(SessionCredentials("session-3", "cid-3", "cs-3", "rt-3"))
        )
        val manager = SessionManager(sessions)

        manager.next()
        manager.markInvalid("session-2")
        val next1 = manager.next()
        val next2 = manager.next()

        assertThat(next1?.id).isEqualTo("session-3")
        assertThat(next2?.id).isEqualTo("session-1")
    }

    @Test
    fun `handles empty session list gracefully`() {
        val manager = SessionManager(emptyList())

        assertThat(manager.next()).isNull()
        assertThat(manager.hasValidSession()).isFalse()
    }
}
