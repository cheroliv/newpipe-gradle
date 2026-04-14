package com.mp3organizer.ai

import com.mp3organizer.Database
import com.mp3organizer.DatabaseConfig
import com.mp3organizer.playlist.XspfGenerator
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Tâche: Génère une playlist par artiste et lance VLC
 * 
 * Usage: ./gradlew playArtist --args="Miles Davis"
 */
fun main(args: Array<String>) = runBlocking {
    val artistName = args.firstOrNull()?.takeIf { it.isNotBlank() }
        ?: run {
            logger.info("Usage: playArtist <nom artiste>")
            logger.info("Exemples:")
            logger.info("  ./gradlew playArtist --args=\"Miles Davis\"")
            logger.info("  ./gradlew playArtist --args=\"Daft Punk\"")
            return@runBlocking
        }
    
    logger.info("🎵 Génération playlist pour: '$artistName'")
    
    val llm = LlmClient(modelName = "gemma4:e4b-it-q4_K_M")
    val dbConfig = DatabaseConfig()
    val database = Database(dbConfig)
    
    try {
        database.init()
        val agent = PlaylistAgent(llm, database)
        
        val playlist = agent.generatePlaylistByArtist(artistName)
        logger.info("📋 ${playlist.tracks.size} tracks trouvés")
        
        if (playlist.tracks.isEmpty()) {
            logger.warn("⚠️ Aucun track trouvé pour cet artiste")
            return@runBlocking
        }
        
        val playlistDir = File("playlists").apply { mkdirs() }
        val timestamp = System.currentTimeMillis()
        val xspfFile = File(playlistDir, "playlist-artist-${artistName.replace(" ", "-").lowercase()}-$timestamp.xspf")
        
        XspfGenerator.generate(playlist, xspfFile)
        logger.info("💾 Playlist générée: ${xspfFile.canonicalPath}")
        
        logger.info("\n" + "=".repeat(60))
        logger.info("APERÇU PLAYLIST:")
        logger.info("=".repeat(60))
        playlist.tracks.take(5).forEachIndexed { i, track ->
            logger.info("${i + 1}. ${track.title} - ${track.artist} (${track.album})")
        }
        logger.info("=".repeat(60))
        
        logger.info("🎶 Lancement de VLC...")
        val vlcCommand = buildVlcCommand(xspfFile)
        val vlcProcess = ProcessBuilder(vlcCommand).start()
        
        logger.info("✅ VLC démarré avec PID: ${vlcProcess.pid()}")
        logger.info("🎵 Bonne écoute !")
        
    } catch (e: Exception) {
        logger.error("❌ Erreur: ${e.message}", e)
    } finally {
        database.close()
    }
}

private fun buildVlcCommand(playlistFile: File): List<String> {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("win") -> listOf("C:\\Program Files\\VideoLAN\\VLC\\vlc.exe", "--random", playlistFile.canonicalPath)
        os.contains("mac") -> listOf("open", "-a", "VLC", playlistFile.canonicalPath)
        else -> listOf("vlc", "--random", playlistFile.canonicalPath)
    }
}
