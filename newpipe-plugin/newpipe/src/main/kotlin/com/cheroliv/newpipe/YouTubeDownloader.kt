package com.cheroliv.newpipe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream

/**
 * Classe principale pour télécharger l'audio de vidéos YouTube
 * Utilise NewPipe Extractor pour extraire les informations et flux audio
 */
class YouTubeDownloader {
    
    private val logger = LoggerFactory.getLogger(YouTubeDownloader::class.java)
    private val httpClient = OkHttpClient()
    
    init {
        // Initialisation de NewPipe avec le service YouTube
        NewPipe.init(DownloaderImpl.getInstance())
    }
    
    /**
     * Récupère les informations de la vidéo YouTube
     * @param url URL de la vidéo YouTube
     * @return StreamInfo contenant toutes les métadonnées
     */
    suspend fun getVideoInfo(url: String): StreamInfo = withContext(Dispatchers.IO) {
        logger.info("Extraction des informations pour: $url")
        
        try {
            val service = ServiceList.YouTube
            val extractor = service.getStreamExtractor(url) as YoutubeStreamExtractor
            extractor.fetchPage()
            
            val streamInfo = StreamInfo.getInfo(extractor)
            
            logger.info("Titre: ${streamInfo.name}")
            logger.info("Auteur: ${streamInfo.uploaderName}")
            logger.info("Durée: ${streamInfo.duration} secondes")
            
            streamInfo
        } catch (e: Exception) {
            logger.error("Erreur lors de l'extraction des informations: ${e.message}", e)
            throw DownloadException("Impossible d'extraire les informations de la vidéo", e)
        }
    }
    
    /**
     * Sélectionne le meilleur flux audio disponible
     * Préfère les formats de haute qualité (M4A, WebM)
     */
    fun getBestAudioStream(streamInfo: StreamInfo): AudioStream {
        val audioStreams = streamInfo.audioStreams
        
        if (audioStreams.isEmpty()) {
            throw DownloadException("Aucun flux audio disponible pour cette vidéo")
        }
        
        // Trier par bitrate décroissant et sélectionner le meilleur
        val bestStream = audioStreams
            .filter { it.averageBitrate > 0 }
            .maxByOrNull { it.averageBitrate }
            ?: audioStreams.first()
        
        logger.info("Flux audio sélectionné: ${bestStream.format?.name ?: "inconnu"}, " +
                    "bitrate: ${bestStream.averageBitrate} kbps")
        
        return bestStream
    }
    
    /**
     * Télécharge le flux audio vers un fichier local
     * Affiche la progression du téléchargement
     */
    suspend fun downloadAudio(
        audioStream: AudioStream,
        outputFile: File,
        onProgress: (downloaded: Long, total: Long, percent: Int) -> Unit = { _, _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        logger.info("Téléchargement vers: ${outputFile.absolutePath}")
        
        try {
            val request = Request.Builder()
                .url(audioStream.content)
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw DownloadException("Échec du téléchargement: ${response.code}")
                }
                
                val totalBytes = response.body?.contentLength() ?: -1L
                var downloadedBytes = 0L
                var lastPercent = 0
                
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            
                            if (totalBytes > 0) {
                                val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(downloadedBytes, totalBytes, percent)
                                }
                            }
                        }
                    }
                }
                
                logger.info("Téléchargement terminé: ${downloadedBytes / 1024 / 1024} MB")
            }
            
            outputFile
        } catch (e: Exception) {
            logger.error("Erreur lors du téléchargement: ${e.message}", e)
            if (outputFile.exists()) {
                outputFile.delete()
            }
            throw DownloadException("Échec du téléchargement du fichier audio", e)
        }
    }
    
    /**
     * Nettoie un nom de fichier en supprimant les caractères invalides
     */
    fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("[^a-zA-Z0-9àâäéèêëïîôùûüÿçÀÂÄÉÈÊËÏÎÔÙÛÜŸÇ \\-_]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(200) // Limite de longueur
    }
}

/**
 * Exception personnalisée pour les erreurs de téléchargement
 */
class DownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)
