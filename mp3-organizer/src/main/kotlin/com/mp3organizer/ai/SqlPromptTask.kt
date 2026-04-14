package com.mp3organizer.ai

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Tâche: Interprète un prompt en langage naturel et génère du SQL
 * 
 * Usage: ./gradlew sqlPrompt --args="top 10 artistes par nombre de tracks"
 */
fun main(args: Array<String>) = runBlocking {
    val prompt = args.joinToString(" ").takeIf { it.isNotBlank() }
        ?: run {
            logger.error("Usage: sqlPrompt <votre prompt en français>")
            logger.error("Exemple: ./gradlew sqlPrompt --args=\"afficher les 10 artistes avec le plus de tracks\"")
            return@runBlocking
        }
    
    logger.info("Prompt: $prompt")
    
    val llm = LlmClient()
    val agent = SqlGenerationAgent(llm)
    
    try {
        logger.info("Génération SQL...")
        val result = agent.translatePromptToSql(prompt)
        
        if (result.success) {
            logger.info("\n" + "=".repeat(60))
            logger.info("SQL GÉNÉRÉ:")
            logger.info("=".repeat(60))
            logger.info(result.generatedSql)
            logger.info("=".repeat(60))
            
            // Sauvegarder dans un fichier
            val outputFile = File("exports/generated-query.sql")
            outputFile.parentFile.mkdirs()
            outputFile.writeText("-- Prompt: ${result.originalPrompt}\n-- Généré par IA\n\n${result.generatedSql}")
            logger.info("\nRequête sauvegardée: ${outputFile.canonicalPath}")
            
        } else {
            logger.error("Échec génération SQL")
            logger.error("Réponse brute: ${result.generatedSql}")
        }
        
    } catch (e: LlmException) {
        logger.error("Erreur LLM: ${e.message}")
        logger.error("Vérifie qu'Ollama tourne: ollama run gemma2:2b-it-q4_K_M")
    } catch (e: Exception) {
        logger.error("Erreur inattendue: ${e.message}", e)
    }
}
