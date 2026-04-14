package com.mp3organizer.ai

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Tâche: Chat interactif avec LLM local
 * Pour poser des questions sur la DB, le code, etc.
 * 
 * Usage: ./gradlew llmChat --args="quelle est la structure de la DB?"
 */
fun main(args: Array<String>) = runBlocking {
    val prompt = args.joinToString(" ").takeIf { it.isNotBlank() }
        ?: run {
            logger.info("Usage: llmChat <votre question>")
            logger.info("Exemples:")
            logger.info("  ./gradlew llmChat --args=\"comment optimiser mes requêtes SQL?\"")
            logger.info("  ./gradlew llmChat --args=\"explique le schéma de la base MP3\"")
            return@runBlocking
        }
    
    val llm = LlmClient()
    
    try {
        logger.info("Question: $prompt")
        logger.info("Modèle: ${llm.modelName}")
        
        val response = llm.chat(prompt)
        
        logger.info("\n" + "=".repeat(60))
        logger.info("RÉPONSE IA:")
        logger.info("=".repeat(60))
        logger.info(response)
        logger.info("=".repeat(60))
        
    } catch (e: LlmException) {
        logger.error("Erreur LLM: ${e.message}")
        logger.error("\nPour utiliser cette tâche:")
        logger.error("1. Installe Ollama: https://ollama.ai")
        logger.error("2. Tire un modèle: ollama pull gemma2:2b-it-q4_K_M")
        logger.error("3. Lance Ollama: ollama serve")
    } catch (e: Exception) {
        logger.error("Erreur: ${e.message}", e)
    }
}
