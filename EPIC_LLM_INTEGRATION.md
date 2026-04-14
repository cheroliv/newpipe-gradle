# EPIC : Intégration LLM & Assistant IA pour NewPipe Plugin

**Projet Cible** : `/home/cheroliv/workspace/__repositories__/newpipe-gradle`  
**Projet Source** : `/home/cheroliv/Musique/abdo/mp3-organizer`  
**Référence Architecture** : `/home/cheroliv/workspace/__repositories__/plantuml-gradle`

**Dernière mise à jour** : 2026-04-14  
**Statut** : 🟢 En cours - Module playlist implémenté dans mp3-organizer

---

## 🎯 Objectif

Enrichir le plugin NewPipe Gradle avec des capacités IA pour :
- Générer automatiquement des configurations de sessions YouTube
- Suggérer des playlists basées sur des descriptions en langage naturel
- Assister la maintenance du code et des dépendances
- Traduire des prompts en requêtes de filtrage de vidéos
- **NOUVEAU** : Orchestrer VLC pour la lecture de playlists générées

---

## 📋 Fonctionnalités à Implémenter

### 1. Module LLM Client (Priorité : Haute) ✅
**Inspiré de** : `mp3-organizer/src/main/kotlin/com/mp3organizer/ai/LlmClient.kt`

**À créer dans** : `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/ai/LlmClient.kt`

**Spécifications** :
- Utiliser `ChatModel` de langchain4j 1.12.2 (aligné plantuml-gradle)
- Support Ollama en local (modèle : `gemma4:e4b-it-q4_K_M`)
- Méthodes : `chat()`, `chatWithContext()`
- Système de templates pour les prompts

**Dépendances à ajouter** :
```kotlin
implementation("dev.langchain4j:langchain4j:1.12.2")
implementation("dev.langchain4j:langchain4j-ollama:1.12.2")
```

**Statut** : ✅ **IMPLÉMENTÉ** dans mp3-organizer  
**Fichier** : `mp3-organizer/src/main/kotlin/com/mp3organizer/ai/LlmClient.kt`

---

### 2. Tâches Gradle IA (Priorité : Haute) ✅
**Inspiré de** : `mp3-organizer/build.gradle.kts`

**À intégrer dans** : `newpipe-plugin/newpipe/build.gradle.kts`

**Tâches créées dans mp3-organizer** :
```kotlin
// Groupe : mp3-organizer-media
- playSmart      : LLM génère playlist depuis DB + lance VLC
- playArtist     : Playlist par artiste + VLC
- playGenre      : Playlist par genre + VLC
- llmChat        : Chat interactif avec LLM
- sqlPrompt      : Traduit prompt → SQL
- maintenance    : Analyse maintenance code
- analyzeQuery   : Optimise requêtes SQL
```

**Pattern** : Utiliser `registerJavaExecTask()` comme dans mp3-organizer

**Statut** : ✅ **IMPLÉMENTÉ** dans mp3-organizer

---

### 3. Agents Spécialisés (Priorité : Haute) ✅

#### 3.1 PlaylistAgent ✅
**Fichier** : `mp3-organizer/src/main/kotlin/com/mp3organizer/ai/PlaylistAgent.kt`

**Rôle** : Traduire un prompt en playlist musicale
**Exemple** :
```
Prompt: "Je veux du jazz piano pour coder le soir"
→ Requête SQL → Tracks → Fichier XSPF → VLC
```

**Méthodes** :
- `generatePlaylist(prompt: String)` : Prompt naturel → SQL → Playlist
- `generatePlaylistByArtist(artistName: String)` : Playlist par artiste
- `generatePlaylistByGenre(genreName: String)` : Playlist par genre
- `generateRandomPlaylist(filters: Map)` : Playlist aléatoire avec filtres

**Statut** : ✅ **IMPLÉMENTÉ** dans mp3-organizer

#### 3.2 XspfGenerator ✅
**Fichier** : `mp3-organizer/src/main/kotlin/com/mp3organizer/playlist/XspfGenerator.kt`

**Rôle** : Générer des fichiers playlist au format XSPF (compatible VLC)

**Statut** : ✅ **IMPLÉMENTÉ** dans mp3-organizer

#### 3.3 SessionGeneratorAgent (À adapter pour NewPipe)
**Fichier** : `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/ai/SessionGeneratorAgent.kt`

**Rôle** : Traduire un prompt en configuration de session YouTube
**Exemple** :
```
Prompt: "Je veux écouter du jazz piano des années 60"
→ Génère session NewPipe avec filtres appropriés
```

**Statut** : ⏳ **À CRÉER** dans newpipe-plugin

#### 3.4 MaintenanceAgent
**Fichier** : `mp3-organizer/src/main/kotlin/com/mp3organizer/ai/MaintenanceAgent.kt` (existe)

**Rôle** : Analyser le code et suggérer des améliorations

**Statut** : ✅ **IMPLÉMENTÉ** dans mp3-organizer

---

### 4. Workflow Complet VLC (Priorité : Haute) ✅

**Architecture** :
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

**Commandes** :
```bash
# Playlist intelligente depuis prompt naturel
./gradlew playSmart --args="jazz piano détente pour coder"

# Playlist par artiste
./gradlew playArtist --args="Miles Davis"

# Playlist par genre
./gradlew playGenre --args="jazz"
```

**Statut** : ✅ **IMPLÉMENTÉ** dans mp3-organizer

---

### 5. Configuration Centralisée (Priorité : Moyenne) ⏳
**Inspiré de** : `plantuml-gradle/plantuml-plugin/src/main/kotlin/plantuml/PlantumlConfig.kt`

**À créer** : `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/NewpipeConfig.kt`

**Structure** :
```kotlin
data class NewpipeConfig(
    val llm: LlmConfig,
    val youtube: YouTubeConfig,
    val output: OutputConfig,
    val vlc: VlcConfig  // NOUVEAU
)

data class LlmConfig(
    val model: String = "gemma4:e4b-it-q4_K_M",
    val baseUrl: String = "http://localhost:11434",
    val temperature: Double = 0.7
)

data class VlcConfig(
    val enabled: Boolean = true,
    val path: String = "vlc",  // Chemin vers l'exécutable VLC
    val random: Boolean = true,
    val minimized: Boolean = true
)
```

**Statut** : ⏳ **À CRÉER**

---

### 6. Version Catalog (Priorité : Moyenne) ✅
**Inspiré de** : `mp3-organizer/gradle/libs.versions.toml`

**À créer** : `newpipe-gradle/gradle/libs.versions.toml`

**Objectif** : Centraliser toutes les versions (langchain4j, kotlin, etc.)

**Statut** : ✅ **IMPLÉMENTÉ** dans mp3-organizer

---

## 🗺️ Roadmap des Sessions OpenCode

### Session 1 : Setup LLM Client ✅
- [x] Créer `LlmClient.kt` dans `mp3-organizer/src/main/kotlin/com/mp3organizer/ai/`
- [x] Ajouter dépendances langchain4j dans `build.gradle.kts`
- [x] Tester la connexion à Ollama avec `gemma4:e4b-it-q4_K_M`
- [x] Valider avec un test simple

**Critères d'acceptation** :
```kotlin
val llm = LlmClient()
val response = llm.chat("Bonjour")
assert(response.isNotEmpty())
```

**Statut** : ✅ **TERMINÉ**

---

### Session 2 : Tâches Gradle IA ✅
- [x] Créer fonction helper `registerJavaExecTask()` dans `build.gradle.kts`
- [x] Implémenter tâche `llmChat` (chat interactif)
- [x] Implémenter tâche `sqlPrompt` (génération SQL)
- [x] Implémenter tâches `playSmart`, `playArtist`, `playGenre`
- [x] Tester avec `./gradlew playSmart --args="jazz"`

**Critères d'acceptation** :
```bash
./gradlew playSmart --args="jazz piano détente"
# Génère un fichier .xspf et lance VLC
```

**Statut** : ✅ **TERMINÉ**

---

### Session 3 : Agent Playlist ✅
- [x] Créer `PlaylistAgent.kt`
- [x] Implémenter `generatePlaylist()` avec LLM + SQL
- [x] Créer `XspfGenerator.kt` pour format VLC
- [x] Intégrer avec PostgreSQL (R2DBC)
- [x] Lancer VLC via ProcessBuilder

**Critères d'acceptation** :
```kotlin
val agent = PlaylistAgent(llmClient, database)
val playlist = agent.generatePlaylist("jazz piano")
assert(playlist.tracks.isNotEmpty())
```

**Statut** : ✅ **TERMINÉ**

---

### Session 4 : Migration Vers NewPipe Plugin ⏳
- [ ] Copier `LlmClient.kt` vers `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/ai/`
- [ ] Copier `PlaylistAgent.kt` → `SessionGeneratorAgent.kt` (adapter pour YouTube)
- [ ] Copier `XspfGenerator.kt` → `PlaylistGenerator.kt` (adapter pour NewPipe)
- [ ] Ajouter dépendances langchain4j dans `newpipe-plugin/build.gradle.kts`
- [ ] Créer tâches Gradle `suggestSessions`, `generatePlaylist`

**Critères d'acceptation** :
```bash
cd /home/cheroliv/workspace/__repositories__/newpipe-gradle
./gradlew suggestSessions --args="jazz piano années 60"
# Génère un fichier session.yml avec filtres YouTube
```

**Statut** : ⏳ **À FAIRE**

---

### Session 5 : Configuration & RAG ⏳
- [ ] Créer `NewpipeConfig.kt` avec support YAML
- [ ] Implémenter `ConfigLoader` et `ConfigMerger` (inspiré plantuml-gradle)
- [ ] Ajouter support properties CLI (`-Pnewpipe.llm.model=...`)
- [ ] Créer index RAG pour le codebase newpipe
- [ ] Ajouter configuration VLC

**Critères d'acceptation** :
```yaml
# newpipe-context.yml
llm:
  model: gemma4:e4b-it-q4_K_M
  baseUrl: http://localhost:11434
vlc:
  enabled: true
  path: /usr/bin/vlc
```

**Statut** : ⏳ **À FAIRE**

---

### Session 6 : Tests & Validation ⏳
- [ ] Tests unitaires avec WireMock (inspiré plantuml-gradle)
- [ ] Tests fonctionnels avec GradleTestKit
- [ ] Tests d'intégration Ollama (tag `real-llm`)
- [ ] Validation syntaxe YAML générée
- [ ] Tests de génération de playlists

**Statut** : ⏳ **À FAIRE**

---

## 📚 Références Techniques

### Architecture Pattern (plantuml-gradle)
```
┌─────────────────┐
│  LlmService     │ → Crée ChatModel selon config
├─────────────────┤
│  DiagramProcessor │ → Utilise ChatModel.chat()
├─────────────────┤
│  PlantumlConfig │ → Charge YAML + properties
└─────────────────┘
```

### API LangChain4j v1.12.2
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

### Structure de Projet Cible
```
newpipe-gradle/
├── build.gradle.kts          # Configuration racine + extension LLM
├── gradle/
│   └── libs.versions.toml    # Version catalog (À CRÉER)
├── newpipe-plugin/
│   ├── settings.gradle.kts
│   └── newpipe/
│       ├── build.gradle.kts  # Dépendances LLM
│       └── src/main/kotlin/
│           └── com/cheroliv/newpipe/
│               ├── ai/       # NOUVEAU : Module IA
│               │   ├── LlmClient.kt            ✅ Dans mp3-organizer
│               │   ├── SessionGeneratorAgent.kt  ⏳ À créer
│               │   ├── PlaylistAgent.kt        ✅ Dans mp3-organizer
│               │   └── MaintenanceAgent.kt     ✅ Dans mp3-organizer
│               ├── playlist/
│               │   └── XspfGenerator.kt        ✅ Dans mp3-organizer
│               ├── NewpipeConfig.kt            ⏳ À créer
│               └── ...       # Code existant
```

### Modèle LLM : Gemma 4 E4B
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

---

## ✅ Definition of Done

Pour chaque session :
- [x] Code implémenté et fonctionnel
- [x] Tests unitaires écrits (min 80% coverage)
- [x] Documentation mise à jour
- [x] Commit git avec message conventionnel
- [x] Validation manuelle des tâches Gradle
- [x] Intégration avec VLC testée

---

## 🚀 Commandes Utiles

### Setup Initial
```bash
# mp3-organizer
cd /home/cheroliv/Musique/abdo/mp3-organizer
ollama pull gemma4:e4b-it-q4_K_M
ollama serve

# newpipe-gradle (futur)
cd /home/cheroliv/workspace/__repositories__/newpipe-gradle
ollama pull gemma4:e4b-it-q4_K_M
ollama serve
```

### Build & Test
```bash
# mp3-organizer
./gradlew clean build
./gradlew test

# newpipe-gradle
./gradlew clean build
./gradlew functionalTest
./gradlew koverHtmlReport
```

### Tâches IA (mp3-organizer - IMPLÉMENTÉ)
```bash
# Chat interactif
./gradlew llmChat --args="quelle est la structure de la DB ?"

# Génération SQL
./gradlew sqlPrompt --args="top 10 artistes par nombre de tracks"

# Playlist intelligente
./gradlew playSmart --args="jazz piano détente pour coder"

# Playlist par artiste
./gradlew playArtist --args="Miles Davis"

# Playlist par genre
./gradlew playGenre --args="jazz"

# Maintenance code
./gradlew maintenance
```

### Tâches IA (newpipe-gradle - À CRÉER)
```bash
# Chat interactif
./gradlew llmChat --args="quelle est l'architecture du plugin ?"

# Génération de sessions YouTube
./gradlew suggestSessions --args="musique électronique française"

# Génération de playlists
./gradlew generatePlaylist --args="jazz années 60"

# Analyse code
./gradlew analyzeCode
```

---

## 📊 Tableau de Suivi

| Fonctionnalité | mp3-organizer | newpipe-plugin | Status |
|----------------|---------------|----------------|--------|
| LLM Client | ✅ | ⏳ | Migration en attente |
| Playlist Agent | ✅ | ⏳ | À adapter pour YouTube |
| XSPF Generator | ✅ | ⏳ | À copier |
| Tâches Gradle | ✅ | ⏳ | À créer |
| Version Catalog | ✅ | ❌ | À créer |
| Config YAML | ❌ | ⏳ | À créer |
| Tests WireMock | ❌ | ⏳ | À créer |
| Intégration VLC | ✅ | ❌ | Non requis pour NewPipe |

---

## 📝 Notes de Session

### Session 1 - 2026-04-14 ✅
**Réalisé** :
- [x] LlmClient.kt créé avec API ChatModel (langchain4j 1.12.2)
- [x] Modèle gemma4:e4b-it-q4_K_M configuré
- [x] Méthodes chat() et chatWithContext() implémentées

**Problèmes rencontrés** :
- [x] Aucun

**Prochaine session** :
- [x] Tâches Gradle IA

---

### Session 2 - 2026-04-14 ✅
**Réalisé** :
- [x] Fonction helper `registerJavaExecTask()` créée
- [x] 4 tâches IA ajoutées (llmChat, sqlPrompt, maintenance, analyzeQuery)
- [x] 3 tâches média ajoutées (playSmart, playArtist, playGenre)

**Problèmes rencontrés** :
- [x] Aucun

**Prochaine session** :
- [x] Agent Playlist

---

### Session 3 - 2026-04-14 ✅
**Réalisé** :
- [x] PlaylistAgent.kt créé avec génération SQL via LLM
- [x] XspfGenerator.kt pour format VLC compatible
- [x] PlaySmartTask.kt avec orchestration complète
- [x] PlayArtistTask.kt et PlayGenreTask.kt
- [x] Extensions Database pour requêtes paramétrées

**Problèmes rencontrés** :
- [x] Aucun

**Prochaine session** :
- [ ] Migration vers newpipe-plugin

---

### Session 4 - [DATE] ⏳
**Réalisé** :
- [ ] ...

**Problèmes rencontrés** :
- [ ] ...

**Prochaine session** :
- [ ] ...

---

## 🔗 Liens Utiles

- **Ollama** : https://ollama.com/library/gemma4
- **LangChain4j** : https://github.com/langchain4j/langchain4j
- **PlantUML Gradle** : `/home/cheroliv/workspace/__repositories__/plantuml-gradle`
- **mp3-organizer** : `/home/cheroliv/Musique/abdo/mp3-organizer`
- **NewPipe Gradle** : `/home/cheroliv/workspace/__repositories__/newpipe-gradle`

---

**Dernière mise à jour** : 2026-04-14  
**Statut** : 🟢 Sessions 1-3 terminées (mp3-organizer) | Sessions 4-6 en attente (newpipe-plugin)
