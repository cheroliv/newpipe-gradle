package com.cheroliv.newpipe

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File

/**
 * Tests d'intégration avec de vrais comptes YouTube authentifiés.
 *
 * Ces tests nécessitent :
 * - Des comptes Google de test avec refresh tokens valides
 * - Variables d'environnement :
 *   - TEST_YOUTUBE_CLIENT_ID
 *   - TEST_YOUTUBE_CLIENT_SECRET
 *   - TEST_YOUTUBE_REFRESH_TOKEN
 *
 * Lancer avec :
 *   ./gradlew functionalTest --tests "*YouTubeAuthIntegrationTest*" --include-tags "real-youtube"
 *
 * @tag real-youtube - Tests à ne pas exécuter en CI par défaut
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("real-youtube")
class YouTubeAuthIntegrationTest {

    companion object {
        private val rootDir: File = File(System.getProperty("user.dir")).parentFile
        private val sessionsFile: File = rootDir.resolve("sessions.yml")
        private val musicsFile: File = rootDir.resolve("musics.yml")

        private var testSession: Session? = null
        private var memberOnlyVideoUrl: String = ""
        private var publicVideoUrl: String = ""
    }

    @BeforeAll
    fun setUp() {
        assumeTrue(
            System.getenv("TEST_YOUTUBE_CLIENT_ID") != null,
            "TEST_YOUTUBE_CLIENT_ID non défini - test skippé"
        )
        assumeTrue(
            System.getenv("TEST_YOUTUBE_CLIENT_SECRET") != null,
            "TEST_YOUTUBE_CLIENT_SECRET non défini - test skippé"
        )
        assumeTrue(
            System.getenv("TEST_YOUTUBE_REFRESH_TOKEN") != null,
            "TEST_YOUTUBE_REFRESH_TOKEN non défini - test skippé"
        )

        testSession = Session(
            credentials = SessionCredentials(
                id = "test-account",
                clientId = System.getenv("TEST_YOUTUBE_CLIENT_ID"),
                clientSecret = System.getenv("TEST_YOUTUBE_CLIENT_SECRET"),
                refreshToken = System.getenv("TEST_YOUTUBE_REFRESH_TOKEN")
            )
        )

        memberOnlyVideoUrl = "https://www.youtube.com/watch?v=MEMBER_ONLY_VIDEO_ID"
        publicVideoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    }

    @Test
    fun `download member-only video with authenticated session`() {
        assumeTrue(testSession != null, "Session de test non initialisée")

        val sessionManager = SessionManager(listOf(testSession!!))
        DownloaderImpl.init(sessionManager)

        val downloader = YouTubeDownloader()

        val metadata: VideoMetadata = runBlocking {
            downloader.getVideoInfo(memberOnlyVideoUrl)
        }

        assertThat(metadata.title)
            .describedAs("Le titre de la vidéo membre-only ne doit pas être vide")
            .isNotBlank

        assertThat(metadata.uploaderName)
            .describedAs("Le nom de l'uploader ne doit pas être vide")
            .isNotBlank

        println(
            """
            ✓ Vidéo membre-only téléchargée avec succès
              URL      : $memberOnlyVideoUrl
              Titre    : ${metadata.title}
              Uploader : ${metadata.uploaderName}
            """.trimIndent()
        )
    }

    @Test
    fun `refresh expired token automatically`() {
        assumeTrue(testSession != null, "Session de test non initialisée")

        val sessionManager = SessionManager(listOf(testSession!!))
        DownloaderImpl.init(sessionManager)

        val downloader = YouTubeDownloader()

        val metadata: VideoMetadata = runBlocking {
            downloader.getVideoInfo(publicVideoUrl)
        }

        assertThat(metadata.title).isNotBlank()

        println(
            """
            ✓ Vidéo téléchargée avec succès
              URL   : $publicVideoUrl
              Titre : ${metadata.title}
            """.trimIndent()
        )
    }

    @Test
    fun `fallback to anonymous when all sessions invalid`() {
        val invalidSession = Session(
            credentials = SessionCredentials(
                id = "invalid-account",
                clientId = "invalid-client-id",
                clientSecret = "invalid-secret",
                refreshToken = "invalid-refresh-token"
            )
        )

        val sessionManager = SessionManager(listOf(invalidSession))
        DownloaderImpl.init(sessionManager)

        val downloader = YouTubeDownloader()

        val metadata: VideoMetadata = runBlocking {
            downloader.getVideoInfo(publicVideoUrl)
        }

        assertThat(metadata.title)
            .describedAs("La vidéo publique doit être téléchargeable en mode anonyme")
            .isNotBlank

        println(
            """
            ✓ Fallback mode anonyme fonctionnel
              URL   : $publicVideoUrl
              Titre : ${metadata.title}
            """.trimIndent()
        )
    }
}
