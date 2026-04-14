# Architecture buildSrc - Guide Détaillé

## 🎯 Pourquoi buildSrc ?

### Problème avec l'approche traditionnelle

**Avant (build.gradle.kts monolithique) :**

```kotlin
// build.gradle.kts - 80 lignes, mélange de configuration et logique
plugins { /* ... */ }
dependencies { /* 20 lignes de dépendances */ }

tasks.register("downloadMusic") {
    doLast {
        // 50 lignes de code procédural
        val url = project.findProperty("url") as String?
        // Pas de type safety
        // Pas de réutilisabilité
        // Difficile à tester
    }
}
```

**❌ Problèmes :**
- Code mélangé (configuration + logique métier)
- Pas de type safety sur les paramètres
- Difficile à tester unitairement
- Pas d'auto-complétion IDE
- Duplication si plusieurs projets

### Solution avec buildSrc

**Maintenant (architecture séparée) :**

```
buildSrc/                    # Module de build compilé avant le projet
├── build.gradle.kts         # Dépendances du build uniquement
└── src/main/kotlin/
    ├── downloader/          # Logique métier réutilisable
    ├── tasks/               # Tâches Gradle typées
    └── plugins/             # Plugins personnalisés
    
build.gradle.kts             # 9 lignes ! Configuration pure
```

**✅ Avantages :**
- Séparation claire des responsabilités
- Type safety complet
- Testable unitairement
- Auto-complétion IDE
- Réutilisable entre projets

---

## 📦 Structure Complète

```
youtube-mp3-downloader/
│
├── build.gradle.kts                 # ← 9 LIGNES SEULEMENT !
├── settings.gradle.kts              # Configuration projet
├── gradle/wrapper/                  # Wrapper Gradle
│
└── buildSrc/                        # ← MODULE DE BUILD
    │
    ├── build.gradle.kts             # Dépendances pour le build
    │   ├── kotlin-dsl               # Support Kotlin DSL
    │   ├── NewPipe Extractor        # Extraction YouTube
    │   ├── OkHttp                   # Client HTTP
    │   ├── JAudioTagger             # Métadonnées MP3
    │   └── ...                      # Autres dépendances
    │
    └── src/main/
        │
        ├── kotlin/
        │   │
        │   ├── downloader/                      # Package métier
        │   │   ├── YouTubeDownloader.kt         # Classe métier réutilisable
        │   │   ├── DownloaderImpl.kt            # Implémentation technique
        │   │   └── Mp3Converter.kt              # Conversion audio
        │   │
        │   ├── tasks/                           # Tâches Gradle
        │   │   └── DownloadMusicTask.kt         # Tâche typée avec @TaskAction
        │   │
        │   └── plugins/                         # Plugins personnalisés
        │       └── YouTubeDownloaderPlugin.kt   # Enregistre les tâches
        │
        └── resources/
            ├── logback.xml                      # Configuration logging
            └── META-INF/gradle-plugins/
                └── youtube-downloader-plugin.properties  # Déclaration plugin
```

---

## 🔧 Fichiers Clés Expliqués

### 1. `build.gradle.kts` (racine) - ULTRA SIMPLE

```kotlin
plugins {
    kotlin("jvm") version "1.9.22"
    id("youtube-downloader-plugin")    // ← Notre plugin custom !
}

group = "com.youtube.downloader"
version = "1.0.0"

repositories {
    mavenCentral()
}
```

**C'est TOUT !** 
- ✅ Pas de dépendances à déclarer (dans buildSrc)
- ✅ Pas de tâches à enregistrer (le plugin s'en charge)
- ✅ Configuration pure et déclarative

### 2. `buildSrc/build.gradle.kts` - DÉPENDANCES DU BUILD

```kotlin
plugins {
    `kotlin-dsl`    // Support Kotlin DSL pour les scripts Gradle
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // Toutes les dépendances pour construire notre outil
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.23.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")
}
```

**Rôle :** Fournir les dépendances nécessaires pour compiler et exécuter le code dans buildSrc.

### 3. `buildSrc/src/main/kotlin/tasks/DownloadMusicTask.kt` - TÂCHE TYPÉE

```kotlin
open class DownloadMusicTask : DefaultTask() {
    
    @get:Input
    @set:Option(option = "url", description = "URL de la vidéo YouTube")
    var url: String = ""
    
    @get:Input
    @get:Optional
    @set:Option(option = "output", description = "Dossier de destination")
    var outputPath: String = ""
    
    @TaskAction
    fun download() {
        // Logique de téléchargement
        val downloader = YouTubeDownloader()
        val converter = Mp3Converter()
        
        runBlocking {
            // Workflow de téléchargement
        }
    }
}
```

**Avantages :**
- 🎯 **Type safety** : `url` est un `String`, pas `Any?`
- 📝 **Auto-complétion** : L'IDE connaît tous les champs
- 🧪 **Testable** : Peut créer des tests unitaires facilement
- 🔧 **Options déclaratives** : Annotations `@Option` pour CLI
- 📊 **Caching Gradle** : Support natif des inputs/outputs

### 4. `buildSrc/src/main/kotlin/plugins/YouTubeDownloaderPlugin.kt` - PLUGIN

```kotlin
class YouTubeDownloaderPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("downloadMusic", DownloadMusicTask::class.java) {
            outputPath = "${project.projectDir}/downloads"  // Config par défaut
        }
    }
}
```

**Rôle :** 
- Enregistrer automatiquement les tâches
- Configurer les valeurs par défaut
- Peut être étendu pour créer des DSL personnalisés

### 5. `META-INF/gradle-plugins/youtube-downloader-plugin.properties`

```properties
implementation-class=plugins.YouTubeDownloaderPlugin
```

**Rôle :** Déclare le plugin pour que Gradle puisse le trouver via `id("youtube-downloader-plugin")`.

---

## 🔄 Workflow de Compilation

```
1. Gradle démarre
   ↓
2. Compile buildSrc/ EN PREMIER
   ├── Télécharge les dépendances (NewPipe, OkHttp, etc.)
   ├── Compile les classes Kotlin
   └── Génère le plugin
   ↓
3. Applique build.gradle.kts principal
   ├── Charge le plugin youtube-downloader-plugin
   ├── Le plugin enregistre la tâche downloadMusic
   └── La tâche est maintenant disponible !
   ↓
4. Exécution : ./gradlew downloadMusic --url="..."
   ├── Gradle instancie DownloadMusicTask
   ├── Injecte les paramètres (url, outputPath)
   └── Appelle @TaskAction download()
```

---

## 🎨 Comparaison Avant/Après

### Approche Simple (Sans buildSrc)

**build.gradle.kts :**
```kotlin
// 80+ lignes de code

plugins { /* ... */ }

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.23.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    // ... 10+ lignes de dépendances
}

tasks.register("downloadMusic") {
    group = "youtube"
    description = "Télécharge..."
    
    doLast {
        val url = project.findProperty("url") as String?
            ?: throw GradleException("URL requise")
        
        val outputDir = project.findProperty("output") as String?
            ?: "${project.projectDir}/downloads"
        
        javaexec {
            mainClass.set("downloader.tasks.DownloadMusicTaskKt")
            classpath = sourceSets["main"].runtimeClasspath
            args = listOf(url, outputDir)
        }
    }
}

// src/main/kotlin/downloader/... (code applicatif)
```

**❌ Problèmes :**
- Tout est mélangé
- Difficile à maintenir
- Pas de type safety sur `url`
- Pas de réutilisabilité

### Approche buildSrc (Moderne)

**build.gradle.kts :**
```kotlin
// 9 lignes !

plugins {
    kotlin("jvm") version "1.9.22"
    id("youtube-downloader-plugin")
}

group = "com.youtube.downloader"
version = "1.0.0"

repositories {
    mavenCentral()
}
```

**buildSrc/build.gradle.kts :**
```kotlin
// Dépendances séparées
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.23.1")
    // ...
}
```

**buildSrc/src/main/kotlin/tasks/DownloadMusicTask.kt :**
```kotlin
// Tâche typée et réutilisable
open class DownloadMusicTask : DefaultTask() {
    @get:Input
    @set:Option(option = "url", ...)
    var url: String = ""
    
    @TaskAction
    fun download() { /* ... */ }
}
```

**✅ Avantages :**
- Séparation claire
- Type safety complet
- Réutilisable
- Testable
- Maintenable

---

## 🧪 Testabilité

### Sans buildSrc (Difficile à tester)

```kotlin
// Comment tester un bloc doLast { } ?
tasks.register("downloadMusic") {
    doLast {
        // Code procédural
        // Impossible à tester unitairement
    }
}
```

### Avec buildSrc (Facile à tester)

```kotlin
// buildSrc/src/test/kotlin/tasks/DownloadMusicTaskTest.kt
class DownloadMusicTaskTest {
    
    @Test
    fun `should throw exception when url is blank`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.create("test", DownloadMusicTask::class.java)
        
        task.url = ""
        
        assertThrows<GradleException> {
            task.download()
        }
    }
    
    @Test
    fun `should use default output path`() {
        val task = DownloadMusicTask()
        task.url = "https://youtube.com/..."
        
        // Test unitaire sur la logique métier
    }
}
```

---

## 🚀 Extensions Possibles

### 1. Ajouter de nouvelles tâches

```kotlin
// buildSrc/src/main/kotlin/tasks/DownloadPlaylistTask.kt
open class DownloadPlaylistTask : DefaultTask() {
    @get:Input
    @set:Option(option = "playlist-url", ...)
    var playlistUrl: String = ""
    
    @TaskAction
    fun downloadPlaylist() { /* ... */ }
}

// Dans le plugin
class YouTubeDownloaderPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("downloadMusic", DownloadMusicTask::class.java)
        project.tasks.register("downloadPlaylist", DownloadPlaylistTask::class.java)
    }
}
```

### 2. Créer un DSL personnalisé

```kotlin
// buildSrc/src/main/kotlin/extensions/YouTubeExtension.kt
open class YouTubeExtension {
    var defaultOutputDir: String = "downloads"
    var defaultBitrate: String = "192k"
}

// Dans le plugin
class YouTubeDownloaderPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("youtubeDownloader", YouTubeExtension::class.java)
        
        project.tasks.register("downloadMusic", DownloadMusicTask::class.java) {
            // Utiliser la config de l'extension
        }
    }
}

// Utilisation dans build.gradle.kts
youtubeDownloader {
    defaultOutputDir = "/home/user/Music"
    defaultBitrate = "320k"
}
```

### 3. Publier le plugin

```kotlin
// buildSrc → Gradle Plugin Portal
// Permet de réutiliser dans d'autres projets via :

plugins {
    id("com.example.youtube-downloader") version "1.0.0"
}
```

---

## 📊 Comparaison Performance

| Aspect | Sans buildSrc | Avec buildSrc |
|--------|---------------|---------------|
| Premier build | ~30s | ~35s (+compilation buildSrc) |
| Builds suivants | ~5s | ~5s (buildSrc caché) |
| Temps de dev | Lent (pas d'IDE) | Rapide (auto-complétion) |
| Maintenabilité | Faible | Élevée |
| Réutilisabilité | Nulle | Totale |
| Testabilité | Impossible | Facile |

**Conclusion :** Le léger coût initial est largement compensé par les gains en développement et maintenance.

---

## ✅ Checklist Migration vers buildSrc

- [x] Créer `buildSrc/build.gradle.kts` avec `kotlin-dsl`
- [x] Déplacer les dépendances vers buildSrc
- [x] Créer tâches typées avec `@TaskAction`
- [x] Créer plugin personnalisé
- [x] Déclarer le plugin dans META-INF
- [x] Simplifier `build.gradle.kts` principal
- [x] Tester : `./gradlew clean build`
- [x] Vérifier : `./gradlew tasks --all`

---

## 🎓 Ressources

- [Gradle buildSrc Documentation](https://docs.gradle.org/current/userguide/organizing_gradle_projects.html#sec:build_sources)
- [Custom Tasks](https://docs.gradle.org/current/userguide/custom_tasks.html)
- [Custom Plugins](https://docs.gradle.org/current/userguide/custom_plugins.html)
- [Kotlin DSL Primer](https://docs.gradle.org/current/userguide/kotlin_dsl.html)
