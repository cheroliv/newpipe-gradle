package com.cheroliv.newpipe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.Artwork
import org.jaudiotagger.tag.images.ArtworkFactory
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL
import java.time.Year

/**
 * Gère la conversion audio et l'ajout de métadonnées MP3
 */
class Mp3Converter {
    
    private val logger = LoggerFactory.getLogger(Mp3Converter::class.java)
    
    /**
     * Convertit un fichier audio en MP3 en utilisant FFmpeg
     * Note: FFmpeg doit être installé sur le système
     */
    suspend fun convertToMp3(
        inputFile: File,
        outputFile: File,
        bitrate: String = "192k",
        onProgress: (percent: Int) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        logger.info("Conversion en MP3: ${inputFile.name} -> ${outputFile.name}")
        
        try {
            // Vérifier si FFmpeg est disponible
            if (!isFFmpegAvailable()) {
                logger.warn("FFmpeg non disponible, copie du fichier audio sans conversion")
                inputFile.copyTo(outputFile, overwrite = true)
                return@withContext outputFile
            }
            
            // Commande FFmpeg pour conversion MP3
            val command = listOf(
                "ffmpeg",
                "-i", inputFile.absolutePath,
                "-vn", // Pas de vidéo
                "-ar", "44100", // Fréquence d'échantillonnage
                "-ac", "2", // Stéréo
                "-b:a", bitrate, // Bitrate
                "-y", // Overwrite
                outputFile.absolutePath
            )
            
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            
            // Lecture de la sortie pour suivre la progression
            val output = StringBuilder()
            process.inputStream.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    output.append(line).append("\n")
                    
                    // Extraction de la progression (ex: "time=00:01:30.00")
                    if (line.contains("time=")) {
                        // Parsing basique de la progression
                        logger.debug(line)
                    }
                }
            }
            
            val exitCode = process.waitFor()
            
            if (exitCode != 0) {
                logger.error("FFmpeg a échoué avec le code: $exitCode")
                logger.error("Output: $output")
                throw ConversionException("La conversion FFmpeg a échoué")
            }
            
            logger.info("Conversion MP3 réussie: ${outputFile.length() / 1024 / 1024} MB")
            
            // Nettoyer le fichier source
            if (inputFile.exists() && inputFile != outputFile) {
                inputFile.delete()
                logger.debug("Fichier source supprimé: ${inputFile.name}")
            }
            
            outputFile
        } catch (e: Exception) {
            logger.error("Erreur lors de la conversion: ${e.message}", e)
            throw ConversionException("Échec de la conversion en MP3", e)
        }
    }
    
    /**
     * Ajoute des métadonnées ID3 au fichier MP3
     */
    suspend fun addMetadata(
        mp3File: File,
        title: String?,
        artist: String?,
        album: String? = null,
        thumbnailUrl: String? = null
    ): File = withContext(Dispatchers.IO) {
        logger.info("Ajout des métadonnées au fichier MP3")
        
        try {
            val audioFile = AudioFileIO.read(mp3File)
            val tag = audioFile.tagOrCreateAndSetDefault
            
            // Ajout des métadonnées textuelles
            title?.let { tag.setField(FieldKey.TITLE, it) }
            artist?.let { tag.setField(FieldKey.ARTIST, it) }
            album?.let { tag.setField(FieldKey.ALBUM, it) }
            
            // Ajout de l'année courante
            tag.setField(FieldKey.YEAR, Year.now().toString())
            
            // Téléchargement et ajout de la vignette si disponible
            thumbnailUrl?.let { url ->
                try {
                    val artwork = downloadThumbnail(url)
                    artwork?.let { 
                        tag.setField(artwork)
                        logger.info("Vignette ajoutée avec succès")
                    }
                } catch (e: Exception) {
                    logger.warn("Impossible d'ajouter la vignette: ${e.message}")
                }
            }
            
            // Sauvegarde des métadonnées
            audioFile.commit()
            
            logger.info("Métadonnées ajoutées: $title - $artist")
            mp3File
        } catch (e: Exception) {
            logger.error("Erreur lors de l'ajout des métadonnées: ${e.message}", e)
            // Ne pas lever d'exception, retourner le fichier tel quel
            mp3File
        }
    }
    
    /**
     * Télécharge la vignette et la convertit en artwork
     */
    private fun downloadThumbnail(url: String): Artwork? {
        return try {
            val connection = URL(url).openConnection()
            connection.connect()
            
            val imageData = connection.getInputStream().readBytes()
            
            ArtworkFactory.createArtworkFromFile(
                File.createTempFile("thumbnail", ".jpg").apply {
                    writeBytes(imageData)
                    deleteOnExit()
                }
            )
        } catch (e: Exception) {
            logger.warn("Échec du téléchargement de la vignette: ${e.message}")
            null
        }
    }
    
    /**
     * Vérifie si FFmpeg est disponible sur le système
     */
    private fun isFFmpegAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("ffmpeg", "-version")
                .redirectErrorStream(true)
                .start()
            
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Exception pour les erreurs de conversion
 */
class ConversionException(message: String, cause: Throwable? = null) : Exception(message, cause)
