# Guide de Personnalisation

All customizable files live in `buildSrc/src/main/kotlin/`.

---

## 🎵 Modifier le bitrate MP3

**Fichier :** `buildSrc/src/main/kotlin/tasks/DownloadMusicTask.kt`

```kotlin
converter.convertToMp3(tempFile, mp3File, bitrate = "320k")
```

Options : `"128k"` (léger) · `"192k"` (défaut) · `"256k"` · `"320k"` (max)

---

## 📁 Changer le dossier de sortie par défaut

**Fichier :** `buildSrc/src/main/kotlin/plugins/YouTubeDownloaderPlugin.kt`

```kotlin
override fun apply(project: Project) {
    project.tasks.register("downloadMusic", DownloadMusicTask::class.java) {
        outputPath = "${System.getProperty("user.home")}/Music/YouTube"
    }
}
```

---

## 🏷️ Personnaliser les métadonnées ID3

**Fichier :** `buildSrc/src/main/kotlin/tasks/DownloadMusicTask.kt`

```kotlin
converter.addMetadata(
    mp3File  = mp3File,
    title    = title,
    artist   = artist,
    album    = "Ma Collection YouTube",   // ← modifier ici
    thumbnailUrl = thumbnailUrl
)
```

Ajouter d'autres tags dans `buildSrc/src/main/kotlin/downloader/Mp3Converter.kt` :

```kotlin
tag.setField(FieldKey.GENRE,    "Downloaded")
tag.setField(FieldKey.COMMENT,  "Téléchargé depuis YouTube")
tag.setField(FieldKey.COMPOSER, artist)
```

---

## 📝 Format du nom de fichier

**Fichier :** `buildSrc/src/main/kotlin/tasks/DownloadMusicTask.kt`

```kotlin
// Défaut : "Titre.mp3"
val mp3File = File(outputDir, "${sanitizedTitle}.mp3")

// Artiste - Titre
val mp3File = File(outputDir,
    "${downloader.sanitizeFileName(artist)} - ${sanitizedTitle}.mp3")

// Date - Titre
val mp3File = File(outputDir,
    "${java.time.LocalDate.now()} - ${sanitizedTitle}.mp3")
```

---

## 🌐 Proxy HTTP

**Fichier :** `buildSrc/src/main/kotlin/downloader/DownloaderImpl.kt`

```kotlin
private val client = OkHttpClient.Builder()
    .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("proxy.example.com", 8080)))
    .proxyAuthenticator { _, response ->
        val credential = Credentials.basic("user", "password")
        response.request.newBuilder()
            .header("Proxy-Authorization", credential)
            .build()
    }
    .build()
```

---

## ⏱️ Timeouts personnalisés

**Fichier :** `buildSrc/src/main/kotlin/downloader/DownloaderImpl.kt`

```kotlin
private val client = OkHttpClient.Builder()
    .readTimeout(60, TimeUnit.SECONDS)
    .connectTimeout(30, TimeUnit.SECONDS)
    .build()
```

---

## ➕ Ajouter une nouvelle tâche Gradle

1. Créer `buildSrc/src/main/kotlin/tasks/DownloadPlaylistTask.kt` :

```kotlin
open class DownloadPlaylistTask : DefaultTask() {

    @get:Input
    @set:Option(option = "playlist-url", description = "URL de la playlist")
    var playlistUrl: String = ""

    init {
        group = "youtube"
        description = "Télécharge une playlist YouTube complète"
    }

    @TaskAction
    fun download() {
        if (playlistUrl.isBlank())
            throw GradleException("--playlist-url requis")
        // ...logique de téléchargement...
    }
}
```

2. L'enregistrer dans le plugin (`YouTubeDownloaderPlugin.kt`) :

```kotlin
project.tasks.register("downloadPlaylist", DownloadPlaylistTask::class.java)
```

3. Utiliser :

```bash
./gradlew downloadPlaylist --playlist-url="https://youtube.com/playlist?list=..."
```

---

## 🎛️ DSL personnalisé (avancé)

Ajouter une extension pour configurer le plugin depuis `build.gradle.kts` :

```kotlin
// buildSrc/src/main/kotlin/extensions/YouTubeExtension.kt
open class YouTubeExtension {
    var defaultBitrate: String  = "192k"
    var defaultOutput:  String  = "downloads"
}

// Dans YouTubeDownloaderPlugin.kt
val ext = project.extensions.create("youtubeDownloader", YouTubeExtension::class.java)
project.tasks.register("downloadMusic", DownloadMusicTask::class.java) {
    outputPath = ext.defaultOutput
}
```

Puis dans `build.gradle.kts` :

```kotlin
youtubeDownloader {
    defaultBitrate = "320k"
    defaultOutput  = "/home/user/Music"
}
```

---

## 🔁 Retry automatique

```kotlin
// Dans DownloadMusicTask.kt
suspend fun downloadWithRetry(url: String, maxRetries: Int = 3): File {
    repeat(maxRetries) { attempt ->
        try {
            return downloader.downloadAudio(...)
        } catch (e: Exception) {
            if (attempt == maxRetries - 1) throw e
            logger.warn("Tentative ${attempt + 1} échouée, nouvelle tentative...")
            delay(1000L * (attempt + 1))
        }
    }
    error("Unreachable")
}
```

---

## 🔒 Validation d'URL

```kotlin
// Dans DownloadMusicTask.kt
private fun isValidYouTubeUrl(url: String): Boolean =
    Regex("""^https?://(www\.)?(youtube\.com/watch\?v=|youtu\.be/)[a-zA-Z0-9_\-]{11}.*""")
        .matches(url)

// Dans download()
if (!isValidYouTubeUrl(url))
    throw GradleException("URL YouTube invalide : $url")
```
