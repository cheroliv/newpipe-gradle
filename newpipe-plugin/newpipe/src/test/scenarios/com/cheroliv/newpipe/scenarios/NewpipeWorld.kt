package com.cheroliv.newpipe.scenarios

import com.cheroliv.newpipe.createConfigFile
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner.create
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import java.io.File.createTempFile

class NewpipeWorld {
    val log: Logger = getLogger(NewpipeWorld::class.java)

    // Scope de coroutines pour le scénario
    val scope = CoroutineScope(Default + SupervisorJob())

    // État partagé entre les steps
    var projectDir: File? = null
    var buildResult: BuildResult? = null
    var exception: Throwable? = null

    // Jobs asynchrones en cours
    private val asyncJobs = mutableListOf<Deferred<BuildResult>>()

    /**
     * Exécute une tâche Gradle de manière asynchrone
     */
    fun executeGradleAsync(vararg tasks: String): Deferred<BuildResult> {
        require(projectDir != null) { "Project directory must be initialized" }
        log.info("Starting async Gradle execution: ${tasks.joinToString(" ")}")
        return scope.async {
            try {
                create()
                    .withProjectDir(projectDir!!)
                    .withArguments(tasks.toList() + "--stacktrace")
                    .withPluginClasspath()
                    .build()
            } catch (e: Exception) {
                log.error("Gradle build failed", e)
                exception = e
                throw e
            }
        }.also { asyncJobs.add(it) }
    }

    /**
     * Exécute une tâche Gradle de manière synchrone
     */
    suspend fun executeGradle(vararg tasks: String)
            : BuildResult = executeGradleAsync(*tasks)
        .await()
        .also { buildResult = it }

    /**
     * Exécute une action avec un timeout
     */
    suspend fun <T> withTimeout(seconds: Long, block: suspend () -> T)
            : T = withTimeout(seconds * 1000) { block() }

    /**
     * Attend la fin de toutes les opérations asynchrones
     */
    suspend fun awaitAll() {
        if (asyncJobs.isNotEmpty()) {
            log.info("Waiting for ${asyncJobs.size} async operations...")
            asyncJobs.awaitAll()
            log.info("All async operations completed")
        }
    }

    /**
     * Nettoyage des ressources
     */
    @Suppress("unused")
    fun cleanup() {
        scope.cancel()
        projectDir?.deleteRecursively()
        projectDir = null
        buildResult = null
        exception = null
        asyncJobs.clear()
    }

    /**
     * Crée un projet Gradle de test
     */
    fun createGradleProject(configFileName: String = "musics.yml"): File {
        val pluginId = "com.cheroliv.newpipe"
        val buildScriptContent = "newpipe { configPath = file(\"$configFileName\").absolutePath }"
        createTempFile("gradle-test-", "").apply {
            delete()
            mkdirs()
        }.run {
            resolve("settings.gradle.kts")
                .apply { createNewFile() }
                .writeText(
                    "pluginManagement.repositories.gradlePluginPortal()\n" +
                            "rootProject.name = \"${name}\""
                )
            resolve("build.gradle.kts")
                .apply { createNewFile() }
                .writeText("plugins { id(\"$pluginId\") }\n$buildScriptContent")
            createConfigFile()
            projectDir = this
            return this
        }
    }
}

class NewpipeWorld {
    val log: Logger = getLogger(NewpipeWorld::class.java)

    val scope = CoroutineScope(Default + SupervisorJob())

    var projectDir: File? = null
    var buildResult: BuildResult? = null
    var exception: Throwable? = null

    private val asyncJobs = mutableListOf<Deferred<BuildResult>>()

    // ------------------------------------------------------------------
    // Gradle execution
    // ------------------------------------------------------------------

    fun executeGradleAsync(vararg tasks: String): Deferred<BuildResult> {
        require(projectDir != null) { "Project directory must be initialized" }
        log.info("Starting async Gradle execution: ${tasks.joinToString(" ")}")
        return scope.async {
            try {
                GradleRunner.create()
                    .withProjectDir(projectDir!!)
                    .withArguments(tasks.toList() + "--stacktrace")
                    .withPluginClasspath()
                    .build()
            } catch (e: Exception) {
                log.error("Gradle build failed", e)
                exception = e
                throw e
            }
        }.also { asyncJobs.add(it) }
    }

    suspend fun executeGradle(vararg tasks: String): BuildResult =
        executeGradleAsync(*tasks).await().also { buildResult = it }

    suspend fun <T> withTimeout(seconds: Long, block: suspend () -> T): T =
        withTimeout(seconds * 1000) { block() }

    suspend fun awaitAll() {
        if (asyncJobs.isNotEmpty()) {
            log.info("Waiting for ${asyncJobs.size} async operations...")
            asyncJobs.awaitAll()
            log.info("All async operations completed")
        }
    }

    @Suppress("unused")
    fun cleanup() {
        scope.cancel()
        projectDir?.deleteRecursively()
        projectDir = null
        buildResult = null
        exception = null
        asyncJobs.clear()
    }

    // ------------------------------------------------------------------
    // Project factories
    // ------------------------------------------------------------------

    /**
     * Minimal project — configPath only, no sessions — anonymous mode.
     */
    fun createGradleProject(configFileName: String = CONFIG_FILE): File =
        createTempProject(
            buildScriptExtra = """
                newpipe {
                    configPath = file("$configFileName").absolutePath
                }
            """.trimIndent()
        ).also { it.createConfigFile() }

    /**
     * Project wired to the REAL sessions.yml at newpipe-gradle/sessions.yml.
     * Used for @requires-sessions integration scenarios.
     * Also creates a fake client_secrets/ so buildSessions can run.
     */
    fun createGradleProjectWithRealSessions(): File {
        val sessionsAbsPath = realSessionsFile().absolutePath
        return createTempProject(
            buildScriptExtra = """
                newpipe {
                    configPath       = file("$CONFIG_FILE").absolutePath
                    sessionsPath     = file("$sessionsAbsPath")
                    clientSecretsDir = file("$CLIENT_SECRETS_DIR").absolutePath
                }
            """.trimIndent()
        ).also {
            it.createConfigFile()
            it.createClientSecretsDir()
        }
    }

    /**
     * Project with a sessions.yml containing NO refreshToken —
     * authSessions should detect Device Flow is needed.
     * Fake client_secrets/ is also created so buildSessions can run.
     */
    fun createGradleProjectWithEmptySessions(): File {
        val dir = createTempProject(buildScriptExtra = "")
        val sessionsFile = dir.resolve("sessions.yml").apply {
            writeText(
                """
                sessions:
                  - id: "test-account"
                    clientId: "$FAKE_CLIENT_ID"
                    clientSecret: "$FAKE_CLIENT_SECRET"
                    refreshToken: ""
                """.trimIndent()
            )
        }
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins { id("com.cheroliv.newpipe") }
            newpipe {
                configPath       = file("$CONFIG_FILE").absolutePath
                sessionsPath     = file("${sessionsFile.absolutePath}")
                clientSecretsDir = file("$CLIENT_SECRETS_DIR").absolutePath
            }
            """.trimIndent()
        )
        dir.createConfigFile()
        dir.createClientSecretsDir()
        return dir
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    fun realSessionsFile(): File = com.cheroliv.newpipe.realSessionsFile()

    fun outputContains(text: String): Boolean =
        buildResult?.output?.contains(text) == true

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private fun createTempProject(buildScriptExtra: String): File {
        val pluginId = "com.cheroliv.newpipe"
        return createTempFile("gradle-test-", "").apply {
            delete()
            mkdirs()
        }.also { dir ->
            dir.resolve("settings.gradle.kts")
                .apply { createNewFile() }
                .writeText(
                    "pluginManagement.repositories.gradlePluginPortal()\n" +
                            "rootProject.name = \"${dir.name}\""
                )
            dir.resolve("build.gradle.kts")
                .apply { createNewFile() }
                .writeText("plugins { id(\"$pluginId\") }\n$buildScriptExtra")
            projectDir = dir
        }
    }
}