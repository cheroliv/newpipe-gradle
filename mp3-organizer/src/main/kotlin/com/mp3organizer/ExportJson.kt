package com.mp3organizer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Export JSON - appelle fn_export_full_collection() et écrit le résultat
 */
fun main(args: Array<String>) = runBlocking {
    val config = DatabaseConfig()
    val db = Database(config)
    
    try {
        db.init()
        logger.info("Export JSON...")
        
        // Appel fonction stockée - retourne JSON complet
        val json = db.exportFullCollection()
        
        // Pretty print
        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val prettyJson = mapper.writerWithDefaultPrettyPrinter()
            .writeValueAsString(mapper.readTree(json))
        
        val outputFile = File("exports/mp3-collection.json")
        outputFile.parentFile.mkdirs()
        outputFile.writeText(prettyJson)
        
        logger.info("✓ Export JSON: ${outputFile.canonicalPath}")
        
        // Log export dans DB
        db.update(
            "INSERT INTO export_history (export_format, export_path, record_count) VALUES ('json', $1, (SELECT COUNT(*) FROM tracks))",
            outputFile.canonicalPath
        )
        
    } finally {
        db.close()
    }
}
