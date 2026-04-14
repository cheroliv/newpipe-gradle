package com.mp3organizer.ai

import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Agent IA pour la maintenance du projet
 * - Vérification dépendances
 * - Détection code dupliqué
 * - Suggestions de refactorisation
 */
class MaintenanceAgent(private val llm: LlmClient) {
    
    /**
     * Analyse les dépendances et suggère des mises à jour
     */
    suspend fun analyzeDependencies(buildFile: File): DependencyAnalysis {
        require(buildFile.exists()) { "Build file not found: ${buildFile.path}" }
        
        val buildContent = buildFile.readText()
        
        val prompt = """
Analyse ce fichier build.gradle.kts et identifie:
1. Les dépendances obsolètes
2. Les versions qui pourraient être mises à jour
3. Les dépendances inutilisées potentielles
4. Les conflits de version potentiels

Fichier build.gradle.kts:
```kotlin
$buildContent
```

Retourne un rapport structuré en markdown."""
        
        val analysis = llm.chat(prompt, LlmClient.DEFAULT_MAINTENANCE_SYSTEM_MESSAGE)
        
        return DependencyAnalysis(
            buildFilePath = buildFile.canonicalPath,
            analysisReport = analysis,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Détecte le code dupliqué dans un fichier Kotlin
     */
    suspend fun detectDuplicateCode(sourceFile: File): DuplicateCodeReport {
        require(sourceFile.exists()) { "File not found: ${sourceFile.path}" }
        
        val content = sourceFile.readText()
        
        val prompt = """
Analyse ce fichier Kotlin et détecte:
1. Le code dupliqué (fonctions similaires, blocs répétitifs)
2. Les patterns qui pourraient être refactorisés
3. Les violations de DRY (Don't Repeat Yourself)

Fichier: ${sourceFile.name}
```kotlin
$content
```

Liste les duplications avec les numéros de ligne."""
        
        val report = llm.chat(prompt, LlmClient.DEFAULT_MAINTENANCE_SYSTEM_MESSAGE)
        
        return DuplicateCodeReport(
            filePath = sourceFile.canonicalPath,
            duplicatesFound = report.contains("dupliqué", ignoreCase = true) || 
                             report.contains("répété", ignoreCase = true) ||
                             report.contains("duplicate", ignoreCase = true),
            report = report
        )
    }
    
    /**
     * Suggère des améliorations de code
     */
    suspend fun suggestRefactorings(sourceFile: File): RefactoringSuggestions {
        require(sourceFile.exists()) { "File not found: ${sourceFile.path}" }
        
        val content = sourceFile.readText()
        
        val prompt = """
Propose des améliorations pour ce code Kotlin:
1. Refactorisations pour améliorer la lisibilité
2. Optimisations de performance
3. Meilleures pratiques Kotlin à appliquer
4. Réduction de complexité

Fichier: ${sourceFile.name}
```kotlin
$content
```

Pour chaque suggestion:
- Explique le problème
- Montre le code avant/après
- Justifie l'amélioration"""
        
        val suggestions = llm.chat(prompt, LlmClient.DEFAULT_MAINTENANCE_SYSTEM_MESSAGE)
        
        return RefactoringSuggestions(
            filePath = sourceFile.canonicalPath,
            suggestions = suggestions,
            hasSuggestions = suggestions.isNotBlank()
        )
    }
    
    /**
     * Analyse la structure du projet
     */
    suspend fun analyzeProjectStructure(projectRoot: File): ProjectAnalysis {
        val files = projectRoot.walk()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "kts") }
            .take(50) // Limiter pour éviter timeout LLM
            .toList()
        
        val structurePrompt = """
Voici la structure de fichiers d'un projet Kotlin/Gradle:
${files.joinToString("\n") { it.canonicalPath.removePrefix(projectRoot.canonicalPath) }}

Analyse:
1. L'organisation est-elle claire?
2. Y a-t-il des incohérences de naming?
3. La séparation des responsabilités est-elle respectée?
4. Suggestions d'amélioration"""
        
        val analysis = llm.chat(structurePrompt, LlmClient.DEFAULT_MAINTENANCE_SYSTEM_MESSAGE)
        
        return ProjectAnalysis(
            projectPath = projectRoot.canonicalPath,
            filesAnalyzed = files.size,
            analysisReport = analysis
        )
    }
    
    /**
     * Vérifie la cohérence des imports
     */
    suspend fun checkImportConsistency(sourceFiles: List<File>): ImportReport {
        val importsByFile = sourceFiles.associate { file ->
            file.name to file.readLines()
                .takeWhile { it.startsWith("import") }
                .map { it.removePrefix("import ").trim() }
        }
        
        val prompt = """
Analyse la cohérence des imports dans ces fichiers Kotlin:

${importsByFile.map { (file, imports) -> "$file:\n${imports.joinToString("\n") { "  - $it" }}" }.joinToString("\n\n")}

Problèmes potentiels:
1. Imports inutilisés
2. Imports redondants
3. Wildcard imports (*) à éviter
4. Incohérences entre fichiers"""
        
        val report = llm.chat(prompt, LlmClient.DEFAULT_MAINTENANCE_SYSTEM_MESSAGE)
        
        return ImportReport(
            filesAnalyzed = sourceFiles.size,
            report = report
        )
    }
    
    data class DependencyAnalysis(
        val buildFilePath: String,
        val analysisReport: String,
        val timestamp: Long
    )
    
    data class DuplicateCodeReport(
        val filePath: String,
        val duplicatesFound: Boolean,
        val report: String
    )
    
    data class RefactoringSuggestions(
        val filePath: String,
        val suggestions: String,
        val hasSuggestions: Boolean
    )
    
    data class ProjectAnalysis(
        val projectPath: String,
        val filesAnalyzed: Int,
        val analysisReport: String
    )
    
    data class ImportReport(
        val filesAnalyzed: Int,
        val report: String
    )
}
