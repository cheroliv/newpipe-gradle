package com.mp3organizer.ai

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.ollama.OllamaChatModel
import mu.KotlinLogging
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Client LLM local via Ollama
 * Pour toutes les tâches IA: traduction prompt→SQL, maintenance, etc.
 * Aligné sur l'API plantuml-gradle (langchain4j 1.12.2)
 */
class LlmClient(
    private val baseUrl: String = "http://localhost:11434",
    private val modelName: String = "gemma4:e4b-it-q4_K_M",
    private val temperature: Double = 0.7,
    private val timeoutSeconds: Long = 120
) {
    private val model: ChatModel by lazy {
        OllamaChatModel.builder()
            .baseUrl(baseUrl)
            .modelName(modelName)
            .temperature(temperature)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .build()
    }
    
    /**
     * Exécute un prompt et retourne la réponse
     */
    suspend fun chat(prompt: String, systemMessage: String? = null): String {
        val fullPrompt = if (systemMessage != null) {
            """$systemMessage

USER: $prompt

ASSISTANT:"""
        } else prompt
        
        return try {
            logger.info("Envoi prompt à Ollama ($modelName)...")
            model.chat(fullPrompt).also {
                logger.debug("Réponse: ${it.take(200)}...")
            }
        } catch (e: Exception) {
            logger.error("Erreur LLM: ${e.message}")
            throw LlmException("Échec appel LLM: ${e.message}", e)
        }
    }
    
    /**
     * Exécute un prompt avec contexte (pour SQL generation)
     */
    suspend fun chatWithContext(
        prompt: String,
        context: String,
        systemMessage: String = DEFAULT_SQL_SYSTEM_MESSAGE
    ): String {
        val fullPrompt = """$systemMessage

CONTEXTE:
$context

QUESTION/TÂCHE:
$prompt

RÉPONSE:"""
        
        return chat(fullPrompt, null)
    }
    
    companion object {
        const val DEFAULT_SQL_SYSTEM_MESSAGE = """
Tu es un assistant IA expert en PostgreSQL et SQL.
Ta tâche est de traduire des requêtes en langage naturel en SQL PostgreSQL valide.

Règles:
1. Utilise uniquement PostgreSQL
2. Retourne UNIQUEMENT le SQL, sans explications
3. Utilise les fonctions stockées fn_* et vues v_* quand c'est pertinent
4. Optimise les requêtes pour la performance
5. Utilise des CTE pour la lisibilité
"""
        
        const val DEFAULT_MAINTENANCE_SYSTEM_MESSAGE = """
Tu es un assistant IA expert en maintenance de code Kotlin et Gradle.
Ta tâche est d'aider à la maintenance, refactorisation et débogage.

Règles:
1. Fournis du code Kotlin/Gradle DSL valide
2. Explique brièvement tes changements
3. Suis les best practices Kotlin
4. Privilégie la simplicité et la lisibilité
"""
    }
}

class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause)
