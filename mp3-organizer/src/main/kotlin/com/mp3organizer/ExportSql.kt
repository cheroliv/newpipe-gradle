package com.mp3organizer

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Export SQL - génère CREATE + INSERT depuis les vues
 */
fun main(args: Array<String>) = runBlocking {
    val config = DatabaseConfig()
    val db = Database(config)
    
    try {
        db.init()
        logger.info("Export SQL...")
        
        val lines = mutableListOf<String>()
        lines.add("-- MP3 Collection Export")
        lines.add("-- Generated: ${java.time.Instant.now()}")
        lines.add("")
        lines.add("-- Artists")
        
        // Artists
        val artists = db.queryView("artists")
        artists.forEach { row ->
            lines.add("INSERT INTO artists (id, name) VALUES (${row["id"]}, '${escape(row["name"] as String)}');")
        }
        
        lines.add("")
        lines.add("-- Albums")
        
        // Albums
        val albums = db.queryView("albums")
        albums.forEach { row ->
            val year = row["year"]?.toString() ?: "NULL"
            lines.add("INSERT INTO albums (id, title, artist_id, year) VALUES (${row["id"]}, '${escape(row["title"] as String)}', ${row["artist_id"]}, $year);")
        }
        
        lines.add("")
        lines.add("-- Tracks")
        
        // Tracks
        val tracks = db.queryView("tracks", limit = 10000)
        tracks.forEach { row ->
            val albumId = row["album_id"]?.toString() ?: "NULL"
            val duration = row["duration_seconds"]?.toString() ?: "NULL"
            val bitrate = row["bitrate_kbps"]?.toString() ?: "NULL"
            val sampleRate = row["sample_rate"]?.toString() ?: "NULL"
            lines.add(
                "INSERT INTO tracks (id, title, artist_id, album_id, file_path, file_size_bytes, duration_seconds, bitrate_kbps, sample_rate, has_video) " +
                "VALUES (${row["id"]}, '${escape(row["title"] as String)}', ${row["artist_id"]}, $albumId, '${escape(row["file_path"] as String)}', ${row["file_size_bytes"]}, $duration, $bitrate, $sampleRate, ${row["has_video"]});"
            )
        }
        
        lines.add("")
        lines.add("-- Genres")
        
        // Genres
        val genres = db.queryView("genres")
        genres.forEach { row ->
            lines.add("INSERT INTO genres (id, name) VALUES (${row["id"]}, '${escape(row["name"] as String)}');")
        }
        
        lines.add("")
        lines.add("-- Track Genres")
        
        // Track genres
        val trackGenres = db.queryView("track_genres")
        trackGenres.forEach { row ->
            lines.add("INSERT INTO track_genres (track_id, genre_id) VALUES (${row["track_id"]}, ${row["genre_id"]});")
        }
        
        val outputFile = File("exports/mp3-collection.sql")
        outputFile.parentFile.mkdirs()
        outputFile.writeText(lines.joinToString("\n"))
        
        logger.info("✓ Export SQL: ${outputFile.canonicalPath} (${tracks.size} tracks)")
        
    } finally {
        db.close()
    }
}

private fun escape(value: String): String = value.replace("'", "''")
