# 🤖 Agent Context - NewPipe Gradle Ecosystem

**Dernière mise à jour** : 2026-04-15 (US-6 terminée)  
**Projets** : `newpipe-gradle`, `newpipe-plugin`, `mp3-organizer`  
**Stack** : Kotlin 2.3.20, Gradle 9.4.1, LangChain4j 1.12.2, Gemma4:e4b  
**Statut Build** : ✅ BUILD SUCCESSFUL

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
- [x] US-1 : Authentification Initiale ✅
- [x] US-2 : Téléchargement Authentifié ✅
- [x] US-3 : Refresh automatique des tokens expirés ✅
- [x] US-4 : Gestion des erreurs d'authentification ✅
- [x] US-6 : Monitoring de l'état des sessions ✅
- [x] US-7 : Tests d'intégration ✅

**Commandes** :
```bash
# Construire les sessions YouTube
./gradlew buildSessions

# Authentifier les sessions
./gradlew authSessions

# Voir l'état des sessions
./gradlew sessionStatus

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

## 📋 BACKLOG - Tâches Prioritaires (Sprint 1)

**Progression** : 2/8 US (25%) | 8/37 points (22%)

### 🔴 À TRAITER MAINTENANT - Sprint 1 (2026-04-14 → 2026-04-21)

**Objectif** : Rendre l'existant fiable et testable

#### US-4 : Gestion des Erreurs d'Authentification (3 points)
**Priorité** : 🟠 Moyenne | **Statut** : ⏳ À FAIRE

**Fichiers à créer** :
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/AuthErrorHandler.kt`

**Critères d'acceptation** :
- Mapper les codes erreur OAuth2 → messages utilisateur en français
- Gérer : token révoqué, token expiré, secret invalide, quota dépassé, compte suspendu
- Logs structurés avec emoji pour lisibilité
- Rapport de fin de téléchargement avec état des sessions

**Scénarios** :
```gherkin
Scenario: Token révoqué → Message "Token révoqué par l'utilisateur"
Scenario: Multiple sessions avec erreurs → Seules sessions valides utilisées
Scenario: Toutes sessions invalides → Fallback anonyme + message clair
```

#### US-7 : Tests d'Intégration avec Vrais Comptes (5 points)
**Priorité** : 🟠 Moyenne | **Statut** : ⏳ À FAIRE

**Fichiers à créer** :
- `newpipe-plugin/newpipe/src/functionalTest/kotlin/com/cheroliv/newpipe/YouTubeAuthIntegrationTest.kt`

**Critères d'acceptation** :
- Tests taggués `@Tag("real-youtube")` pour éviter exécution CI par défaut
- Variables d'environnement : `TEST_YOUTUBE_CLIENT_ID`, `TEST_YOUTUBE_CLIENT_SECRET`, `TEST_YOUTUBE_REFRESH_TOKEN`
- 3 tests minimum : download member-only video, refresh expired token, fallback anonymous

**Commande** :
```bash
./gradlew test --tests "*YouTubeAuthIntegrationTest*" --include-tags "real-youtube"
```

---

### 🟡 BACKLOG COMPLÉMENTAIRE (Sprints 2-3)

**Backlog complet** : `BACKLOG_BACKUP.md` (37 points total, 29 points restants)

#### Sprint 2 : Amélioration UX (8 points)
- US-3 : Refresh Automatique Tokens (5 points)
- US-6 : Monitoring Sessions (3 points)

#### Sprint 3 : Fonctionnalités Avancées (13 points)
- US-5 : Vidéos 18+ (8 points)
- US-8 : Playlists Privées (5 points)

---

## 📝 Procédure de Session OpenCode

### ⚠️ RÈGLE D'OR : GIT INTERDICTIONS

**OpenCode NE DOIT JAMAIS** :
- ❌ `git commit` - Interdit
- ❌ `git push` - Interdit
- ❌ `git rollback` - Interdit
- ❌ `git revert` - Interdit

**Seul l'utilisateur peut** gérer Git manuellement après validation.

---

## ⚠️ RÈGLE CRITIQUE : SECRETS ET TOKENS — INTERDICTION DE LOG

**PRINCIPE** : Les tokens API, mots de passe, et identifiants NE DOIVENT JAMAIS apparaître dans la sortie terminal de l'agent. L'utilisateur peut streamer son terminal (Discord, Twitch, etc.). Tout token visible dans le terminal est compromis.

**INTERDICTIONS** :
- ❌ **NE JAMAIS** passer un token en argument visible d'une commande (ex: `GH_TOKEN=xxx gh run list` affiche le token dans l'historique shell)
- ❌ **NE JAMAIS** afficher le contenu d'un fichier contenant des tokens (site.yml, .env, credentials, etc.) si le token peut être visible dans la sortie
- ❌ **NE JAMAIS** utiliser `echo`, `cat`, ou toute commande qui affiche des tokens dans le terminal

**Procédure OBLIGATOIRE pour utiliser des tokens** :
1. Utiliser `gh auth login --with-token` avec le token via **stdin** (pipe ou redirect), jamais en argument visible
2. Utiliser des variables d'environnement dans un sous-shell : `(< token gh auth login --with-token)` plutôt que `GH_TOKEN=token gh ...`
3. Vérifier que `gh auth status` fonctionne après configuration
4. Ne **jamais** lire puis afficher le contenu d'un fichier de credentials (site.yml, .env, etc.)

**Leçon de la Session 008 (magic_stick)** : L'agent a affiché le contenu de `site.yml` contenant un token GitHub dans le terminal, puis a utilisé `GH_TOKEN=xxx gh run list` qui affiche le token dans l'historique shell. L'utilisateur streamait son terminal sur Discord. Token compromis en 1 seconde.

---

### Avant de Commencer - PROCÉDURE OBLIGATOIRE

**⚠️ PREMIÈRE ACTION OBLIGATOIRE** :
```bash
# LIRE CE FICHIER EN PREMIER - Contient le contexte de la session
cat PROMPT_REPRISE.md
```

**Ce fichier contient** :
- ✅ US précédente terminée (statut, fichiers créés/modifiés)
- ✅ État du code (build, tests, couverture)
- ✅ Prochaines US recommandées avec priorités
- ✅ Commandes utiles pour démarrer

**Emplacement** : `PROMPT_REPRISE.md` à la racine du projet (NEWPIPE-GRADLE/)

**Puis** :
- [ ] Vérifier Ollama : `ollama list | grep gemma4`
- [ ] Vérifier secrets OAuth : `ls client_secrets/`
- [ ] Pull le modèle si besoin : `ollama pull gemma4:e4b-it-q4_K_M`
- [ ] Consulter `BACKLOG.md` pour vue d'ensemble des US

### Pendant la Session
- [ ] Noter les commandes exécutées
- [ ] Capturer les erreurs importantes
- [ ] Mettre à jour la documentation
- [ ] Mettre à jour `BACKLOG.md` (statut US, points, progression)

### Après la Session - PROCÉDURE DE CLÔTURE

**1. Créer le prompt d'archive** :
```bash
# Nomenclature : PROMPT_REPRISE_YYYY-MM-DD_USX-Titre.md
cp .prompts/PROMPT_REPRISE_COURANT.md \
   .prompts/PROMPT_REPRISE_$(date +%Y-%m-%d)_US${US_NUM}-$(echo $US_TITLE | tr ' ' '-').md
```

**2. Ajouter les méta-données YAML** (en tête du fichier) :
```yaml
---
date: YYYY-MM-DD
us: US-X
title: Titre de l'US
status: completed|in_progress|cancelled
next_us: US-Y, US-Z
---
```

**3. Écrire le NOUVEAU prompt de reprise** (PROMPT_REPRISE.md à la racine) :
- **D'abord, archiver le prompt actuel** dans `.prompts/` suivant la nomenclature :
  ```bash
  cp PROMPT_REPRISE.md .prompts/PROMPT_REPRISE_$(date +%Y-%m-%d)_US${US_NUM}-$(echo $US_TITLE | tr ' ' '-').md
  ```
- **Puis, écrire un NOUVEAU fichier `PROMPT_REPRISE.md`** avec :
  - La prochaine US à traiter en priorité (ou US recommandée)
  - Contexte : fichiers créés/modifiés, tests, build, commandes utiles
  - Ce fichier servira de point de départ pour la session suivante

**4. Mettre à jour la documentation** :
- `BACKLOG.md` : Statut US, points, progression
- `AGENT.md` : Dernière mise à jour en bas de page

**5. Récapitulatif pour l'utilisateur** :
- Fichiers modifiés
- Tests créés/passés
- Backlog mis à jour
- Prochaines options (US recommandée en priorité)

---

### 📁 Structure des Fichiers de Reprise

```
newpipe-gradle/
├── 📄 PROMPT_REPRISE.md                   # 🎯 FICHIER DE REPRISE (racine)
└── .prompts/
    ├── PROMPT_REPRISE_2026-04-15_US4-Completion.md
    ├── PROMPT_REPRISE_2026-04-15_US7-Tests-Integration.md
    └── ...                                # Archives historiques
```

**Différence claire** :
- `PROMPT_REPRISE.md` (racine) = Fichier de travail **ACTUEL** pour reprendre
- `.prompts/PROMPT_REPRISE_YYYY-MM-DD_USX-*.md` = **ARCHIVES** historiques

---

**Workflow de Session** :

**1. DÉBUT DE SESSION** - Reprendre le contexte
```bash
# OpenCode DOIT lire ce fichier en premier
cat PROMPT_REPRISE.md
```
Ce fichier contient :
- ✅ US précédente terminée
- ✅ État du code (build, tests)
- ✅ Prochaines US recommandées
- ✅ Commandes utiles

**2. PENDANT LA SESSION** - Travailler sur l'US
- Lire le backlog : `BACKLOG.md`
- Consulter la doc spécialisée si besoin
- Mettre à jour la documentation au fur et à mesure

**3. FIN DE SESSION** - Archiver et préparer la suivante

**Étape 3a : Archiver le prompt actuel**
```bash
# Nomenclature : PROMPT_REPRISE_YYYY-MM-DD_USX-Titre.md
cp PROMPT_REPRISE.md \
   .prompts/PROMPT_REPRISE_$(date +%Y-%m-%d)_US${US_NUM}-$(echo $US_TITLE | tr ' ' '-').md

# Exemple : .prompts/PROMPT_REPRISE_2026-04-15_US7-Tests-Integration.md
```

**Étape 3b : Ajouter les méta-données YAML** (en tête du fichier archivé)
```yaml
---
date: YYYY-MM-DD
us: US-X
title: Titre de l'US
status: completed|in_progress|cancelled
next_us: US-Y, US-Z
---
```

**Étape 3c : Mettre à jour PROMPT_REPRISE.md**
- Mettre à jour avec le contexte de la NOUVELLE session
- Indiquer les prochaines US à traiter
- Ce fichier servira de point de départ pour la session suivante

**Étape 3d : Mettre à jour la documentation**
- `BACKLOG.md` : Statut US, points, progression
- `AGENT.md` : Dernière mise à jour en bas de page

**Règle d'or** : 
- `PROMPT_REPRISE.md` = **TOUJOURS** le fichier de reprise (à la racine)
- `.prompts/PROMPT_REPRISE_*.md` = **ARCHIVES** historiques (backup)

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

**Dernière mise à jour** : 2026-04-15 (Procédure de session + archive prompts)  
**Maintenu par** : OpenCode Agent + Cheroliv
