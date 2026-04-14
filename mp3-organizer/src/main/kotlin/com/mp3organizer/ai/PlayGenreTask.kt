package com.mp3organizer.ai

import com.mp3organizer.Database
import com.mp3organizer.DatabaseConfig
import com.mp3organizer.playlist.XspfGenerator
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Tâche: Génère une playlist par genre et lance VLC
 * 
 * Usage: ./gradlew playGenre --args="jazz"
 */
fun main(args: Array<String>) = runBlocking {
    val genreName = args.firstOrNull()?.takeIf { it.isNotBlank() }
        ?: run {
            logger.info("Usage: playGenre <nom genre>")
            logger.info("Exemples:")
            logger.info("  ./gradlew playGenre --args=\"jazz\"")
            logger.info("  ./gradlew playGenre --args=\"rock\"")
            logger.info("  ./gradlew playGenre --args=\"électro\"")
            return@runBlocking
        }
    
    logger.info("🎵 Génération playlist genre: '$genreName'")
    
    val llm = LlmClient(modelName = "gemma4:e4b-it-q4_K_M")
    val dbConfig = DatabaseConfig()
    val database = Database(dbConfig)
    
    try {
        database.init()
        val agent = PlaylistAgent(llm, database)
        
        val playlist = agent.generatePlaylistByGenre(genreName)
        logger.info("📋 ${playlist.tracks.size} tracks trouvés")
        
        if (playlist.tracks.isEmpty()) {
            logger.warn("⚠️ Aucun track trouvé pour ce genre")
            return@runBlocking
        }
        
        val playlistDir = File("playlists").apply { mkdirs() }
        val timestamp = System.currentTimeMillis()
        val xspfFile = File(playlistDir, "playlist-genre-${genreName.lowercase()}-$timestamp.xspf")
        
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
