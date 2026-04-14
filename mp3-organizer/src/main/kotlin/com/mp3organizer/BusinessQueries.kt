package com.mp3organizer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Requêtes métier - appelle fn_business_stats() et affiche le résultat
 */
fun main(args: Array<String>) = runBlocking {
    val config = DatabaseConfig()
    val db = Database(config)
    
    try {
        db.init()
        
        logger.info("=" .repeat(60))
        logger.info("REQUÊTES MÉTIER")
        logger.info("=" .repeat(60))
        
        // Appel fonction stockée - TOUTE la logique est dans PostgreSQL
        val json = db.getBusinessStats()
        
        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val stats = mapper.readTree(json)
        
        // Top artists
        logger.info("\n1. TOP 10 ARTISTES")
        stats.get("top_artists").forEach { artist ->
            val name = artist.get("name").asText()
            val tracks = artist.get("track_count").asInt()
            val albums = artist.get("album_count").asInt()
            val size = artist.get("size_pretty").asText()
            logger.info("   $name: $tracks tracks, $albums albums ($size)")
        }
        
        // Small files
        logger.info("\n2. FICHIERS < 2MB (à supprimer)")
        val smallFiles = stats.get("small_files")
        logger.info("   ${smallFiles.size()} fichiers trouvés")
        smallFiles.take(10).forEach { file ->
            val artist = file.get("artist_name").asText()
            val title = file.get("title").asText()
            val size = file.get("size_pretty").asText()
            logger.info("   $artist - $title ($size)")
        }
        
        // Missing metadata
        logger.info("\n3. TRACKS SANS MÉTADONNÉES")
        val missing = stats.get("missing_metadata")
        logger.info("   ${missing.size()} fichiers avec métadonnées manquantes")
        
        // Video tracks
        logger.info("\n4. CLIPS VIDÉO")
        stats.get("video_tracks").take(5).forEach { track ->
            val artist = track.get("artist_name").asText()
            val title = track.get("title").asText()
            val size = track.get("size_pretty").asText()
            logger.info("   $artist - $title ($size)")
        }
        
        // Collection summary
        logger.info("\n5. RÉSUMÉ COLLECTION")
        val collection = stats.get("collection")
        logger.info("   Artistes: ${collection.get("total_artists").asInt()}")
        logger.info("   Albums: ${collection.get("total_albums").asInt()}")
        logger.info("   Tracks: ${collection.get("total_tracks").asInt()}")
        logger.info("   Taille: ${collection.get("total_size").asText()}")
        logger.info("   Durée: ${collection.get("total_duration").asText()}")
        
        logger.info("\n" + "=" .repeat(60))
        
    } finally {
        db.close()
    }
}
