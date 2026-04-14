package com.mp3organizer

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Processus complet - wrapper autour des fonctions PostgreSQL
 * Supporte MULTIPLES dossiers sources
 * 
 * Usage:
 *   ./gradlew fullProcess --args="/path1 /path2 /path3"
 */
fun main(args: Array<String>) = runBlocking {
    val config = DatabaseConfig()
    val db = Database(config)
    val scanner = Mp3Scanner(db)
    
    try {
        db.init()
        db.createSchema()
        
        logger.info("=" .repeat(60))
        logger.info("MP3 ORGANIZER - PROCESSUS COMPLET")
        logger.info("=" .repeat(60))
        
        // Parser les chemins sources multiples
        val scanPaths = if (args.isNotEmpty()) {
            args.joinToString(" ")
                .split(Regex("[,;\\s]+"))
                .filter { it.isNotBlank() }
        } else {
            listOf("/media/cheroliv/PHILIPS UFD")
        }
        
        logger.info("\n[1/6] Scan MP3 - ${scanPaths.size} dossier(s)...")
        scanPaths.forEach { logger.info("  → $it") }
        scanner.scanDirectories(scanPaths)
        
        // 2. Export JSON
        logger.info("\n[2/6] Export JSON...")
        runExternalTask("exportJson")
        
        // 3. Export YAML
        logger.info("\n[3/6] Export YAML...")
        runExternalTask("exportYaml")
        
        // 4. Export XML
        logger.info("\n[4/6] Export XML...")
        runExternalTask("exportXml")
        
        // 5. Export SQL
        logger.info("\n[5/6] Export SQL...")
        runExternalTask("exportSql")
        
        // 6. Business queries
        logger.info("\n[6/6] Requêtes métier...")
        runExternalTask("businessQueries")
        
        logger.info("\n" + "=" .repeat(60))
        logger.info("PROCESSUS TERMINÉ")
        logger.info("=" .repeat(60))
        
    } finally {
        db.close()
    }
}

private fun runExternalTask(taskName: String) {
    try {
        val process = ProcessBuilder("./gradlew", taskName)
            .directory(File("."))
            .start()
        process.waitFor()
    } catch (e: Exception) {
        logger.warn("Task $taskName failed: ${e.message}")
    }
}
