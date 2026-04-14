package com.mp3organizer.ai

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Tâche: Maintenance du projet avec IA
 * - Analyse dépendances
 * - Détection code dupliqué
 * - Suggestions refactorisation
 * 
 * Usage: ./gradlew maintenance
 */
fun main(args: Array<String>) = runBlocking {
    val projectRoot = File(".")
    val buildFile = File("build.gradle.kts")
    val sourceFiles = projectRoot.walk()
        .filter { it.isFile && it.extension == "kt" && !it.path.contains("/build/") }
        .take(20)
        .toList()
    
    logger.info("=" .repeat(60))
    logger.info("MAINTENANCE DU PROJET AVEC IA")
    logger.info("=" .repeat(60))
    
    val llm = LlmClient()
    val maintenanceAgent = MaintenanceAgent(llm)
    
    try {
        // 1. Analyse dépendances
        logger.info("\n[1/4] Analyse des dépendances...")
        if (buildFile.exists()) {
            val depAnalysis = maintenanceAgent.analyzeDependencies(buildFile)
            logger.info(depAnalysis.analysisReport)
        } else {
            logger.warn("build.gradle.kts non trouvé")
        }
        
        // 2. Détection code dupliqué
        logger.info("\n[2/4] Détection code dupliqué...")
        sourceFiles.take(5).forEach { file ->
            val report = maintenanceAgent.detectDuplicateCode(file)
            if (report.duplicatesFound) {
                logger.info("\n${file.name}:")
                logger.info(report.report.take(500))
            }
        }
        
        // 3. Suggestions refactorisation
        logger.info("\n[3/4] Suggestions de refactorisation...")
        sourceFiles.take(3).forEach { file ->
            val suggestions = maintenanceAgent.suggestRefactorings(file)
            if (suggestions.hasSuggestions) {
                logger.info("\n${file.name}:")
                logger.info(suggestions.suggestions.take(500))
            }
        }
        
        // 4. Analyse structure projet
        logger.info("\n[4/4] Analyse structure projet...")
        val projectAnalysis = maintenanceAgent.analyzeProjectStructure(projectRoot)
        logger.info(projectAnalysis.analysisReport.take(1000))
        
        logger.info("\n" + "=" .repeat(60))
        logger.info("MAINTENANCE TERMINÉE")
        logger.info("=" .repeat(60))
        
    } catch (e: LlmException) {
        logger.error("Erreur LLM: ${e.message}")
        logger.error("Vérifie qu'Ollama tourne: ollama run gemma2:2b-it-q4_K_M")
    } catch (e: Exception) {
        logger.error("Erreur: ${e.message}", e)
    }
}
