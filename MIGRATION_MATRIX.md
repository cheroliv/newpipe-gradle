# 📊 Matrice de Migration - MP3 Organizer vers NewPipe Gradle

**Dernière mise à jour** : 2026-04-14  
**Statut** : 🟡 En cours

---

## Vue d'Ensemble

Cette matrice documente la migration des composants de `mp3-organizer` vers `newpipe-gradle`.

**Objectif** : Unifier l'infrastructure IA dans le plugin NewPipe pour :
- Générer automatiquement des configurations de sessions YouTube
- Suggérer des playlists basées sur des descriptions en langage naturel
- Assister la maintenance du code et des dépendances

---

## Matrice de Migration

| Composant | mp3-organizer | newpipe-gradle | newpipe-plugin | Status | Priorité |
|-----------|---------------|----------------|----------------|--------|----------|
| **LlmClient** | ✅ | ⏳ | ⏳ | À migrer | 🔴 Haute |
| **PlaylistAgent** | ✅ | ❌ | ⏳ | À adapter (YouTube) | 🔴 Haute |
| **SqlGenerationAgent** | ✅ | ❌ | ❌ | Non requis | ⚪ N/A |
| **MaintenanceAgent** | ✅ | ❌ | ⏳ | À migrer | 🟡 Moyenne |
| **XspfGenerator** | ✅ | ❌ | ❌ | Optionnel | ⚪ N/A |
| **PlaySmartTask** | ✅ | ❌ | ⏳ | À adapter | 🟡 Moyenne |
| **PlayArtistTask** | ✅ | ❌ | ❌ | Non requis | ⚪ N/A |
| **PlayGenreTask** | ✅ | ❌ | ❌ | Non requis | ⚪ N/A |
| **PostgreSQL** | ✅ | ❌ | ❌ | Non requis | ⚪ N/A |
| **R2DBC** | ✅ | ❌ | ❌ | Non requis | ⚪ N/A |
| **VLC Integration** | ✅ | ❌ | ❌ | Non requis | ⚪ N/A |
| **Version Catalog** | ✅ | ❌ | ⏳ | À créer | 🔴 Haute |
| **Config YAML** | ❌ | ⏳ | ⏳ | À créer | 🟡 Moyenne |
| **Auth Google** | ❌ | 🔴 | ⏳ | PRIORITÉ 1 | 🔴 Haute |
| **NewPipeExtractor** | ❌ | ✅ | ✅ | Déjà implémenté | ✅ FAIT |

**Légende** :
- ✅ : Implémenté et fonctionnel
- ⏳ : En cours de migration
- ❌ : Non requis pour newpipe-gradle
- 🔴 : Priorité haute

---

## Détail des Migrations

### 1. LlmClient

**Source** : `mp3-organizer/src/main/kotlin/com/mp3organizer/ai/LlmClient.kt`  
**Destination** : `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/ai/LlmClient.kt`  
**Statut** : ⏳ À migrer

**Adaptations requises** :
- Changer le package : `com.mp3organizer.ai` → `com.cheroliv.newpipe.ai`
- Aucun changement fonctionnel requis

**Dépendances** :
```kotlin
implementation("dev.langchain4j:langchain4j:1.12.2")
implementation("dev.langchain4j:langchain4j-ollama:1.12.2")
```

---

### 2. PlaylistAgent → SessionGeneratorAgent

**Source** : `mp3-organizer/src/main/kotlin/com/mp3organizer/ai/PlaylistAgent.kt`  
**Destination** : `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/ai/SessionGeneratorAgent.kt`  
**Statut** : ⏳ À adapter

**Adaptations requises** :
- Remplacer PostgreSQL → NewPipeExtractor
- Remplacer SQL → Configuration YAML YouTube
- Changer le contexte DB → Schéma YouTube (filtres, genres, artistes)

**Exemple de transformation** :
```kotlin
// mp3-organizer : SQL
val sql = llm.chat("SELECT * FROM tracks WHERE genre = 'jazz'")

// newpipe-gradle : YAML
val config = llm.chat("""
    filters:
      genre: jazz
      decade: 1960s
      instrument: piano
""")
```

---

### 3. PlaySmartTask → GeneratePlaylistTask

**Source** : `mp3-organizer/src/main/kotlin/com/mp3organizer/tasks/PlaySmartTask.kt`  
**Destination** : `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/tasks/GeneratePlaylistTask.kt`  
**Statut** : ⏳ À créer

**Adaptations requises** :
- Remplacer DB query → YouTube search via NewPipeExtractor
- Remplacer XSPF generation → Playlist YouTube (ou fichier local)
- Supprimer VLC integration (non requis)

**Nouvelle tâche Gradle** :
```bash
./gradlew generatePlaylist --args="jazz piano années 60"
```

---

### 4. Version Catalog

**Source** : `mp3-organizer/gradle/libs.versions.toml`  
**Destination** : `newpipe-gradle/gradle/libs.versions.toml`  
**Statut** : ⏳ À créer

**Contenu attendu** :
```toml
[versions]
kotlin = "2.3.20"
gradle = "9.4.1"
langchain4j = "1.12.2"
newpipe-extractor = "0.24.0"

[libraries]
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
langchain4j-core = { module = "dev.langchain4j:langchain4j", version.ref = "langchain4j" }
langchain4j-ollama = { module = "dev.langchain4j:langchain4j-ollama", version.ref = "langchain4j" }
newpipe-extractor = { module = "com.github.TeamNewPipe:NewPipeExtractor", version.ref = "newpipe-extractor" }
```

---

### 5. Config YAML

**Inspiration** : `plantuml-gradle/plantuml-plugin/src/main/kotlin/plantuml/PlantumlConfig.kt`  
**Destination** : `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/config/NewpipeConfig.kt`  
**Statut** : ⏳ À créer

**Structure attendue** :
```kotlin
data class NewpipeConfig(
    val llm: LlmConfig,
    val youtube: YouTubeConfig,
    val output: OutputConfig
)

data class LlmConfig(
    val model: String = "gemma4:e4b-it-q4_K_M",
    val baseUrl: String = "http://localhost:11434",
    val temperature: Double = 0.7
)

data class YouTubeConfig(
    val sessionsPath: String = "sessions.yml",
    val clientSecretsDir: String = "client_secrets"
)
```

---

## Composants Non Requis

### SQL Generation Agent
**Raison** : NewPipe n'utilise pas de base de données, mais l'API YouTube

### XSPF Generator
**Raison** : Format VLC non requis pour NewPipe (téléchargement MP3 direct)

### VLC Integration
**Raison** : NewPipe se concentre sur le téléchargement, pas la lecture

### PostgreSQL / R2DBC
**Raison** : Aucune base de données dans newpipe-gradle

---

## Roadmap de Migration

### Phase 1 : Foundation (Sprint 1)
- [ ] Créer `gradle/libs.versions.toml`
- [ ] Migrer `LlmClient.kt`
- [ ] Ajouter dépendances langchain4j
- [ ] Tests unitaires LlmClient

### Phase 2 : Agents (Sprint 2)
- [ ] Créer `SessionGeneratorAgent.kt` (adapté de PlaylistAgent)
- [ ] Créer contexte YouTube pour LLM
- [ ] Tests de génération de configuration

### Phase 3 : Tâches Gradle (Sprint 3)
- [ ] Créer `GeneratePlaylistTask.kt`
- [ ] Intégrer avec NewPipeExtractor
- [ ] Tests fonctionnels

### Phase 4 : Configuration (Sprint 4)
- [ ] Créer `NewpipeConfig.kt`
- [ ] Support YAML + properties CLI
- [ ] Documentation

---

## Commandes de Test

### Avant Migration (mp3-organizer)
```bash
cd /home/cheroliv/Musique/abdo/mp3-organizer

# Playlist intelligente
./gradlew playSmart --args="jazz piano détente"

# Chat interactif
./gradlew llmChat --args="quelle est la structure de la DB ?"
```

### Après Migration (newpipe-gradle)
```bash
cd /home/cheroliv/workspace/__repositories__/newpipe-gradle

# Générer configuration YouTube
./gradlew suggestSessions --args="jazz piano années 60"

# Générer playlist
./gradlew generatePlaylist --args="électro française"

# Chat interactif
./gradlew llmChat --args="quelle est l'architecture du plugin ?"
```

---

## Critères d'Acceptation

### LlmClient
```kotlin
val llm = LlmClient()
val response = llm.chat("Bonjour")
assert(response.isNotEmpty())
```

### SessionGeneratorAgent
```kotlin
val agent = SessionGeneratorAgent(LlmClient())
val config = agent.generateConfig("jazz piano années 60")
assert(config.filters.genre == "jazz")
assert(config.filters.decade == "1960s")
```

### GeneratePlaylistTask
```bash
./gradlew generatePlaylist --args="jazz"
# Doit générer un fichier playlist.yml avec vidéos YouTube
```

---

## Voir Aussi

- **Architecture technique** : `doc/ARCHITECTURE.md`
- **Structure des projets** : `doc/PROJECT_STRUCTURE.md`
- **EPIC LLM Integration** : `EPIC_LLM_INTEGRATION.md`
- **EPIC Download Authenticated** : `doc/EPIC_DOWNLOAD_AUTHENTICATED.md`

---

**Dernière mise à jour** : 2026-04-14
