package com.cheroliv.newpipe

import com.cheroliv.newpipe.NewpipeManager.NEWPIPE_GROUP
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Tâche Gradle typée pour télécharger de la musique depuis YouTube
 * Usage: gradle downloadMusic --url=<youtube-url> [--output=<output-dir>]
 */
open class DownloadMusicTask : DefaultTask() {

    private val logger = LoggerFactory.getLogger(DownloadMusicTask::class.java)

    @get:Input
    @set:Option(
        option = "url",
        description = "URL de la vidéo YouTube à télécharger"
    )
    var url: String = ""

    @get:Input
    @get:Optional
    @set:Option(
        option = "output",
        description = "Dossier de destination pour le fichier MP3 (défaut: ./downloads)"
    )
    var outputPath: String = ""

    init {
        group = NEWPIPE_GROUP
        description = "Downloads audio from a YouTube video and converts it to MP3"
    }

    @TaskAction
    fun download() {
        // Validation de l'URL
        if (url.isBlank()) {
            throw GradleException("L'URL YouTube est requise. Utilisez: --url=<youtube-url>")
        }

        // Déterminer le dossier de sortie
        val outputDir = if (outputPath.isNotBlank()) File(outputPath)
        else File(project.projectDir, "downloads")

        // Création du dossier de sortie
        if (!outputDir.exists()) {
            outputDir.mkdirs()
            logger.info("Dossier créé: ${outputDir.absolutePath}")
        }

        printHeader(url, outputDir)

        val downloader = YouTubeDownloader()
        val converter = Mp3Converter()

        runBlocking {
            try {
                // Étape 1: Extraction des informations
                logger.info("\n[1/4] Extraction des informations de la vidéo...")
                val videoInfo = downloader.getVideoInfo(url)

                val title = videoInfo.name
                val artist = videoInfo.uploaderName
                val duration = videoInfo.duration

                logger.info("✓ Titre: $title")
                logger.info("✓ Artiste: $artist")
                logger.info("✓ Durée: ${formatDuration(duration)}")

                // Étape 2: Sélection du flux audio
                logger.info("\n[2/4] Sélection du meilleur flux audio...")
                val audioStream = downloader.getBestAudioStream(videoInfo)
                logger.info("✓ Format: ${audioStream.format?.name ?: "inconnu"}")
                logger.info("✓ Bitrate: ${audioStream.averageBitrate} kbps")

                // Étape 3: Téléchargement
                logger.info("\n[3/4] Téléchargement de l'audio...")

                val sanitizedTitle = downloader.sanitizeFileName(title)
                val tempFile = File(outputDir, "${sanitizedTitle}_temp.${audioStream.format?.suffix ?: "m4a"}")

                var lastPrintedPercent = 0
                downloader.downloadAudio(audioStream, tempFile) { downloaded, total, percent ->
                    if (percent >= lastPrintedPercent + 5) {
                        val downloadedMB = downloaded / 1024.0 / 1024.0
                        val totalMB = total / 1024.0 / 1024.0
                        logger.info("  Progression: $percent%% (%.2f MB / %.2f MB)".format(downloadedMB, totalMB))
                        lastPrintedPercent = percent
                    }
                }
                logger.info("✓ Téléchargement terminé: ${tempFile.length() / 1024 / 1024} MB")

                // Étape 4: Conversion en MP3 et ajout des métadonnées
                logger.info("\n[4/4] Conversion en MP3 et ajout des métadonnées...")

                val mp3File = File(outputDir, "${sanitizedTitle}.mp3")
                converter.convertToMp3(tempFile, mp3File, bitrate = "192k")

                // Ajout des métadonnées
                val thumbnailUrl = videoInfo.url
                converter.addMetadata(
                    mp3File = mp3File,
                    title = title,
                    artist = artist,
                    album = "YouTube",
                    thumbnailUrl = thumbnailUrl
                )

                logger.info("✓ Conversion terminée")
                printSuccess(mp3File)

            } catch (e: Exception) {
                printError(e)
                throw GradleException("Échec du téléchargement: ${e.message}", e)
            }
        }
    }

    private fun printHeader(url: String, outputDir: File) {
        logger.info("=".repeat(60))
        logger.info("YouTube MP3 Downloader")
        logger.info("=".repeat(60))
        logger.info("URL: $url")
        logger.info("Destination: ${outputDir.absolutePath}")
        logger.info("=".repeat(60))
    }

    private fun printSuccess(mp3File: File) {
        logger.info("\n" + "=".repeat(60))
        logger.info("✓ SUCCÈS!")
        logger.info("Fichier: ${mp3File.absolutePath}")
        logger.info("Taille: ${mp3File.length() / 1024 / 1024} MB")
        logger.info("=".repeat(60))
    }

    private fun printError(e: Exception) {
        logger.error("\n" + "=".repeat(60))
        logger.error("✗ ERREUR: ${e.message}")
        logger.error("=".repeat(60))
    }

    private fun formatDuration(seconds: Long): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(minutes, secs)
    }
}
