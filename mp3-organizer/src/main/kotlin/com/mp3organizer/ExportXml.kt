package com.mp3organizer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Export XML - appelle fn_export_full_collection() et convertit en XML
 */
fun main(args: Array<String>) = runBlocking {
    val config = DatabaseConfig()
    val db = Database(config)
    
    try {
        db.init()
        logger.info("Export XML...")
        
        // Appel fonction stockée
        val json = db.exportFullCollection()
        
        val mapper = XmlMapper().registerModule(KotlinModule.Builder().build())
        val jsonMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val data = jsonMapper.readTree(json)
        val xml = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data)
        
        val outputFile = File("exports/mp3-collection.xml")
        outputFile.parentFile.mkdirs()
        outputFile.writeText(xml)
        
        logger.info("✓ Export XML: ${outputFile.canonicalPath}")
        
    } finally {
        db.close()
    }
}
