package com.mp3organizer

import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

data class DatabaseConfig(
    val host: String = "localhost",
    val port: Int = 5432,
    val database: String = "mp3db",
    val username: String = "mp3user",
    val password: String = "mp3password"
)

/**
 * Wrapper minimal autour de PostgreSQL
 * TOUTE la logique métier est dans les fonctions/procédures stockées
 */
class Database(private val config: DatabaseConfig) {
    
    private val pool: ConnectionPool by lazy {
        val configuration = PostgresqlConnectionConfiguration.builder()
            .host(config.host)
            .port(config.port)
            .database(config.database)
            .username(config.username)
            .password(config.password)
            .build()
        
        val factory = PostgresqlConnectionFactory(configuration)
        ConnectionPool(ConnectionPoolConfiguration.builder(factory).maxSize(10).build())
    }
    
    suspend fun init() {
        logger.info("Connexion à PostgreSQL...")
        pool.create().awaitFirst().use { connection ->
            connection.createStatement("SELECT 1").execute().awaitFirstOrNull()
            logger.info("Connecté à ${config.database}@${config.host}:${config.port}")
        }
    }
    
    suspend fun createSchema() {
        logger.info("Création du schéma...")
        val schema = File("src/main/sql/01_schema.sql").readText()
        pool.create().awaitFirst().use { connection ->
            connection.createStatement(schema).execute().awaitFirstOrNull()
        }
        logger.info("Schéma créé")
    }
    
    suspend fun close() {
        logger.info("Fermeture connexion")
        pool.dispose()
    }
    
    // ========================================================================
    // WRAPPERS DES FONCTIONS STOCKÉES - AUCUNE LOGIQUE MÉTIER ICI
    // ========================================================================
    
    /**
     * Appelle fn_import_track et retourne l'ID du track
     */
    suspend fun importTrack(
        title: String,
        artistName: String,
        albumTitle: String? = null,
        albumYear: Int? = null,
        filePath: String,
        fileSizeBytes: Long,
        durationSeconds: Int? = null,
        bitrateKbps: Int? = null,
        sampleRate: Int? = null,
        hasVideo: Boolean = false,
        genreNames: List<String>? = null
    ): Int {
        val genresArray = genreNames?.let { "{${it.joinToString(",")}}" } ?: "NULL"
        
        return pool.create().awaitFirst().use { connection ->
            val sql = """
                SELECT fn_import_track(
                    $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11::TEXT[]
                ) AS track_id
            """.trimIndent()
            
            val result = connection.createStatement(sql)
                .bind(0, title)
                .bind(1, artistName)
                .bind(2, albumTitle)
                .bind(3, albumYear)
                .bind(4, filePath)
                .bind(5, fileSizeBytes)
                .bind(6, durationSeconds)
                .bind(7, bitrateKbps)
                .bind(8, sampleRate)
                .bind(9, hasVideo)
                .bind(10, genresArray)
                .execute()
                .awaitFirstOrNull()
            
            result?.map { row, _ -> row.get("track_id", Int::class.java) }
                ?.awaitFirstOrNull()
                ?: throw RuntimeException("fn_import_track failed")
        }
    }
    
    /**
     * Appelle fn_export_full_collection et retourne JSON
     */
    suspend fun exportFullCollection(): String {
        return pool.create().awaitFirst().use { connection ->
            val result = connection.createStatement(
                "SELECT result FROM fn_export_full_collection()"
            ).execute().awaitFirstOrNull()
            
            result?.map { row, _ -> row.get("result", String::class.java) }
                ?.awaitFirstOrNull()
                ?: "{}"
        }
    }
    
    /**
     * Appelle fn_business_stats et retourne JSON
     */
    suspend fun getBusinessStats(): String {
        return pool.create().awaitFirst().use { connection ->
            val result = connection.createStatement(
                "SELECT result FROM fn_business_stats()"
            ).execute().awaitFirstOrNull()
            
            result?.map { row, _ -> row.get("result", String::class.java) }
                ?.awaitFirstOrNull()
                ?: "{}"
        }
    }
    
    /**
     * Récupère les fichiers à organiser
     */
    suspend fun getFilesToOrganize(): List<Map<String, Any?>> {
        return pool.create().awaitFirst().use { connection ->
            val results = mutableListOf<Map<String, Any?>>()
            connection.createStatement(
                "SELECT * FROM fn_get_files_to_organize()"
            ).execute().awaitFirstOrNull()?.use { result ->
                result.map { row, metadata ->
                    metadata.columnMetadatas.associate { col ->
                        col.name to row.get(col.name, Any::class.java)
                    }
                }.collect { results.add(it) }
            }
            results
        }
    }
    
    /**
     * Met à jour le chemin d'un track
     */
    suspend fun updateTrackPath(trackId: Int, newPath: String): Boolean {
        return pool.create().awaitFirst().use { connection ->
            val result = connection.createStatement(
                "SELECT fn_update_track_path($1, $2)"
            ).bind(0, trackId).bind(1, newPath)
                .execute().awaitFirstOrNull()
            
            result?.map { row, _ -> row.get(0, Boolean::class.java) }
                ?.awaitFirstOrNull()
                ?: false
        }
    }
    
    /**
     * Supprime les petits fichiers
     */
    suspend fun deleteSmallFiles(thresholdBytes: Long = 2097152): Int {
        return pool.create().awaitFirst().use { connection ->
            val result = connection.createStatement(
                "SELECT fn_delete_small_tracks($1)"
            ).bind(0, thresholdBytes)
                .execute().awaitFirstOrNull()
            
            result?.map { row, _ -> row.get(0, Int::class.java) }
                ?.awaitFirstOrNull()
                ?: 0
        }
    }
    
    /**
     * Requête générique qui retourne JSON - pour toutes les vues
     */
    suspend fun queryAsJson(query: String): String {
        return pool.create().awaitFirst().use { connection ->
            val result = connection.createStatement(query)
                .execute().awaitFirstOrNull()
            
            result?.map { row, _ -> row.get(0, String::class.java) }
                ?.awaitFirstOrNull()
                ?: "[]"
        }
    }
    
    /**
     * Exécute une vue et retourne les rows brutes
     */
    suspend fun queryView(viewName: String, limit: Int = 1000): List<Map<String, Any?>> {
        return pool.create().awaitFirst().use { connection ->
            val results = mutableListOf<Map<String, Any?>>()
            connection.createStatement("SELECT * FROM $viewName LIMIT $limit")
                .execute().awaitFirstOrNull()?.use { result ->
                    result.map { row, metadata ->
                        metadata.columnMetadatas.associate { col ->
                            col.name to row.get(col.name, Any::class.java)
                        }
                    }.collect { results.add(it) }
                }
            results
        }
    }
    
    /**
     * Exécute une requête SQL et mappe les résultats
     */
    suspend inline fun <reified T> queryAsResultSet(
        sql: String,
        crossinline mapper: suspend (Any) -> T
    ): List<T> {
        return pool.create().awaitFirst().use { connection ->
            val results = mutableListOf<T>()
            connection.createStatement(sql)
                .execute().awaitFirstOrNull()?.use { result ->
                    result.map { row, _ -> mapper(row) }
                        .collect { results.add(it) }
                }
            results
        }
    }
    
    /**
     * Exécute une requête SQL paramétrée et mappe les résultats
     */
    suspend inline fun <reified T> queryAsResultSet(
        sql: String,
        params: List<Any>,
        crossinline mapper: suspend (Any) -> T
    ): List<T> {
        return pool.create().awaitFirst().use { connection ->
            val results = mutableListOf<T>()
            val statement = connection.createStatement(sql)
            
            params.forEachIndexed { index, param ->
                statement.bind(index, param)
            }
            
            statement.execute().awaitFirstOrNull()?.use { result ->
                result.map { row, _ -> mapper(row) }
                    .collect { results.add(it) }
            }
            results
        }
    }
}
