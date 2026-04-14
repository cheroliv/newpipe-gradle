# YouTube MP3 Downloader (Kotlin + NewPipe + buildSrc)

Téléchargeur MP3 pour YouTube écrit en Kotlin, utilisant **NewPipe Extractor** pour l'extraction et FFmpeg pour la conversion audio.

**Architecture moderne avec `buildSrc`** : Tâche Gradle typée et plugin personnalisé pour un code propre et maintenable.

## 🎯 Fonctionnalités

- ✅ Téléchargement audio depuis YouTube via **NewPipe Extractor**
- ✅ Conversion automatique en MP3 (192 kbps)
- ✅ Métadonnées ID3 (titre, artiste, vignette)
- ✅ Progression du téléchargement en temps réel
- ✅ **Tâche Gradle typée** avec `@TaskAction`
- ✅ **Plugin Gradle personnalisé** dans `buildSrc`
- ✅ Gestion d'erreurs robuste avec coroutines Kotlin
- ✅ Architecture propre et extensible

## 📋 Prérequis

### Obligatoire
- **Java 11+** (OpenJDK ou Oracle JDK)
- **Gradle 7+** (ou utiliser le wrapper inclus)

### Optionnel (mais recommandé)
- **FFmpeg** installé sur le système pour la conversion MP3
  - Ubuntu/Debian: `sudo apt-get install ffmpeg`
  - macOS: `brew install ffmpeg`
  - Windows: Télécharger depuis [ffmpeg.org](https://ffmpeg.org/download.html)

> **Note:** Sans FFmpeg, le fichier audio sera téléchargé dans son format d'origine (M4A/WebM) sans conversion MP3.

## 🚀 Installation

1. Cloner le projet (ou copier tous les fichiers)
2. Se placer dans le répertoire du projet
3. Lancer le build initial:

```bash
./gradlew build
```

## 📖 Utilisation

### Commande principale

```bash
# Télécharger une vidéo YouTube en MP3
./gradlew downloadMusic --url="https://www.youtube.com/watch?v=dQw4w9WgXcQ"

# Spécifier un dossier de sortie personnalisé
./gradlew downloadMusic --url="https://www.youtube.com/watch?v=dQw4w9WgXcQ" --output="./mes_mp3"
```

### Options disponibles

- `--url=<youtube-url>` **(requis)** - L'URL de la vidéo YouTube à télécharger
- `--output=<path>` *(optionnel)* - Le dossier de destination (défaut: `./downloads`)

## 🏗️ Architecture du projet avec buildSrc

```
youtube-mp3-downloader/
├── build.gradle.kts              # Build principal ULTRA-SIMPLIFIÉ (9 lignes!)
├── settings.gradle.kts           # Configuration du projet
│
└── buildSrc/                     # Sources de build (plugins et tâches)
    ├── build.gradle.kts                    # Dépendances du build
    └── src/main/
        ├── kotlin/
        │   ├── downloader/
        │   │   ├── YouTubeDownloader.kt        # Extraction et téléchargement
        │   │   ├── DownloaderImpl.kt           # Implémentation HTTP NewPipe
        │   │   └── Mp3Converter.kt             # Conversion FFmpeg + métadonnées
        │   ├── tasks/
        │   │   └── DownloadMusicTask.kt        # Tâche Gradle typée
        │   └── plugins/
        │       └── YouTubeDownloaderPlugin.kt  # Plugin personnalisé
        └── resources/
            ├── logback.xml                     # Configuration logs
            └── META-INF/gradle-plugins/
                └── youtube-downloader-plugin.properties
```

### 🎨 Avantages de l'architecture buildSrc

#### ✅ **build.gradle.kts ultra-simplifié**
```kotlin
plugins {
    kotlin("jvm") version "1.9.22"
    id("youtube-downloader-plugin")  // Notre plugin !
}

group = "com.youtube.downloader"
version = "1.0.0"

repositories {
    mavenCentral()
}
```

**C'est tout !** Plus besoin de :
- ❌ Déclarer manuellement les dépendances
- ❌ Enregistrer la tâche avec `tasks.register`
- ❌ Configurer les repositories spéciaux (JitPack)

#### ✅ **Tâche Gradle typée et robuste**

Avant (approche simple) :
```kotlin
tasks.register("downloadMusic") {
    doLast {
        val url = project.findProperty("url") as String?
        // Code procédural...
    }
}
```

Maintenant (approche buildSrc) :
```kotlin
open class DownloadMusicTask : DefaultTask() {
    @get:Input
    @set:Option(option = "url", description = "...")
    var url: String = ""
    
    @TaskAction
    fun download() {
        // Code orienté objet, testable, réutilisable
    }
}
```

**Bénéfices :**
- 🎯 Type safety (erreurs à la compilation, pas au runtime)
- 📝 Auto-complétion dans l'IDE
- 🧪 Facilement testable
- 🔧 Options déclaratives avec `@Option`
- 📊 Intégration native avec Gradle (up-to-date checks, caching, etc.)

## 🔧 Dépendances principales

| Bibliothèque | Version | Usage |
|--------------|---------|-------|
| NewPipe Extractor | 0.23.1 | Extraction YouTube (alternative à yt-dlp) |
| Kotlin Coroutines | 1.7.3 | Programmation asynchrone |
| OkHttp | 4.12.0 | Client HTTP pour téléchargements |
| JAudioTagger | 3.0.1 | Métadonnées ID3 pour MP3 |
| SLF4J + Logback | 2.0.9 / 1.4.14 | Logging |

**Note :** Toutes les dépendances sont dans `buildSrc/build.gradle.kts`, pas dans le build principal.

## 📝 Exemple de sortie

```
============================================================
YouTube MP3 Downloader
============================================================
URL: https://www.youtube.com/watch?v=dQw4w9WgXcQ
Destination: /home/user/downloads
============================================================

[1/4] Extraction des informations de la vidéo...
✓ Titre: Rick Astley - Never Gonna Give You Up
✓ Artiste: Rick Astley
✓ Durée: 03:33

[2/4] Sélection du meilleur flux audio...
✓ Format: M4A
✓ Bitrate: 128 kbps

[3/4] Téléchargement de l'audio...
  Progression: 100% (5.12 MB / 5.12 MB)
✓ Téléchargement terminé: 5 MB

[4/4] Conversion en MP3 et ajout des métadonnées...
✓ Conversion terminée

============================================================
✓ SUCCÈS!
Fichier: /home/user/downloads/Rick Astley - Never Gonna Give You Up.mp3
Taille: 5 MB
============================================================
```

## 🛠️ Personnalisation

Voir [CUSTOMIZATION.md](CUSTOMIZATION.md) pour un guide complet.

## 📜 Licence

Ce projet est fourni à titre éducatif. Respectez les conditions d'utilisation de YouTube et les lois sur le droit d'auteur de votre pays.
