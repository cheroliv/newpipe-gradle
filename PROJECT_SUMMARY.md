# 🎉 YouTube MP3 Downloader - Projet buildSrc Refactorisé

## ✅ Ce qui a changé

### Avant (approche simple)
```
youtube-mp3-downloader/
├── build.gradle.kts          (80+ lignes avec tout dedans)
├── src/main/kotlin/
│   └── downloader/
│       ├── YouTubeDownloader.kt
│       ├── Mp3Converter.kt
│       └── tasks/
│           └── DownloadMusicTask.kt
```

**Problèmes :**
- ❌ Code mélangé (config + logique)
- ❌ Pas de type safety
- ❌ Difficile à tester
- ❌ Pas réutilisable

### Maintenant (approche buildSrc) ✨
```
youtube-mp3-downloader/
├── build.gradle.kts          (9 lignes seulement!)
└── buildSrc/                 (module de build séparé)
    ├── build.gradle.kts      (dépendances)
    └── src/main/kotlin/
        ├── downloader/       (logique métier)
        ├── tasks/            (tâche Gradle typée)
        └── plugins/          (plugin personnalisé)
```

**Avantages :**
- ✅ Séparation claire des responsabilités
- ✅ Type safety complet avec @TaskAction
- ✅ Facilement testable
- ✅ Code réutilisable
- ✅ Auto-complétion IDE
- ✅ Plugin Gradle personnalisé

---

## 📦 Structure Finale

```
youtube-mp3-downloader/
│
├── build.gradle.kts                 # ⭐ 9 LIGNES ULTRA-SIMPLE !
├── settings.gradle.kts              # Configuration projet
├── .gitignore                       # Fichiers à ignorer
│
├── README.md                        # Documentation principale
├── QUICK_START.md                   # Guide démarrage rapide
├── BUILDSRC_ARCHITECTURE.md         # ⭐ Guide architecture buildSrc
├── example-usage.sh                 # Script d'exemple
│
├── gradle/wrapper/                  # Gradle wrapper
│   └── gradle-wrapper.properties
│
└── buildSrc/                        # ⭐ MODULE DE BUILD
    │
    ├── build.gradle.kts             # Dépendances du build
    │
    └── src/main/
        │
        ├── kotlin/
        │   │
        │   ├── downloader/                      # Package métier
        │   │   ├── YouTubeDownloader.kt         # Extraction YouTube
        │   │   ├── DownloaderImpl.kt            # HTTP pour NewPipe
        │   │   └── Mp3Converter.kt              # Conversion MP3
        │   │
        │   ├── tasks/                           # Tâches Gradle
        │   │   └── DownloadMusicTask.kt         # ⭐ Tâche typée
        │   │
        │   └── plugins/                         # Plugins
        │       └── YouTubeDownloaderPlugin.kt   # ⭐ Plugin custom
        │
        └── resources/
            ├── logback.xml                      # Configuration logs
            └── META-INF/gradle-plugins/
                └── youtube-downloader-plugin.properties  # Déclaration plugin
```

---

## 🎯 build.gradle.kts - LA SIMPLICITÉ

**TOUT LE FICHIER (9 lignes) :**

```kotlin
plugins {
    kotlin("jvm") version "1.9.22"
    id("youtube-downloader-plugin")    // ← Notre plugin !
}

group = "com.youtube.downloader"
version = "1.0.0"

repositories {
    mavenCentral()
}
```

**C'est TOUT !** Comparé à 80+ lignes avant.

---

## 🚀 Utilisation (inchangée)

```bash
# Télécharger une vidéo
./gradlew downloadMusic --url="https://www.youtube.com/watch?v=dQw4w9WgXcQ"

# Avec dossier personnalisé
./gradlew downloadMusic --url="URL" --output="./ma_musique"

# Voir toutes les tâches disponibles
./gradlew tasks --group=youtube
```

---

## 🏗️ Architecture buildSrc Expliquée

### 1. Plugin Personnalisé

**buildSrc/src/main/kotlin/plugins/YouTubeDownloaderPlugin.kt**

```kotlin
class YouTubeDownloaderPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Enregistre automatiquement la tâche
        project.tasks.register("downloadMusic", DownloadMusicTask::class.java) {
            outputPath = "${project.projectDir}/downloads"
        }
    }
}
```

**Rôle :** 
- Enregistre les tâches automatiquement
- Configure les valeurs par défaut
- Peut être étendu et publié

### 2. Tâche Typée

**buildSrc/src/main/kotlin/tasks/DownloadMusicTask.kt**

```kotlin
open class DownloadMusicTask : DefaultTask() {
    
    @get:Input
    @set:Option(option = "url", description = "URL YouTube")
    var url: String = ""
    
    @get:Input
    @get:Optional
    @set:Option(option = "output", description = "Dossier destination")
    var outputPath: String = ""
    
    @TaskAction
    fun download() {
        // Validation
        if (url.isBlank()) {
            throw GradleException("URL requise")
        }
        
        // Workflow de téléchargement
        val downloader = YouTubeDownloader()
        val converter = Mp3Converter()
        
        runBlocking {
            // 1. Extraction
            // 2. Téléchargement
            // 3. Conversion
            // 4. Métadonnées
        }
    }
}
```

**Avantages :**
- ✅ Type safety : `url` est un `String`, pas `Any?`
- ✅ Auto-complétion IDE complète
- ✅ Options CLI déclaratives avec `@Option`
- ✅ Facilement testable unitairement
- ✅ Support natif du caching Gradle

### 3. Logique Métier Séparée

**buildSrc/src/main/kotlin/downloader/**

Toute la logique métier (extraction YouTube, conversion MP3, etc.) est maintenant dans `buildSrc`, ce qui permet :

- 🔄 Réutilisation dans plusieurs projets
- 🧪 Tests unitaires faciles
- 🎯 Séparation des responsabilités
- 📦 Possibilité de publier comme bibliothèque

---

## 🎨 Pourquoi buildSrc ?

### Comparaison

| Aspect | Sans buildSrc | Avec buildSrc |
|--------|---------------|---------------|
| **build.gradle.kts** | 80+ lignes | **9 lignes** ✨ |
| **Type Safety** | ❌ `Any?` | ✅ Types forts |
| **Auto-complétion** | ❌ Non | ✅ Complète |
| **Testabilité** | ❌ Impossible | ✅ Facile |
| **Réutilisabilité** | ❌ Nulle | ✅ Totale |
| **Maintenabilité** | ⚠️ Faible | ✅ Élevée |
| **Extensibilité** | ⚠️ Limitée | ✅ Illimitée |

### Workflow de Compilation

```
1. Gradle démarre
   ↓
2. ⭐ Compile buildSrc/ EN PREMIER
   ├── Télécharge dépendances (NewPipe, OkHttp, etc.)
   ├── Compile les classes Kotlin
   └── Construit le plugin
   ↓
3. Applique build.gradle.kts
   ├── Charge youtube-downloader-plugin
   ├── Plugin enregistre downloadMusic
   └── Tâche disponible !
   ↓
4. ./gradlew downloadMusic --url="..."
```

---

## 🧪 Testabilité

### Avant (Impossible)
```kotlin
tasks.register("downloadMusic") {
    doLast {
        // Comment tester un bloc doLast {} ?
    }
}
```

### Maintenant (Facile)
```kotlin
class DownloadMusicTaskTest {
    @Test
    fun `should throw exception when url is blank`() {
        val task = DownloadMusicTask()
        task.url = ""
        
        assertThrows<GradleException> {
            task.download()
        }
    }
}
```

---

## 📚 Documentation

1. **README.md** - Documentation principale avec architecture buildSrc
2. **QUICK_START.md** - Guide démarrage rapide (3 étapes)
3. **BUILDSRC_ARCHITECTURE.md** - ⭐ Guide détaillé de l'architecture buildSrc
4. **example-usage.sh** - Script interactif d'exemple

---

## 🔧 Extensions Futures

Avec buildSrc, il est facile d'ajouter :

### 1. Nouvelles tâches
```kotlin
// buildSrc/src/main/kotlin/tasks/DownloadPlaylistTask.kt
open class DownloadPlaylistTask : DefaultTask() { /* ... */ }

// Dans le plugin
project.tasks.register("downloadPlaylist", DownloadPlaylistTask::class.java)
```

### 2. DSL personnalisé
```kotlin
youtubeDownloader {
    defaultBitrate = "320k"
    defaultOutput = "/home/user/Music"
}
```

### 3. Publication du plugin
```kotlin
// Publier sur Gradle Plugin Portal
plugins {
    id("com.example.youtube-downloader") version "1.0.0"
}
```

---

## ✅ Checklist

- [x] build.gradle.kts simplifié à 9 lignes
- [x] Tâche Gradle typée avec @TaskAction
- [x] Plugin personnalisé fonctionnel
- [x] Toutes les dépendances dans buildSrc
- [x] Documentation complète
- [x] Type safety complet
- [x] Testabilité assurée
- [x] Code réutilisable

---

## 🎓 Pour aller plus loin

- Lire **BUILDSRC_ARCHITECTURE.md** pour comprendre en détail
- Consulter la [documentation Gradle buildSrc](https://docs.gradle.org/current/userguide/organizing_gradle_projects.html#sec:build_sources)
- Étudier les [Custom Tasks](https://docs.gradle.org/current/userguide/custom_tasks.html)
- Explorer les [Custom Plugins](https://docs.gradle.org/current/userguide/custom_plugins.html)

---

## 🎉 Résultat Final

**Un projet professionnel, maintenable et extensible !**

- ✨ Architecture propre et moderne
- 🎯 Séparation claire des responsabilités
- 🧪 Facilement testable
- 📦 Réutilisable et extensible
- 🚀 Build ultra-simplifié (9 lignes!)

**Prêt pour la production !** 🚀
