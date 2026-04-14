package com.mp3organizer.ai

import com.mp3organizer.Database
import com.mp3organizer.DatabaseConfig
import com.mp3organizer.playlist.XspfGenerator
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Tâche: Génère une playlist musicale via LLM et lance VLC
 * 
 * Usage: ./gradlew playSmart --args="jazz détente pour coder"
 */
fun main(args: Array<String>) = runBlocking {
    val prompt = args.joinToString(" ").takeIf { it.isNotBlank() }
        ?: run {
            logger.info("Usage: playSmart <votre demande musicale>")
            logger.info("Exemples:")
            logger.info("  ./gradlew playSmart --args=\"jazz piano détente\"")
            logger.info("  ./gradlew playSmart --args=\"rock énergique 80s\"")
            logger.info("  ./gradlew playSmart --args=\"électro pour running\"")
            return@runBlocking
        }
    
    logger.info("🎵 Génération playlist pour: '$prompt'")
    
    // 1. Initialiser les composants
    val llm = LlmClient(modelName = "gemma4:e4b-it-q4_K_M")
    val dbConfig = DatabaseConfig(
        host = "localhost",
        port = 5432,
        database = "mp3db",
        username = "mp3user",
        password = "mp3password"
    )
    val database = Database(dbConfig)
    
    try {
        // 2. Initialiser la DB
        database.init()
        logger.info("✅ Connecté à PostgreSQL")
        
        // 3. Créer l'agent playlist
        val agent = PlaylistAgent(llm, database)
        
        // 4. Générer la playlist via LLM
        val playlist = agent.generatePlaylist(prompt)
        logger.info("📋 ${playlist.tracks.size} tracks trouvés")
        
        if (playlist.tracks.isEmpty()) {
            logger.warn("⚠️ Aucun track trouvé pour cette demande")
            return@runBlocking
        }
        
        // 5. Générer le fichier XSPF
        val playlistDir = File("playlists").apply { mkdirs() }
        val timestamp = System.currentTimeMillis()
        val xspfFile = File(playlistDir, "playlist-$timestamp.xspf")
        
        XspfGenerator.generate(playlist, xspfFile)
        logger.info("💾 Playlist générée: ${xspfFile.canonicalPath}")
        
        // 6. Afficher un aperçu
        logger.info("\n" + "=".repeat(60))
        logger.info("APERÇU PLAYLIST:")
        logger.info("=".repeat(60))
        playlist.tracks.take(5).forEachIndexed { i, track ->
            logger.info("${i + 1}. ${track.title} - ${track.artist} (${track.album})")
        }
        if (playlist.tracks.size > 5) {
            logger.info("... et ${playlist.tracks.size - 5} autres tracks")
        }
        logger.info("=".repeat(60))
        
        // 7. Lancer VLC avec la playlist
        logger.info("🎶 Lancement de VLC...")
        
        val vlcCommand = buildVlcCommand(xspfFile)
        val vlcProcess = ProcessBuilder(vlcCommand).start()
        
        logger.info("✅ VLC démarré avec PID: ${vlcProcess.pid()}")
        logger.info("🎵 Bonne écoute !")
        
        // Optionnel: attendre que VLC se termine
        // val exitCode = vlcProcess.waitFor()
        // logger.info("VLC terminé avec code: $exitCode")
        
    } catch (e: Exception) {
        logger.error("❌ Erreur: ${e.message}", e)
        logger.error("\nPour utiliser cette tâche:")
        logger.error("1. Vérifie que PostgreSQL tourne: ./gradlew startDb")
        logger.error("2. Vérifie qu'Ollama tourne: ollama serve")
        logger.error("3. Lance: ./gradlew playSmart --args=\"ta demande\"")
    } finally {
        database.close()
    }
}

/**
 * Construit la commande VLC selon l'OS
 */
private fun buildVlcCommand(playlistFile: File): List<String> {
    val os = System.getProperty("os.name").lowercase()
    
    return when {
        os.contains("win") -> {
            // Windows
            listOf(
                "C:\\Program Files\\VideoLAN\\VLC\\vlc.exe",
                "--qt-start-minimized",
                "--random",
                playlistFile.canonicalPath
            )
        }
        os.contains("mac") -> {
            // macOS
            listOf(
                "open",
                "-a",
                "VLC",
                playlistFile.canonicalPath
            )
        }
        else -> {
            // Linux (défaut)
            listOf(
                "vlc",
                "--qt-start-minimized",
                "--random",
                playlistFile.canonicalPath
            )
        }
    }
}
