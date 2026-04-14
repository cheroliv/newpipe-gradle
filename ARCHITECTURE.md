# 🏗️ Architecture Technique - NewPipe Gradle Ecosystem

**Dernière mise à jour** : 2026-04-14  
**Projets** : `newpipe-gradle`, `newpipe-plugin`, `mp3-organizer`  
**Stack** : Kotlin 2.3.20, Gradle 9.4.1, LangChain4j 1.12.2, Gemma4:e4b

---

## Stack Complète

```yaml
Langages:
  - Kotlin 2.3.20
  - Gradle DSL 9.4.1

IA / LLM:
  - LangChain4j 1.12.2
  - Ollama (local)
  - Modèle: gemma4:e4b-it-q4_K_M (9.6GB, multimodal)

YouTube:
  - NewPipeExtractor v0.24.0
  - OAuth2 Google API
  - Cookies de session

Base de données (mp3-organizer):
  - PostgreSQL 15+
  - R2DBC (reactive)
  - Connection Pool

Lecture:
  - VLC (via ProcessBuilder)
  - Format: XSPF playlists

Docker:
  - ffmpeg (conversion audio)
  - Portainer (gestion containers)
```

---

## Patterns d'Architecture

### 1. LLM Client Pattern (depuis plantuml-gradle)

```kotlin
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.ollama.OllamaChatModel

class LlmClient(
    private val baseUrl: String = "http://localhost:11434",
    private val modelName: String = "gemma4:e4b-it-q4_K_M"
) {
    private val model: ChatModel by lazy {
        OllamaChatModel.builder()
            .baseUrl(baseUrl)
            .modelName(modelName)
            .temperature(0.7)
            .build()
    }
    
    suspend fun chat(prompt: String, systemMessage: String? = null): String {
        return model.chat(buildFullPrompt(prompt, systemMessage))
    }
}
```

**Fichier de référence** : `mp3-organizer/src/main/kotlin/com/mp3organizer/ai/LlmClient.kt`

---

### 2. Agent Pattern

```kotlin
class SessionGeneratorAgent(private val llm: LlmClient) {
    suspend fun translatePromptToSession(prompt: String): Session {
        val sql = llm.chatWithContext(
            prompt = "Génère configuration pour: $prompt",
            context = YOUTUBE_SCHEMA,
            systemMessage = SESSION_SYSTEM_MESSAGE
        )
        return parseSession(sql)
    }
}
```

**Fichier de référence** : `mp3-organizer/src/main/kotlin/com/mp3organizer/ai/PlaylistAgent.kt`

---

### 3. Gradle Task Pattern

```kotlin
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
        if (args.isNotEmpty()) this.args = args.toList()
    }
}
```

**Fichier de référence** : `mp3-organizer/build.gradle.kts`

---

## Architecture Authentification YouTube

```
┌─────────────────────────────────────────────────────────────┐
│  Gradle Build                                                │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  downloadMusic Task                                          │
│  (DownloadMusicTask.kt)                                      │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  SessionManager                                              │
│  - Round-Robin entre sessions auth                           │
│  - Fallback anonyme si toutes sessions invalides             │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  DownloaderImpl                                              │
│  - Utilise NewPipe Extractor                                 │
│  - Injecte cookies OAuth2 dans les requêtes                  │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  YouTube API                                                 │
│  - Vidéos publiques (anonyme)                                │
│  - Vidéos réservées membres (authentifié requis)             │
│  - Vidéos geo-restreintes (authentifié requis)               │
└─────────────────────────────────────────────────────────────┘
```

**Fichiers clés** :
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/AuthSessionTask.kt`
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/SessionManager.kt`
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/DownloaderImpl.kt`

---

## Flux OAuth2 Device Flow

```
1. buildSessions
   ↓
2. Ouvre browser pour OAuth2 consent
   ↓
3. Google retourne authorization code
   ↓
4. Échange code contre tokens (access + refresh)
   ↓
5. Stocke tokens dans sessions.yml (chiffré)
   ↓
6. Utilise tokens pour API YouTube
```

**URLs OAuth2** :
- Device Code : `https://oauth2.googleapis.com/device/code`
- Token : `https://oauth2.googleapis.com/token`
- Scope : `https://www.googleapis.com/auth/youtube.readonly`

**Documentation** : Voir `doc/AUTH_GUIDE.md`

---

## Architecture LLM & IA

```
┌─────────────────────────────────────────────────────────────┐
│  GRADLE + LLM + PostgreSQL + VLC                             │
└─────────────────────────────────────────────────────────────┘

Prompt: "Je veux du jazz piano pour coder le soir"

         ▼
┌─────────────────┐
│  LLM (gemma4)   │ → Analyse l'intention + contexte DB
│  + DB Schema    │ → Génère requête SQL
└─────────────────┘
         │
         ▼
┌─────────────────┐
│  PostgreSQL     │ → Exécute requête
│  (R2DBC)        │ → Retourne liste de tracks
└─────────────────┘
         │
         ▼
┌─────────────────┐
│  Générateur     │ → Crée fichier .xspf (VLC playlist)
│  Playlist XSPF  │
└─────────────────┘
         │
         ▼
┌─────────────────┐
│  VLC            │ → Ouvre et joue la playlist
│  (Exec Task)    │
└─────────────────┘
```

**Fichiers de référence** :
- `mp3-organizer/src/main/kotlin/com/mp3organizer/ai/PlaylistAgent.kt`
- `mp3-organizer/src/main/kotlin/com/mp3organizer/playlist/XspfGenerator.kt`
- `mp3-organizer/src/main/kotlin/com/mp3organizer/tasks/PlaySmartTask.kt`

---

## API LangChain4j v1.12.2

```kotlin
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.ollama.OllamaChatModel

val model: ChatModel = OllamaChatModel.builder()
    .baseUrl("http://localhost:11434")
    .modelName("gemma4:e4b-it-q4_K_M")
    .temperature(0.7)
    .build()

val response: String = model.chat("prompt")
```

**Dépendances** :
```kotlin
implementation("dev.langchain4j:langchain4j:1.12.2")
implementation("dev.langchain4j:langchain4j-ollama:1.12.2")
```

---

## Modèle LLM : Gemma 4 E4B

**Spécifications** :
- **Taille** : 9.6GB
- **Contexte** : 128K tokens
- **Modalités** : Texte, Image, Audio (compréhension uniquement)
- **Type** : Edge model (optimisé local)
- **Force** : Raisonnement, code, compréhension langage naturel

**Installation** :
```bash
ollama pull gemma4:e4b-it-q4_K_M
ollama serve
```

**Lien** : https://ollama.com/library/gemma4

---

## Voir Aussi

- **Structure des projets** : `doc/PROJECT_STRUCTURE.md`
- **Guide d'authentification** : `doc/AUTH_GUIDE.md`
- **Stratégie de tests** : `doc/TESTING_STRATEGY.md`
- **Matrice de migration** : `doc/MIGRATION_MATRIX.md`

---

**Dernière mise à jour** : 2026-04-14
