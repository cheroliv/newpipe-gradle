# 📁 Structure des Projets - NewPipe Gradle Ecosystem

**Dernière mise à jour** : 2026-04-14

---

## newpipe-gradle (Projet Actuel)

```
/home/cheroliv/workspace/__repositories__/newpipe-gradle/
├── 📄 EPIC_LLM_INTEGRATION.md       # Roadmap détaillée
├── 📄 AGENT.md                      # Contexte IA (fichier principal)
├── 📄 build.gradle.kts              # Build root
├── 📄 settings.gradle.kts
├── 📄 musics.yml                    # Configuration downloads
├── 📄 sessions.yml                  # Sessions YouTube (⚠️ gitignore)
├── 📁 client_secrets/               # ⚠️ Secrets OAuth (⚠️ gitignore)
├── 📁 downloads/                    # ⚠️ Fichiers téléchargés (⚠️ gitignore)
└── 📁 newpipe-plugin/
    ├── 📄 settings.gradle.kts
    └── 📁 newpipe/
        ├── 📄 build.gradle.kts
        └── 📁 src/main/kotlin/com/cheroliv/newpipe/
            ├── 📄 DownloaderPlugin.kt
            ├── 📄 NewpipeManager.kt
            ├── 📄 YouTubeDownloader.kt
            ├── 📄 AuthSessionTask.kt          # 🔴 Auth Google
            ├── 📄 BuildSessionsTask.kt        # 🔴 Auth Google
            ├── 📄 SessionManager.kt           # 🔴 Auth Google
            ├── 📄 DownloadMusicTask.kt
            ├── 📄 Mp3Converter.kt
            ├── 📄 Models.kt
            └── 📁 ai/                         # ⏳ À créer (migration)
                ├── 📄 LlmClient.kt
                ├── 📄 SessionGeneratorAgent.kt
                └── 📄 PlaylistGeneratorAgent.kt
```

**Fichiers de configuration** :
- `build.gradle.kts` : Configuration racine
- `settings.gradle.kts` : Déclare JitPack dans pluginManagement
- `musics.yml` : Sélection musicale à télécharger
- `sessions.yml` : Sessions OAuth2 (⚠️ NE PAS COMMIT)

---

## mp3-organizer (Projet Référence)

```
/home/cheroliv/Musique/abdo/mp3-organizer/
├── 📄 build.gradle.kts              # ✅ Version catalog + tasks
├── 📁 gradle/
│   └── 📄 libs.versions.toml        # ✅ Centralisé
└── 📁 src/main/kotlin/com/mp3organizer/
    ├── 📁 ai/                       # ✅ IMPLÉMENTÉ
    │   ├── 📄 LlmClient.kt
    │   ├── 📄 PlaylistAgent.kt
    │   ├── 📄 SqlGenerationAgent.kt
    │   ├── 📄 MaintenanceAgent.kt
    │   ├── 📄 PlaySmartTask.kt      # ✅ VLC integration
    │   ├── 📄 PlayArtistTask.kt
    │   └── 📄 PlayGenreTask.kt
    ├── 📁 playlist/
    │   └── 📄 XspfGenerator.kt      # ✅ Format VLC
    ├── 📁 database/
    │   ├── 📄 Database.kt           # ✅ PostgreSQL R2DBC
    │   └── 📄 DatabaseExtension.kt
    └── 📄 Models.kt
```

**Fichiers de configuration** :
- `build.gradle.kts` : Version catalog + tâches IA
- `gradle/libs.versions.toml` : Catalogue de versions centralisé
- `.env` : Variables d'environnement (DB credentials)

---

## plantuml-gradle (Référence Architecture)

```
/home/cheroliv/workspace/__repositories__/plantuml-gradle/
├── 📄 build.gradle.kts
├── 📁 gradle/
│   └── 📄 libs.versions.toml
└── 📁 plantuml-plugin/
    ├── 📄 settings.gradle.kts
    └── 📁 src/main/kotlin/plantuml/
        ├── 📄 PlantumlPlugin.kt
        ├── 📄 PlantumlConfig.kt     # ✅ Config YAML + properties
        ├── 📄 ConfigLoader.kt
        ├── 📄 ConfigMerger.kt
        └── 📁 service/
            ├── 📄 LlmService.kt     # ✅ Pattern LLM
            └── 📄 DiagramProcessor.kt  # ✅ ChatModel usage
```

**Patterns à réutiliser** :
- `PlantumlConfig.kt` : Configuration YAML + properties CLI
- `ConfigLoader.kt` : Chargement de configuration
- `ConfigMerger.kt` : Fusion de configurations multiples
- `LlmService.kt` : Abstraction LLM

---

## Architecture Finale Cible

```
newpipe-gradle/
├── build.gradle.kts                 # Configuration racine
├── settings.gradle.kts
├── gradle/
│   └── libs.versions.toml           # Version catalog
└── newpipe-plugin/
    ├── settings.gradle.kts
    └── newpipe/
        ├── build.gradle.kts         # Plugin avec IA embarquée
        └── src/main/kotlin/
            ├── ai/                  # Module IA complet
            │   ├── LlmClient.kt
            │   ├── SessionGeneratorAgent.kt
            │   ├── PlaylistGeneratorAgent.kt
            │   └── MaintenanceAgent.kt
            ├── tasks/
            │   ├── DownloadMusicTask.kt
            │   ├── BuildSessionsTask.kt
            │   ├── GeneratePlaylistTask.kt
            │   └── ChatWithCodebaseTask.kt
            └── config/
                ├── NewpipeConfig.kt
                ├── ConfigLoader.kt
                └── ConfigMerger.kt
```

---

## Emplacements des Fichiers Clés

### Authentification Google
```
newpipe-gradle/
├── newpipe-plugin/newpipe/src/main/kotlin/
│   ├── AuthSessionTask.kt          # Gestion sessions OAuth
│   ├── BuildSessionsTask.kt        # Construction sessions
│   └── SessionManager.kt           # Manager de sessions
├── sessions.yml                     # Configuration des sessions
└── client_secrets/                  # Secrets OAuth2 (⚠️ NE PAS COMMIT)
```

### Migration IA (depuis mp3-organizer)
```
mp3-organizer/                    →  newpipe-gradle/
├── gradle/libs.versions.toml     →  gradle/libs.versions.toml
├── src/main/kotlin/ai/
│   ├── LlmClient.kt              →  newpipe-plugin/.../ai/LlmClient.kt
│   ├── PlaylistAgent.kt          →  newpipe-plugin/.../ai/SessionGeneratorAgent.kt
│   └── PlaySmartTask.kt          →  newpipe-plugin/.../tasks/GeneratePlaylistTask.kt
└── build.gradle.kts              →  newpipe-gradle/build.gradle.kts (extension)
```

---

## Voir Aussi

- **Architecture technique** : `doc/ARCHITECTURE.md`
- **Guide d'authentification** : `doc/AUTH_GUIDE.md`
- **Matrice de migration** : `doc/MIGRATION_MATRIX.md`
- **Démarrage rapide** : `doc/QUICK_START.md`

---

**Dernière mise à jour** : 2026-04-14
