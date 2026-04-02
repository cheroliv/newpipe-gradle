package com.cheroliv.newpipe

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File

/**
 * Test d'intégration — vrais appels réseau vers YouTube via Google OAuth2.
 *
 * Lit sessions.yml depuis newpipe-gradle/ et musics.yml depuis le même
 * dossier pour récupérer la première tune du premier artiste.
 *
 * Comportement :
 * - sessions.yml absent            → SKIPPED silencieusement
 * - refreshToken vide              → SKIPPED silencieusement
 *   (cohérent avec buildSessionManager qui ne tente aucun appel réseau
 *    quand le refreshToken est vide)
 * - refreshToken présent           → refresh OAuth2 silencieux
 *   - refresh échoue (token révoqué, réseau) → mode anonyme + SKIPPED
 *   - refresh réussit              → appel YouTube réel → assertions
 *
 * Lancer après avoir complété le Device Flow :
 *   ./gradlew buildSessions authSessions
 *   ./gradlew functionalTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class YouTubeIntegrationTest {

    companion object {
        // newpipe-gradle/newpipe/ → newpipe-gradle/
        private val rootDir: File =
            File(System.getProperty("user.dir")).parentFile

        private val sessionsFile: File = rootDir.resolve("sessions.yml")
        private val musicsFile: File = rootDir.resolve("musics.yml")
    }

    private var sessionManager: SessionManager? = null
    private var firstTuneUrl: String = ""

    @BeforeAll
    fun setUp() {
        // ------------------------------------------------------------------
        // sessions.yml absent → skip silencieux
        // ------------------------------------------------------------------
        assumeTrue(
            sessionsFile.exists(),
            "sessions.yml absent de ${sessionsFile.absolutePath} " +
                    "— test skippé (run: ./gradlew buildSessions)"
        )

        val config = NewpipeManager.yamlMapper.readValue<SessionConfig>(sessionsFile)

        // ------------------------------------------------------------------
        // Aucun refreshToken présent → skip silencieux
        // Cohérent avec buildSessionManager qui retourne null
        // sans jamais tenter d'appel réseau dans ce cas
        // ------------------------------------------------------------------
        val hasRefreshToken = config.sessions.any { it.refreshToken.isNotBlank() }
        assumeTrue(
            hasRefreshToken,
            "Aucun refreshToken dans sessions.yml " +
                    "— test skippé (run: ./gradlew authSessions)"
        )

        // ------------------------------------------------------------------
        // refreshToken présent → refresh OAuth2 silencieux
        // Si le refresh échoue → mode anonyme → skip
        // (pas de fail : le token a peut-être été révoqué ou le réseau est absent)
        // ------------------------------------------------------------------
        sessionManager = NewpipeManager.buildSessionManager(sessionsFile.absolutePath)
        assumeTrue(
            sessionManager != null,
            "buildSessionManager a retourné null (refresh échoué ou réseau indisponible) " +
                    "— test skippé en mode anonyme"
        )

        // ------------------------------------------------------------------
        // Lecture de musics.yml — première tune du premier artiste
        // ------------------------------------------------------------------
        assertThat(musicsFile)
            .describedAs("musics.yml absent de ${musicsFile.absolutePath}")
            .exists()

        val selection = NewpipeManager.yamlMapper.readValue<Selection>(musicsFile)

        assertThat(selection.artistes)
            .describedAs("musics.yml ne contient aucun artiste")
            .isNotEmpty

        val firstArtist = selection.artistes.first()

        assertThat(firstArtist.tunes)
            .describedAs(
                "Le premier artiste '${firstArtist.name}' n'a aucune tune. " +
                        "Ajoutez au moins une URL dans musics.yml"
            )
            .isNotEmpty

        firstTuneUrl = firstArtist.tunes.first()
    }

    @Test
    fun `first tune metadata is retrieved successfully with authenticated session`() {
        assumeTrue(
            sessionManager != null,
            "SessionManager non initialisé — test skippé"
        )

        // Initialise DownloaderImpl avec le vrai SessionManager
        DownloaderImpl.init(sessionManager)

        val downloader = YouTubeDownloader()

        // Vrai appel réseau vers YouTube
        val metadata: VideoMetadata = runBlocking {
            downloader.getVideoInfo(firstTuneUrl)
        }

        assertThat(metadata.title)
            .describedAs("Le titre de la vidéo ne doit pas être vide")
            .isNotBlank

        assertThat(metadata.uploaderName)
            .describedAs("Le nom de l'uploader ne doit pas être vide")
            .isNotBlank

        assertThat(metadata.duration)
            .describedAs("La durée de la vidéo doit être positive")
            .isPositive

        assertThat(metadata.url)
            .describedAs("L'URL de la vidéo ne doit pas être vide")
            .isNotBlank

        println(
            """
            ✓ Métadonnées YouTube récupérées avec succès
              URL      : $firstTuneUrl
              Titre    : ${metadata.title}
              Uploader : ${metadata.uploaderName}
              Durée    : ${metadata.duration}s
            """.trimIndent()
        )
    }
}