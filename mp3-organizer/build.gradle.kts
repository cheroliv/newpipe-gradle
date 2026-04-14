import org.gradle.api.tasks.JavaExec

plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    application
}

group = "com.mp3organizer"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.jackson)
    implementation(libs.kotlinx.serialization.json)
    
    implementation(libs.r2dbc.postgresql)
    implementation(libs.r2dbc.pool)
    
    implementation(libs.bundles.logging)
    
    implementation(libs.jaudiotagger)
    implementation(libs.okhttp)
    
    implementation(libs.langchain4j)
    implementation(libs.langchain4j.ollama)
}

kotlin {
    jvmToolchain(24)
}

application {
    mainClass.set("com.mp3organizer.MainKt")
}

fun registerJavaExecTask(
    name: String,
    description: String,
    group: String,
    mainClass: String,
    vararg args: String = emptyArray()
) {
    tasks.register<JavaExec>(name) {
        this.description = description
        this.group = group
        classpath = sourceSets["main"].runtimeClasspath
        this.mainClass.set(mainClass)
        if (args.isNotEmpty()) {
            this.args = args.toList()
        }
    }
}

registerJavaExecTask(
    name = "startDb",
    description = "Start PostgreSQL container via Portainer API",
    group = "mp3-organizer",
    mainClass = "com.mp3organizer.PortainerClientKt"
)

registerJavaExecTask(
    name = "scanMp3",
    description = "Scan MP3 files and store metadata in PostgreSQL",
    group = "mp3-organizer",
    mainClass = "com.mp3organizer.Mp3ScannerKt",
    "/media/cheroliv/PHILIPS UFD"
)

registerJavaExecTask(
    name = "exportJson",
    description = "Export database to JSON",
    group = "mp3-organizer",
    mainClass = "com.mp3organizer.ExportJsonKt"
)

registerJavaExecTask(
    name = "exportYaml",
    description = "Export database to YAML",
    group = "mp3-organizer",
    mainClass = "com.mp3organizer.ExportYamlKt"
)

registerJavaExecTask(
    name = "exportXml",
    description = "Export database to XML",
    group = "mp3-organizer",
    mainClass = "com.mp3organizer.ExportXmlKt"
)

registerJavaExecTask(
    name = "exportSql",
    description = "Export database to SQL statements",
    group = "mp3-organizer",
    mainClass = "com.mp3organizer.ExportSqlKt"
)

registerJavaExecTask(
    name = "businessQueries",
    description = "Run business queries",
    group = "mp3-organizer",
    mainClass = "com.mp3organizer.BusinessQueriesKt"
)

registerJavaExecTask(
    name = "organizeFiles",
    description = "Organize MP3 files by artist",
    group = "mp3-organizer",
    mainClass = "com.mp3organizer.OrganizeFilesKt"
)

registerJavaExecTask(
    name = "fullProcess",
    description = "Full process: scan, export all formats, organize files",
    group = "mp3-organizer",
    mainClass = "com.mp3organizer.FullProcessKt"
)

registerJavaExecTask(
    name = "llmChat",
    description = "Chat interactif avec LLM local (Ollama)",
    group = "mp3-organizer-ai",
    mainClass = "com.mp3organizer.ai.ChatTaskKt"
)

registerJavaExecTask(
    name = "sqlPrompt",
    description = "Traduit un prompt en langage naturel en requête SQL",
    group = "mp3-organizer-ai",
    mainClass = "com.mp3organizer.ai.SqlPromptTaskKt"
)

registerJavaExecTask(
    name = "maintenance",
    description = "Analyse maintenance projet (dépendances, code dupliqué, refactorisation)",
    group = "mp3-organizer-ai",
    mainClass = "com.mp3organizer.ai.MaintenanceTaskKt"
)

registerJavaExecTask(
    name = "analyzeQuery",
    description = "Analyse et optimise une requête SQL avec IA",
    group = "mp3-organizer-ai",
    mainClass = "com.mp3organizer.ai.AnalyzeQueryTaskKt"
)

registerJavaExecTask(
    name = "playSmart",
    description = "LLM génère une playlist depuis la DB et lance VLC",
    group = "mp3-organizer-media",
    mainClass = "com.mp3organizer.ai.PlaySmartTaskKt"
)

registerJavaExecTask(
    name = "playArtist",
    description = "Génère une playlist par artiste et lance VLC",
    group = "mp3-organizer-media",
    mainClass = "com.mp3organizer.ai.PlayArtistTaskKt"
)

registerJavaExecTask(
    name = "playGenre",
    description = "Génère une playlist par genre et lance VLC",
    group = "mp3-organizer-media",
    mainClass = "com.mp3organizer.ai.PlayGenreTaskKt"
)
