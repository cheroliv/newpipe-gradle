# 🤖 Agent Context - NewPipe Gradle Ecosystem

**Dernière mise à jour** : 2026-04-14  
**Projets** : `newpipe-gradle`, `newpipe-plugin`, `mp3-organizer`  
**Stack** : Kotlin 2.3.20, Gradle 9.4.1, LangChain4j 1.12.2, Gemma4:e4b

---

## 📋 Vue d'Ensemble

Tu es l'assistant IA pour un écosystème de plugins Gradle centrés sur :
1. **NewPipe Plugin** : Téléchargement audio/vidéo YouTube avec authentification Google
2. **MP3 Organizer** : Organisation de collection musicale avec IA (projet référence)

**Architecture cible** : Unifier les deux dans newpipe-gradle → newpipe-plugin

---

## 🎯 Priorités Actuelles

### 🔴 PRIORITÉ 1 : Authentification Google (NewPipe Plugin)

**Objectif** : Configurer l'authentification OAuth2 pour l'API YouTube

**Contexte** :
- NewPipeExtractor nécessite une authentification Google pour certaines fonctionnalités
- Besoin d'un système de sessions YouTube (cookies OAuth2)

**Fichiers clés** :
```
newpipe-gradle/
├── newpipe-plugin/newpipe/src/main/kotlin/
│   ├── AuthSessionTask.kt          # Gestion sessions OAuth
│   ├── BuildSessionsTask.kt        # Construction sessions
│   └── SessionManager.kt           # Manager de sessions
├── sessions.yml                     # Configuration des sessions
└── client_secrets/                  # Secrets OAuth2 (⚠️ NE PAS COMMIT)
```

**Tâches** :
- [x] Configurer OAuth2 Google Cloud Console
- [x] Implémenter flux d'authentification (AuthSessionTask.kt)
- [x] Stocker sessions de manière sécurisée
- [ ] Refresh automatique des tokens expirés (US-3)
- [ ] Gestion des erreurs d'authentification (US-4)

**Commandes** :
```bash
# Construire les sessions YouTube
./gradlew buildSessions

# Authentifier les sessions
./gradlew authSessions

# Télécharger avec session authentifiée
./gradlew downloadMusic --url="VIDEO_ID"
```

**📚 Documentation** :
- **Guide complet** : `AUTH_GUIDE.md`
- **User-stories** : `EPIC_DOWNLOAD_AUTHENTICATED.md`
- **Architecture** : `ARCHITECTURE.md`

---

### 🟡 PRIORITÉ 2 : Migration MP3 Organizer → NewPipe Gradle

**Objectif** : Migrer l'infrastructure IA de mp3-organizer vers newpipe-gradle

**Ce qui doit être migré** :
```
mp3-organizer/                    →  newpipe-gradle/
├── gradle/libs.versions.toml     →  gradle/libs.versions.toml
├── src/main/kotlin/ai/
│   ├── LlmClient.kt              →  newpipe-plugin/.../ai/LlmClient.kt
│   ├── PlaylistAgent.kt          →  newpipe-plugin/.../ai/SessionGeneratorAgent.kt
│   └── PlaySmartTask.kt          →  newpipe-plugin/.../tasks/GeneratePlaylistTask.kt
└── build.gradle.kts              →  newpipe-gradle/build.gradle.kts (extension)
```

**Adaptations nécessaires** :
- `PlaylistAgent` → `SessionGeneratorAgent` (YouTube au lieu de DB locale)
- `XspfGenerator` → `YoutubePlaylistGenerator` (format YouTube)
- `PostgreSQL` → `NewPipeExtractor` (API YouTube au lieu de DB)

**📚 Documentation** :
- **Matrice de migration** : `MIGRATION_MATRIX.md`
- **EPIC détaillé** : `EPIC_LLM_INTEGRATION.md`

---

### 🟢 PRIORITÉ 3 : Consolidation dans NewPipe Plugin

**Objectif** : Déplacer toute la logique IA dans le plugin déployé

**Architecture finale** :
```
newpipe-gradle/
├── build.gradle.kts              # Configuration racine
├── settings.gradle.kts
└── newpipe-plugin/
    ├── settings.gradle.kts
    └── newpipe/
        ├── build.gradle.kts      # Plugin avec IA embarquée
        └── src/main/kotlin/
            ├── ai/               # Module IA complet
            │   ├── LlmClient.kt
            │   ├── SessionGeneratorAgent.kt
            │   └── MaintenanceAgent.kt
            ├── tasks/
            │   ├── DownloadMusicTask.kt
            │   └── GeneratePlaylistTask.kt
            └── config/
                ├── NewpipeConfig.kt
                └── ConfigLoader.kt
```

**📚 Documentation** :
- **Structure détaillée** : `PROJECT_STRUCTURE.md`

---

## 🏗️ Architecture Technique

### Stack Complète

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

Docker:
  - ffmpeg (conversion audio)
```

**📚 Documentation** :
- **Architecture détaillée** : `ARCHITECTURE.md`
- **Patterns** : Voir section "Patterns" dans `ARCHITECTURE.md`

---

## 📁 Structure des Projets

### newpipe-gradle (Projet Actuel)
```
/home/cheroliv/workspace/__repositories__/newpipe-gradle/
├── 📄 AGENT.md                      # Ce fichier (contexte IA)
├── 📄 EPIC_LLM_INTEGRATION.md       # Roadmap LLM
├── 📄 EPIC_DOWNLOAD_AUTHENTICATED.md # User-stories Auth
├── 📄 ARCHITECTURE.md               # Architecture technique
├── 📄 PROJECT_STRUCTURE.md          # Structure détaillée
├── 📄 AUTH_GUIDE.md                 # Guide authentification
├── 📄 TESTING_STRATEGY.md           # Stratégie de tests
├── 📄 MIGRATION_MATRIX.md           # Matrice de migration
├── 📄 COMMANDS.md                   # Commandes principales
├── 📄 build.gradle.kts              # Build root
├── 📄 settings.gradle.kts
├── 📄 musics.yml                    # Configuration downloads
├── 📄 sessions.yml                  # Sessions YouTube (⚠️ gitignore)
├── 📁 client_secrets/               # ⚠️ Secrets OAuth (⚠️ gitignore)
└── 📁 newpipe-plugin/
    └── 📁 newpipe/
        ├── 📄 build.gradle.kts
        └── 📁 src/main/kotlin/com/cheroliv/newpipe/
            ├── 📄 AuthSessionTask.kt          # 🔴 Auth Google
            ├── 📄 SessionManager.kt           # 🔴 Auth Google
            ├── 📄 DownloadMusicTask.kt
            └── 📄 Models.kt
```

### mp3-organizer (Projet Référence)
```
/home/cheroliv/Musique/abdo/mp3-organizer/
├── 📁 src/main/kotlin/com/mp3organizer/
│   ├── 📁 ai/                       # ✅ IMPLÉMENTÉ
│   │   ├── LlmClient.kt
│   │   ├── PlaylistAgent.kt
│   │   └── PlaySmartTask.kt
│   └── 📁 playlist/
│       └── XspfGenerator.kt
└── 📁 gradle/
    └── libs.versions.toml           # ✅ Version catalog
```

**📚 Documentation** :
- **Structure complète** : `PROJECT_STRUCTURE.md`

---

## 🚀 Commandes Principales

### Authentification Google
```bash
cd /home/cheroliv/workspace/__repositories__/newpipe-gradle

# 1. Construire les sessions YouTube
./gradlew buildSessions

# 2. Authentifier les sessions
./gradlew authSessions

# 3. Télécharger avec session
./gradlew downloadMusic --url="VIDEO_ID"
```

### Tâches IA (mp3-organizer - Référence)
```bash
cd /home/cheroliv/Musique/abdo/mp3-organizer

# Chat interactif
./gradlew llmChat --args="quelle est la structure de la DB ?"

# Playlist intelligente + VLC
./gradlew playSmart --args="jazz piano détente"

# Playlist par artiste
./gradlew playArtist --args="Miles Davis"
```

### Build & Test
```bash
# newpipe-gradle
./gradlew clean build
./gradlew functionalTest
./gradlew publishToMavenLocal

# mp3-organizer
./gradlew clean build
./gradlew test
```

**📚 Documentation** :
- **Toutes les commandes** : `COMMANDS.md`

---

## 🔐 Authentification Google - Résumé

### Setup Google Cloud Console
1. Aller sur https://console.cloud.google.com/
2. Créer un nouveau projet
3. Activer YouTube Data API v3
4. Créer credentials OAuth2 : Desktop app
5. Télécharger `client_secrets.json`
6. Placer dans `client_secrets/`

### Flux d'Authentification
```
1. buildSessions → Génère sessions.yml
   ↓
2. authSessions → Device Flow OAuth2
   ↓
3. sessions.yml mis à jour avec refreshToken
   ↓
4. downloadMusic → Utilise sessions authentifiées
```

### ⚠️ Sécurité
```bash
# NE JAMAIS COMMIT
git update-index --assume-unchanged client_secrets/client_secrets.json
```

**📚 Documentation** :
- **Guide complet** : `AUTH_GUIDE.md`
- **User-stories** : `EPIC_DOWNLOAD_AUTHENTICATED.md`

---

## 🧪 Testing Strategy

### Tests Unitaires
- WireMock pour mock Ollama
- MockK pour les dépendances

### Tests Fonctionnels
- Gradle TestKit pour les tâches

### Tests d'Intégration
- Tag `real-youtube` pour tests avec vrais comptes
- Variables d'environnement pour credentials

**📚 Documentation** :
- **Stratégie complète** : `TESTING_STRATEGY.md`
- **Patterns de test** : Voir `TESTING_STRATEGY.md`

---

## 📊 Matrice de Migration

| Composant | mp3-organizer | newpipe-gradle | Status |
|-----------|---------------|----------------|--------|
| **LlmClient** | ✅ | ⏳ | À migrer |
| **PlaylistAgent** | ✅ | ⏳ | À adapter (YouTube) |
| **Version Catalog** | ✅ | ❌ | À créer |
| **Auth Google** | ❌ | 🔴 | PRIORITÉ 1 |
| **NewPipeExtractor** | ❌ | ✅ | Déjà implémenté |

**📚 Documentation** :
- **Matrice complète** : `MIGRATION_MATRIX.md`

---

## 🔗 Liens Utiles

### Documentation Officielle
- **Ollama** : https://ollama.com/library/gemma4
- **LangChain4j** : https://github.com/langchain4j/langchain4j
- **NewPipeExtractor** : https://github.com/TeamNewPipe/NewPipeExtractor
- **Google OAuth2** : https://developers.google.com/identity/protocols/oauth2

### Projets Locaux
- **newpipe-gradle** : `/home/cheroliv/workspace/__repositories__/newpipe-gradle`
- **mp3-organizer** : `/home/cheroliv/Musique/abdo/mp3-organizer`
- **plantuml-gradle** : `/home/cheroliv/workspace/__repositories__/plantuml-gradle`

---

## 📝 Checklist Session OpenCode

### Avant de Commencer
- [ ] Vérifier Ollama : `ollama list | grep gemma4`
- [ ] Vérifier secrets OAuth : `ls client_secrets/`
- [ ] Pull le modèle si besoin : `ollama pull gemma4:e4b-it-q4_K_M`

### Pendant la Session
- [ ] Noter les commandes exécutées
- [ ] Capturer les erreurs importantes
- [ ] Mettre à jour la documentation
- [ ] Commiter avec message conventionnel

### Après la Session
- [ ] Tester manuellement les nouvelles tâches
- [ ] Vérifier coverage tests
- [ ] Push vers remote

---

## 💡 Conseil

Quand tu hésites, consulte la documentation spécialisée :
1. **Authentification** : `AUTH_GUIDE.md`
2. **Architecture** : `ARCHITECTURE.md`
3. **Structure** : `PROJECT_STRUCTURE.md`
4. **Commandes** : `COMMANDS.md`
5. **Tests** : `TESTING_STRATEGY.md`
6. **Migration** : `MIGRATION_MATRIX.md`

---

**Dernière mise à jour** : 2026-04-14  
**Maintenu par** : OpenCode Agent + Cheroliv
