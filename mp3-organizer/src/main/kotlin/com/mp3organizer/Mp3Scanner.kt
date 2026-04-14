package com.mp3organizer

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

private val logger = KotlinLogging.logger {}

/**
 * Scanner MP3 - extrait les métadonnées et appelle fn_import_track
 * Aucune logique métier ici, juste extraction et appel de fonction stockée
 * 
 * Supporte le scan de MULTIPLES dossiers sources n'importe où dans le système
 */
class Mp3Scanner(private val db: Database, private val maxConcurrency: Int = 8) {
    
    data class ScanResult(
        val filePath: String,
        val fileSize: Long,
        val title: String,
        val artist: String,
        val album: String?,
        val year: Int?,
        val genre: String?,
        val durationSeconds: Int?,
        val bitrateKbps: Int?,
        val sampleRate: Int?,
        val hasVideo: Boolean,
        val sourcePath: String // Dossier source d'origine
    )
    
    /**
     * Scan un seul dossier
     */
    suspend fun scanDirectory(rootPath: String): Int = scanDirectories(listOf(rootPath))
    
    /**
     * Scan MULTIPLES dossiers sources (n'importe où dans le système)
     * Les chemins peuvent être absolus ou relatifs
     */
    suspend fun scanDirectories(rootPaths: List<String>): Int {
        require(rootPaths.isNotEmpty()) { "Au moins un dossier requis" }
        
        // Valider et normaliser les chemins
        val validRoots = rootPaths
            .map { File(it).canonicalFile }
            .onEach { file ->
                require(file.exists()) { "Dossier inexistant: ${file.path}" }
                require(file.isDirectory) { "N'est pas un dossier: ${file.path}" }
            }
        
        logger.info("Scan de ${validRoots.size} dossier(s) source(s):")
        validRoots.forEach { logger.info("  - ${it.canonicalPath}") }
        
        // Trouver tous les fichiers MP3 dans TOUS les dossiers
        val allMp3Files = validRoots.flatMap { root ->
            findMp3Files(root).also { files ->
                logger.info("  ${root.canonicalPath}: ${files.size} MP3")
            }
        }
        
        logger.info("Total: ${allMp3Files.size} fichiers MP3 à scanner")
        
        var successCount = 0
        var errorCount = 0
        
        allMp3Files.asFlow()
            .flowOn(Dispatchers.IO)
            .map { file ->
                withContext(Dispatchers.IO) {
                    try {
                        val result = scanFile(file)
                        db.importTrack(
                            title = result.title,
                            artistName = result.artist,
                            albumTitle = result.album,
                            albumYear = result.year,
                            filePath = result.filePath,
                            fileSizeBytes = result.fileSize,
                            durationSeconds = result.durationSeconds,
                            bitrateKbps = result.bitrateKbps,
                            sampleRate = result.sampleRate,
                            hasVideo = result.hasVideo,
                            genreNames = result.genre?.let { listOf(it) }
                        )
                        successCount++
                        "${file.name} (${result.sourcePath})"
                    } catch (e: Exception) {
                        errorCount++
                        logger.error("Erreur ${file.name}: ${e.message}")
                        null
                    }
                }
            }
            .filterNotNull()
            .collect { logger.debug("✓ $it") }
        
        logger.info("Terminé: $successCount importés, $errorCount erreurs")
        return successCount
    }
    
    private fun findMp3Files(root: File): List<File> =
        Files.walk(root.toPath())
            .asSequence()
            .filter { path ->
                val file = path.toFile()
                file.isFile && file.extension.equals("mp3", ignoreCase = true)
            }
            .map { it.toFile() }
            .toList()
    
    private suspend fun scanFile(file: File): ScanResult = withContext(Dispatchers.IO) {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var year: Int? = null
        var genre: String? = null
        var durationSeconds: Int? = null
        var bitrateKbps: Int? = null
        var sampleRate: Int? = null
        var hasVideo = false
        
        try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag
            
            title = tag?.getFirst(FieldKey.TITLE)?.takeIf { it.isNotBlank() }
            artist = tag?.getFirst(FieldKey.ARTIST)?.takeIf { it.isNotBlank() }
            album = tag?.getFirst(FieldKey.ALBUM)?.takeIf { it.isNotBlank() }
            year = tag?.getFirst(FieldKey.YEAR)?.toIntOrNull()
            genre = tag?.getFirst(FieldKey.GENRE)?.takeIf { it.isNotBlank() }
            
            durationSeconds = audioFile.audioHeader.trackLength
            bitrateKbps = audioFile.audioHeader.bitRateAsNumber
            sampleRate = audioFile.audioHeader.sampleRateAsNumber
            hasVideo = hasVideoStream(file)
            
        } catch (e: Exception) {
            logger.debug("Jaudiotagger échec ${file.name}: ${e.message}")
            
            val ffprobeData = scanWithFfprobe(file)
            title = ffprobeData["title"]
            artist = ffprobeData["artist"]
            album = ffprobeData["album"]
            durationSeconds = ffprobeData["duration"]?.toIntOrNull()
            bitrateKbps = ffprobeData["bitrate"]?.toIntOrNull()
            sampleRate = ffprobeData["sample_rate"]?.toIntOrNull()
            hasVideo = ffprobeData["has_video"] == "true"
        }
        
        // Fallback filename
        if (title.isNullOrBlank()) {
            title = file.nameWithoutExtension
                .replace(Regex("^\\d+[-_]?"), "")
                .replace(Regex("[-_]"), " ")
                .trim()
        }
        
        if (artist.isNullOrBlank()) {
            artist = extractArtistFromPath(file, file.parentFile) 
                ?: extractArtistFromFilename(file.name)
                ?: "Unknown Artist"
        }
        
        ScanResult(
            filePath = file.canonicalPath,
            fileSize = file.length(),
            title = title?.trim() ?: "Unknown",
            artist = artist?.trim() ?: "Unknown Artist",
            album = album?.trim(),
            year = year,
            genre = genre?.trim(),
            durationSeconds = durationSeconds,
            bitrateKbps = bitrateKbps,
            sampleRate = sampleRate,
            hasVideo = hasVideo,
            sourcePath = file.parentFile?.canonicalPath ?: "Unknown"
        )
    }
    
    private fun extractArtistFromPath(file: File, root: File): String? {
        val relative = file.canonicalPath.removePrefix(root.canonicalPath).trimStart('/')
        val parts = relative.split(File.separatorChar)
        return if (parts.size > 1) parts[0].takeIf { it.isNotBlank() } else null
    }
    
    private fun extractArtistFromFilename(filename: String): String? {
        val parts = filename.removeSuffix(".mp3").split(" - ", limit = 2)
        return if (parts.size >= 2) parts[0].trim().takeIf { it.isNotBlank() } else null
    }
    
    private fun hasVideoStream(file: File): Boolean = try {
        val process = ProcessBuilder(
            "ffprobe", "-v", "error", "-select_streams", "v",
            "-show_entries", "stream=codec_type",
            "-of", "default=noprint_wrappers=1",
            file.canonicalPath
        ).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor() == 0 && output.contains("video")
    } catch (e: Exception) { false }
    
    private fun scanWithFfprobe(file: File): Map<String, String> = try {
        val process = ProcessBuilder(
            "ffprobe", "-v", "quiet", "-print_format", "json",
            "-show_format", "-show_streams", file.canonicalPath
        ).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        parseFfprobeJson(output)
    } catch (e: Exception) { emptyMap() }
    
    private fun parseFfprobeJson(output: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        Regex("""\"(title|artist|album)\"\s*:\s*\"([^\"]+)\"""").findAll(output)
            .forEach { result[it.groupValues[1]] = it.groupValues[2] }
        Regex("""\"duration\"\s*:\s*\"([^\"]+)\"""").find(output)?.let { result["duration"] = it.groupValues[1] }
        if (output.contains("\"codec_type\"\s*:\s*\"video\"".toRegex())) result["has_video"] = "true"
        return result
    }
}
