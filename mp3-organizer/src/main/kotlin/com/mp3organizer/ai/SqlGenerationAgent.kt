package com.mp3organizer.ai

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Agent IA pour générer du SQL à partir de prompts en langage naturel
 * Utilise le schéma et les fonctions stockées comme contexte
 */
class SqlGenerationAgent(private val llm: LlmClient) {
    
    /**
     * Traduit un prompt en requête SQL
     */
    suspend fun translatePromptToSql(
        prompt: String,
        includeSchema: Boolean = true
    ): SqlGenerationResult {
        val schemaContext = if (includeSchema) buildSchemaContext() else ""
        
        val context = """
### SCHÉMA DE LA BASE DE DONNÉES

$tablesDescription

### FONCTIONS STOCKÉES DISPONIBLES
$functionsDescription

### VUES DISPONIBLES
$viewsDescription

$schemaContext
"""
        
        val sql = llm.chatWithContext(
            prompt = "Génère la requête SQL pour: $prompt",
            context = context,
            systemMessage = LlmClient.DEFAULT_SQL_SYSTEM_MESSAGE
        )
        
        return SqlGenerationResult(
            originalPrompt = prompt,
            generatedSql = extractSqlFromResponse(sql),
            success = sql.isNotBlank() && !sql.contains("error", ignoreCase = true)
        )
    }
    
    /**
     * Optimise une requête SQL existante
     */
    suspend fun optimizeQuery(sql: String): String {
        val prompt = """Optimise cette requête SQL PostgreSQL pour la performance:

$sql

Fournis uniquement la requête optimisée."""
        
        return llm.chat(prompt, LlmClient.DEFAULT_SQL_SYSTEM_MESSAGE)
            .let { extractSqlFromResponse(it) }
    }
    
    /**
     * Explique une requête SQL
     */
    suspend fun explainQuery(sql: String): String {
        val prompt = """Explique ce que fait cette requête SQL:

$sql

Donne une explication claire en français."""
        
        return llm.chat(prompt)
    }
    
    /**
     * Génère un rapport d'analyse de requête
     */
    suspend fun analyzeQuery(sql: String): QueryAnalysis {
        val explanation = explainQuery(sql)
        val optimized = try {
            optimizeQuery(sql)
        } catch (e: Exception) {
            sql
        }
        
        return QueryAnalysis(
            originalSql = sql,
            explanation = explanation,
            optimizedSql = optimized,
            isOptimized = optimized != sql
        )
    }
    
    private fun buildSchemaContext(): String = """
### CONTEXTE MÉTIER
Cette base gère une collection de fichiers MP3 avec:
- Artistes, albums, tracks, genres
- Métadonnées extraites des tags ID3
- Statistiques et vues agrégées
"""
    
    private fun extractSqlFromResponse(response: String): String {
        // Extraire SQL des balises de code
        val sqlBlockRegex = Regex("""```sql\s*(.*?)\s*```""", RegexOption.DOT_MATCHES_ALL)
        val codeBlockRegex = Regex("""```\s*(.*?)\s*```""", RegexOption.DOT_MATCHES_ALL)
        
        return sqlBlockRegex.find(response)?.groupValues?.get(1)
            ?: codeBlockRegex.find(response)?.groupValues?.get(1)
            ?: response.trim()
    }
    
    data class SqlGenerationResult(
        val originalPrompt: String,
        val generatedSql: String,
        val success: Boolean
    )
    
    data class QueryAnalysis(
        val originalSql: String,
        val explanation: String,
        val optimizedSql: String,
        val isOptimized: Boolean
    )
}

// Descriptions pour le contexte LLM
private val tablesDescription = """
TABLES:
- artists(id, name, created_at)
- albums(id, title, artist_id, year, created_at)
- tracks(id, title, artist_id, album_id, file_path, file_size_bytes, duration_seconds, bitrate_kbps, sample_rate, has_video, created_at)
- genres(id, name)
- track_genres(track_id, genre_id)
- scan_history(id, scan_path, started_at, completed_at, files_scanned, files_imported, files_skipped, files_failed)
- export_history(id, export_format, export_path, exported_at, record_count)
"""

private val functionsDescription = """
FONCTIONS STOCKÉES:
- fn_import_track(title, artist_name, album_title, album_year, file_path, file_size_bytes, duration, bitrate, sample_rate, has_video, genre_names) → track_id
- fn_upsert_artist(name) → artist_id
- fn_upsert_album(title, artist_id, year) → album_id
- fn_upsert_genre(name) → genre_id
- fn_delete_small_tracks(threshold_bytes) → deleted_count
- fn_get_files_to_organize() → TABLE(track_id, current_path, artist_name, target_folder, target_filename, target_path)
- fn_update_track_path(track_id, new_path) → BOOLEAN
- fn_export_full_collection() → TABLE(result JSONB)
- fn_business_stats() → TABLE(result JSONB)
"""

private val viewsDescription = """
VUES:
- v_tracks_full: tracks avec artiste, album, genres
- v_artist_stats: stats par artiste (track_count, album_count, size, duration)
- v_small_files: fichiers < 2MB
- v_tracks_by_genre: tracks groupés par genre
- v_collection_stats: stats globales collection
"""
