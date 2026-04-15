package com.cheroliv.newpipe

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Scanner

/**
 * Interactive CLI task for NewPipe Gradle Plugin.
 *
 * Provides a menu-driven interface for common plugin operations.
 */
open class InteractiveCliTask : DefaultTask() {

    private val scanner = Scanner(System.`in`)

    init {
        group = NewpipeManager.NEWPIPE_GROUP
        description = "Interactive CLI for NewPipe plugin operations"
    }

    @TaskAction
    fun execute() {
        printHeader()
        showMainMenu()
        scanner.close()
    }

    private fun printHeader() {
        println()
        println("╔══════════════════════════════════════════════════════════╗")
        println("║  NewPipe Gradle Plugin - Interface Interactive           ║")
        println("╚══════════════════════════════════════════════════════════╝")
        println()
    }

    private fun showMainMenu() {
        var running = true

        while (running) {
            println("╔══════════════════════════════════════════════════════════╗")
            println("║  Menu Principal                                          ║")
            println("╠══════════════════════════════════════════════════════════╣")
            println("║  1. 🔐 Configurer les sessions OAuth2                    ║")
            println("║  2. 📥 Lancer un téléchargement                          ║")
            println("║  3. 📊 Vérifier l'état des sessions                      ║")
            println("║  4. 📝 Ajouter une playlist                              ║")
            println("║  5. ⚙️  Configuration                                    ║")
            println("║  6. ❓ Aide                                              ║")
            println("║  7. 🚪 Quitter                                           ║")
            println("╚══════════════════════════════════════════════════════════╝")
            print("→ Choisissez une option (1-7) : ")

            val choice = readLine()?.trim()

            when (choice) {
                "1" -> configureSessionsMenu()
                "2" -> downloadMenu()
                "3" -> checkSessionStatus()
                "4" -> addPlaylistMenu()
                "5" -> configurationMenu()
                "6" -> showHelp()
                "7" -> {
                    println("\n✅ Au revoir !")
                    running = false
                }
                else -> println("\n❌ Option invalide. Veuillez choisir un nombre entre 1 et 7.\n")
            }
        }
    }

    private fun configureSessionsMenu() {
        println()
        println("╔══════════════════════════════════════════════════════════╗")
        println("║  Configuration des Sessions OAuth2                       ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  1. Générer les fichiers client_secrets                  ║")
        println("║  2. Authentifier les comptes                             ║")
        println("║  3. Retour au menu principal                             ║")
        println("╚══════════════════════════════════════════════════════════╝")
        print("→ Choisissez une option (1-3) : ")

        val choice = readLine()?.trim()

        when (choice) {
            "1" -> {
                println("\n🔧 Exécution de: ./gradlew buildSessions")
                executeGradleTask("buildSessions")
            }
            "2" -> {
                println("\n🔐 Exécution de: ./gradlew authSessions")
                executeGradleTask("authSessions")
            }
            "3" -> return
            else -> println("\n❌ Option invalide.\n")
        }
    }

    private fun downloadMenu() {
        println()
        println("╔══════════════════════════════════════════════════════════╗")
        println("║  Options de Téléchargement                               ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  1. Télécharger toutes les playlists                     ║")
        println("║  2. Télécharger une vidéo spécifique                     ║")
        println("║  3. Retour au menu principal                             ║")
        println("╚══════════════════════════════════════════════════════════╝")
        print("→ Choisissez une option (1-3) : ")

        val choice = readLine()?.trim()

        when (choice) {
            "1" -> {
                println("\n📥 Exécution de: ./gradlew downloadMusic")
                executeGradleTask("downloadMusic")
            }
            "2" -> {
                print("\n🎬 URL de la vidéo : ")
                val url = readLine()?.trim()
                if (!url.isNullOrBlank()) {
                    println("\n📥 Téléchargement de: $url")
                    // TODO: Implémenter le téléchargement d'une vidéo unique
                    println("⚠️  Fonctionnalité en cours de développement")
                } else {
                    println("\n❌ URL invalide.")
                }
            }
            "3" -> return
            else -> println("\n❌ Option invalide.\n")
        }
    }

    private fun checkSessionStatus() {
        println("\n📊 Vérification de l'état des sessions...")
        executeGradleTask("sessionStatus")
    }

    private fun addPlaylistMenu() {
        println()
        println("╔══════════════════════════════════════════════════════════╗")
        println("║  Ajouter une Playlist                                    ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  Cette option permet d'ajouter une playlist à musics.yml ║")
        println("╚══════════════════════════════════════════════════════════╝")
        println()

        print("🎵 Nom de l'artiste : ")
        val artistName = readLine()?.trim()

        if (artistName.isNullOrBlank()) {
            println("\n❌ Nom d'artiste invalide.")
            return
        }

        print("🔗 URL de la playlist : ")
        val playlistUrl = readLine()?.trim()

        if (playlistUrl.isNullOrBlank()) {
            println("\n❌ URL invalide.")
            return
        }

        // TODO: Implémenter l'ajout de playlist à musics.yml
        println("\n⚠️  Fonctionnalité en cours de développement")
        println("   Veuillez ajouter manuellement dans musics.yml :")
        println()
        println("   artistes:")
        println("     - name: \"$artistName\"")
        println("       playlists:")
        println("         - url: \"$playlistUrl\"")
    }

    private fun configurationMenu() {
        println()
        println("╔══════════════════════════════════════════════════════════╗")
        println("║  Configuration                                           ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  Fichiers de configuration :                             ║")
        println("║  - sessions.yml : Sessions OAuth2                        ║")
        println("║  - musics.yml : Playlists et vidéos                      ║")
        println("║  - client_secrets/ : Credentials OAuth2                  ║")
        println("╚══════════════════════════════════════════════════════════╝")
        println()
        println("⚠️  Appuyez sur Entrée pour retourner au menu principal...")
        readLine()
    }

    private fun showHelp() {
        println()
        println("╔══════════════════════════════════════════════════════════╗")
        println("║  Aide - NewPipe Gradle Plugin                            ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║                                                          ║")
        println("║  Ce plugin permet de télécharger des vidéos YouTube en   ║")
        println("║  utilisant l'authentification OAuth2 pour accéder à :    ║")
        println("║  - Vidéos réservées aux membres                          ║")
        println("║  - Vidéos avec restriction d'âge                         ║")
        println("║  - Playlists privées                                     ║")
        println("║                                                          ║")
        println("║  Commandes principales :                                 ║")
        println("║  - ./gradlew buildSessions : Générer credentials         ║")
        println("║  - ./gradlew authSessions : Authentifier comptes         ║")
        println("║  - ./gradlew sessionStatus : État des sessions           ║")
        println("║  - ./gradlew downloadMusic : Télécharger playlists       ║")
        println("║                                                          ║")
        println("╚══════════════════════════════════════════════════════════╝")
        println()
        println("⚠️  Appuyez sur Entrée pour retourner au menu principal...")
        readLine()
    }

    private fun executeGradleTask(taskName: String) {
        try {
            val projectDir = project.projectDir
            val gradleWrapper = if (isWindows()) "gradlew.bat" else "gradlew"
            val command = listOf("$projectDir/$gradleWrapper", taskName)

            println("\n⏳ Exécution de la tâche $taskName...")
            println("   (Cette tâche peut prendre quelques secondes)")
            println()

            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(projectDir)
            processBuilder.inheritIO()

            val process = processBuilder.start()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                println("\n✅ Tâche $taskName terminée avec succès !")
            } else {
                println("\n❌ La tâche $taskName a échoué (code: $exitCode)")
            }
        } catch (e: Exception) {
            println("\n❌ Erreur lors de l'exécution : ${e.message}")
        }
        println()
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name").lowercase().contains("win")
    }

    private fun readLine(): String? {
        return try {
            scanner.nextLine()
        } catch (e: Exception) {
            null
        }
    }
}
