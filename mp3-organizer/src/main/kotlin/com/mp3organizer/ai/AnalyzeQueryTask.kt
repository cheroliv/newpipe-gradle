package com.mp3organizer.ai

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Tâche: Analyse et optimise une requête SQL
 * 
 * Usage: 
 *   ./gradlew analyzeQuery --args="SELECT * FROM tracks"
 *   Ou lire depuis un fichier: ./gradlew analyzeQuery --args="@exports/my-query.sql"
 */
fun main(args: Array<String>) = runBlocking {
    val input = args.firstOrNull() ?: run {
        logger.error("Usage: analyzeQuery <requête SQL ou @fichier.sql>")
        return@runBlocking
    }
    
    val sql = if (input.startsWith("@")) {
        val file = File(input.removePrefix("@"))
        require(file.exists()) { "Fichier non trouvé: $file" }
        file.readText()
    } else input
    
    logger.info("Requête à analyser:")
    logger.info(sql)
    
    val llm = LlmClient()
    val agent = SqlGenerationAgent(llm)
    
    try {
        logger.info("\nAnalyse en cours...")
        val analysis = agent.analyzeQuery(sql)
        
        logger.info("\n" + "=".repeat(60))
        logger.info("EXPLICATION:")
        logger.info("=".repeat(60))
        logger.info(analysis.explanation)
        
        if (analysis.isOptimized) {
            logger.info("\n" + "=".repeat(60))
            logger.info("VERSION OPTIMISÉE:")
            logger.info("=".repeat(60))
            logger.info(analysis.optimizedSql)
            
            // Sauvegarder
            val outputFile = File("exports/optimized-query.sql")
            outputFile.parentFile.mkdirs()
            outputFile.writeText("-- Version optimisée\n${analysis.optimizedSql}")
            logger.info("\nSauvegardé: ${outputFile.canonicalPath}")
        } else {
            logger.info("\n✓ La requête est déjà optimisée")
        }
        
        logger.info("\n" + "=".repeat(60))
        
    } catch (e: LlmException) {
        logger.error("Erreur LLM: ${e.message}")
    } catch (e: Exception) {
        logger.error("Erreur: ${e.message}", e)
    }
}
