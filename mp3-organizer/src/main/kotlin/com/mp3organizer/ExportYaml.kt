package com.mp3organizer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Export YAML - appelle fn_export_full_collection() et convertit en YAML
 */
fun main(args: Array<String>) = runBlocking {
    val config = DatabaseConfig()
    val db = Database(config)
    
    try {
        db.init()
        logger.info("Export YAML...")
        
        // Appel fonction stockée
        val json = db.exportFullCollection()
        
        val mapper = YAMLMapper().registerModule(KotlinModule.Builder().build())
        val jsonMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val data = jsonMapper.readTree(json)
        val yaml = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data)
        
        val outputFile = File("exports/mp3-collection.yaml")
        outputFile.parentFile.mkdirs()
        outputFile.writeText(yaml)
        
        logger.info("✓ Export YAML: ${outputFile.canonicalPath}")
        
    } finally {
        db.close()
    }
}
