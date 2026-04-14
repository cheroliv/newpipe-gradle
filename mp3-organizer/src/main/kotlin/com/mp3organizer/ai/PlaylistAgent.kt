package com.mp3organizer.ai

import com.mp3organizer.Database
import com.mp3organizer.Playlist
import com.mp3organizer.PlaylistTrack
import io.r2dbc.pool.ConnectionPool
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Agent IA pour générer des playlists musicales depuis la base de données
 * Utilise le LLM pour interpréter les demandes en langage naturel
 */
class PlaylistAgent(
    private val llm: LlmClient,
    private val database: Database
) {
    
    /**
     * Génère une playlist basée sur un prompt en langage naturel
     */
    suspend fun generatePlaylist(prompt: String, maxTracks: Int = 20): Playlist {
        logger.info("Génération playlist pour: '$prompt'")
        
        // 1. Récupérer le contexte du schéma DB
        val schemaContext = buildSchemaContext()
        
        // 2. Demander au LLM de générer la requête SQL
        val sql = llm.chatWithContext(
            prompt = "Crée une requête SQL pour sélectionner des tracks: $prompt",
            context = schemaContext,
            systemMessage = """
Tu es un expert SQL PostgreSQL spécialisé dans les bases de données musicales.
Génère UNIQUEMENT la requête SQL, sans explications.
Utilise les tables et vues disponibles.
Retourne maximum $maxTracks tracks.
Colonnes requises: file_path, title, artist_name, album_title, duration_seconds.
"""
        )
        
        logger.debug("SQL généré: ${sql.trim()}")
        
        // 3. Exécuter la requête
        val tracks = executePlaylistQuery(sql.trim())
        
        // 4. Créer la playlist
        return Playlist(
            tracks = tracks,
            description = prompt
        )
    }
    
    /**
     * Génère une playlist par artiste
     */
    suspend fun generatePlaylistByArtist(artistName: String, maxTracks: Int = 20): Playlist {
        val sql = """
            SELECT t.file_path, t.title, a.name as artist_name, al.title as album_title, t.duration_seconds
            FROM tracks t
            JOIN artists a ON t.artist_id = a.id
            LEFT JOIN albums al ON t.album_id = al.id
            WHERE a.name ILIKE ${'$'}1
            ORDER BY t.created_at DESC
            LIMIT ${'$'}2
        """.trimIndent()
        
        val tracks = executeCustomQuery(sql, listOf(artistName, maxTracks))
        
        return Playlist(
            tracks = tracks,
            description = "Meilleurs titres de $artistName"
        )
    }
    
    /**
     * Génère une playlist par genre
     */
    suspend fun generatePlaylistByGenre(genreName: String, maxTracks: Int = 20): Playlist {
        val sql = """
            SELECT t.file_path, t.title, a.name as artist_name, al.title as album_title, t.duration_seconds
            FROM tracks t
            JOIN artists a ON t.artist_id = a.id
            LEFT JOIN albums al ON t.album_id = al.id
            JOIN track_genres tg ON t.id = tg.track_id
            JOIN genres g ON tg.genre_id = g.id
            WHERE g.name ILIKE ${'$'}1
            ORDER BY RANDOM()
            LIMIT ${'$'}2
        """.trimIndent()
        
        val tracks = executeCustomQuery(sql, listOf(genreName, maxTracks))
        
        return Playlist(
            tracks = tracks,
            description = "Playlist $genreName"
        )
    }
    
    /**
     * Génère une playlist aléatoire avec des critères
     */
    suspend fun generateRandomPlaylist(filters: Map<String, Any?>): Playlist {
        val whereClauses = mutableListOf<String>()
        val params = mutableListOf<Any>()
        var paramIndex = 1
        
        filters.forEach { (key, value) ->
            if (value != null) {
                when (key) {
                    "genre" -> {
                        whereClauses.add("g.name ILIKE ${'$'}${paramIndex++}")
                        params.add(value.toString())
                    }
                    "minDuration" -> {
                        whereClauses.add("t.duration_seconds >= ${'$'}${paramIndex++}")
                        params.add(value.toString().toInt())
                    }
                    "maxDuration" -> {
                        whereClauses.add("t.duration_seconds <= ${'$'}${paramIndex++}")
                        params.add(value.toString().toInt())
                    }
                    "year" -> {
                        whereClauses.add("al.year = ${'$'}${paramIndex++}")
                        params.add(value.toString().toInt())
                    }
                }
            }
        }
        
        val whereClause = if (whereClauses.isNotEmpty()) {
            "WHERE " + whereClauses.joinToString(" AND ")
        } else ""
        
        val sql = """
            SELECT DISTINCT t.file_path, t.title, a.name as artist_name, al.title as album_title, t.duration_seconds
            FROM tracks t
            JOIN artists a ON t.artist_id = a.id
            LEFT JOIN albums al ON t.album_id = al.id
            LEFT JOIN track_genres tg ON t.id = tg.track_id
            LEFT JOIN genres g ON tg.genre_id = g.id
            $whereClause
            ORDER BY RANDOM()
            LIMIT 20
        """.trimIndent()
        
        val tracks = executeCustomQuery(sql, params)
        
        return Playlist(
            tracks = tracks,
            description = "Playlist aléatoire: ${filters.entries.joinToString { "${it.key}=${it.value}" }}"
        )
    }
    
    private suspend fun executePlaylistQuery(sql: String): List<PlaylistTrack> {
        return database.executeRawQuery(sql) { row ->
            PlaylistTrack(
                path = row.get("file_path", String::class.java),
                title = row.get("title", String::class.java) ?: "Unknown",
                artist = row.get("artist_name", String::class.java) ?: "Unknown",
                album = row.get("album_title", String::class.java) ?: "Unknown",
                duration = row.get("duration_seconds", Int::class.java)
            )
        }
    }
    
    private suspend fun executeCustomQuery(sql: String, params: List<Any>): List<PlaylistTrack> {
        return database.executeParametrizedQuery(sql, params) { row ->
            PlaylistTrack(
                path = row.get("file_path", String::class.java),
                title = row.get("title", String::class.java) ?: "Unknown",
                artist = row.get("artist_name", String::class.java) ?: "Unknown",
                album = row.get("album_title", String::class.java) ?: "Unknown",
                duration = row.get("duration_seconds", Int::class.java)
            )
        }
    }
    
    private fun buildSchemaContext(): String = """
### SCHÉMA DE LA BASE DE DONNÉES MUSICALE

TABLES:
- artists(id, name, created_at)
- albums(id, title, artist_id, year, created_at)
- tracks(id, title, artist_id, album_id, file_path, file_size_bytes, duration_seconds, bitrate_kbps, sample_rate, has_video, created_at)
- genres(id, name)
- track_genres(track_id, genre_id)

VUES UTILES:
- v_tracks_full: tracks avec artiste, album, genres
- v_artist_stats: stats par artiste
- v_tracks_by_genre: tracks groupés par genre

EXEMPLES DE REQUÊTES:

1. Tracks par artiste:
SELECT t.file_path, t.title, a.name as artist_name, al.title as album_title, t.duration_seconds
FROM tracks t
JOIN artists a ON t.artist_id = a.id
LEFT JOIN albums al ON t.album_id = al.id
WHERE a.name ILIKE '%Miles%'
ORDER BY t.created_at DESC
LIMIT 10

2. Tracks par genre:
SELECT t.file_path, t.title, a.name as artist_name, al.title as album_title, t.duration_seconds
FROM tracks t
JOIN artists a ON t.artist_id = a.id
LEFT JOIN albums al ON t.album_id = al.id
JOIN track_genres tg ON t.id = tg.track_id
JOIN genres g ON tg.genre_id = g.id
WHERE g.name ILIKE '%jazz%'
ORDER BY RANDOM()
LIMIT 10

3. Tracks aléatoires avec durée:
SELECT t.file_path, t.title, a.name as artist_name, al.title as album_title, t.duration_seconds
FROM tracks t
JOIN artists a ON t.artist_id = a.id
LEFT JOIN albums al ON t.album_id = al.id
WHERE t.duration_seconds BETWEEN 180 AND 300
ORDER BY RANDOM()
LIMIT 10
"""
}

// Extensions pour Database
suspend inline fun <reified T> Database.executeRawQuery(
    sql: String,
    crossinline mapper: suspend (Any) -> T
): List<T> {
    return this.queryAsResultSet(sql) { row ->
        mapper(row)
    }
}

suspend inline fun <reified T> Database.executeParametrizedQuery(
    sql: String,
    params: List<Any>,
    crossinline mapper: suspend (Any) -> T
): List<T> {
    return this.queryAsResultSet(sql, params) { row ->
        mapper(row)
    }
}
