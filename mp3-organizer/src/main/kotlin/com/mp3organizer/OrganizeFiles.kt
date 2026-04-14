package com.mp3organizer

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Organisation fichiers - utilise fn_get_files_to_organize() et fn_update_track_path()
 */
fun main(args: Array<String>) = runBlocking {
    val config = DatabaseConfig()
    val db = Database(config)
    
    try {
        db.init()
        
        logger.info("=" .repeat(60))
        logger.info("ORGANISATION DES FICHIERS")
        logger.info("=" .repeat(60))
        
        // Appel fonction stockée - retourne liste fichiers à organiser
        val filesToOrganize = db.getFilesToOrganize()
        
        logger.info("\n${filesToOrganize.size} fichiers à organiser")
        
        var movedCount = 0
        var errorCount = 0
        var bytesMoved = 0L
        
        val basePath = "/media/cheroliv/PHILIPS UFD"
        
        filesToOrganize.asFlow()
            .flowOn(Dispatchers.Default)
            .map { row ->
                withContext(Dispatchers.IO) {
                    try {
                        val trackId = (row["track_id"] as Int)
                        val currentPath = row["current_path"] as String
                        val targetPath = row["target_path"] as String
                        
                        val sourceFile = File(currentPath)
                        val destFile = File(basePath, targetPath)
                        
                        // Skip if same location
                        if (sourceFile.canonicalPath == destFile.canonicalPath) {
                            return@withContext null
                        }
                        
                        // Create artist folder
                        destFile.parentFile.mkdirs()
                        
                        // Skip if destination exists
                        if (destFile.exists()) {
                            logger.warn("Existe déjà: ${destFile.name}")
                            return@withContext null
                        }
                        
                        // Move file
                        sourceFile.copyTo(destFile, overwrite = false)
                        sourceFile.delete()
                        
                        // Update DB via fonction stockée
                        db.updateTrackPath(trackId, destFile.canonicalPath)
                        
                        movedCount++
                        bytesMoved += sourceFile.length()
                        
                        logger.info("✓ ${row["artist_name"]} - ${sourceFile.name}")
                        
                        trackId to destFile
                    } catch (e: Exception) {
                        errorCount++
                        logger.error("Erreur: ${e.message}")
                        null
                    }
                }
            }
            .filterNotNull()
            .collect { }
        
        logger.info("\n" + "=" .repeat(60))
        logger.info("Terminé:")
        logger.info("  Déplacés: $movedCount")
        logger.info("  Erreurs: $errorCount")
        logger.info("  Données: ${bytesMoved / (1024 * 1024)} MB")
        logger.info("=" .repeat(60))
        
    } finally {
        db.close()
    }
}
