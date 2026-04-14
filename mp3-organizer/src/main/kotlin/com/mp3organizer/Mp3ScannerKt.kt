package com.mp3organizer

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Scan MP3 - Supporte MULTIPLES dossiers sources
 * 
 * Usage:
 *   ./gradlew scanMp3 --args="/home/user/Music"
 *   ./gradlew scanMp3 --args="/home/user/Music /mnt/usb/Music /media/USB"
 *   ./gradlew scanMp3 --args="/path1 /path2 /path3"
 */
fun main(args: Array<String>) = runBlocking {
    val config = DatabaseConfig()
    val db = Database(config)
    val scanner = Mp3Scanner(db)
    
    try {
        db.init()
        db.createSchema()
        
        // Parser les chemins (séparés par espaces ou virgules)
        val scanPaths = if (args.isNotEmpty()) {
            args.joinToString(" ")
                .split(Regex("[,;\\s]+"))
                .filter { it.isNotBlank() }
        } else {
            listOf("/media/cheroliv/PHILIPS UFD")
        }
        
        logger.info("Scan MP3 - ${scanPaths.size} dossier(s) source(s)")
        scanPaths.forEach { logger.info("  → $it") }
        
        val count = scanner.scanDirectories(scanPaths)
        logger.info("$count fichiers importés")
        
    } finally {
        db.close()
    }
}
