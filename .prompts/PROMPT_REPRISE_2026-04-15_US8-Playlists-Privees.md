---
date: 2026-04-15
us: US-8
title: Support des Playlists Privées
status: pending
previous_us: US-5
---

# Prompt de Reprise - Session 2026-04-15 (Démarrage US-8)

## 📋 PROCÉDURE DE REPRISE POUR L'AGENT

**IMPORTANT** : À chaque début de session, l'agent DOIT suivre cette procédure :

1. **Archiver le prompt actuel** :
   ```bash
   # Déplacer le PROMPT_REPRISE.md courant vers .prompts/ avec timestamp
   cp PROMPT_REPRISE.md .prompts/PROMPT_REPRISE_YYYY-MM-DD_US<numéro>-<titre>.md
   ```

2. **Lire le nouveau prompt de reprise** :
   ```bash
   # Le fichier PROMPT_REPRISE.md dans le dossier courant est le prompt ACTIF
   # Il contient l'US à traiter en priorité
   ```

3. **Commencer l'US indiquée** :
   - Suivre les critères d'acceptation
   - Créer les fichiers nécessaires
   - Ajouter les tests unitaires
   - Valider par `./gradlew clean build test`

**Règle** : Le fichier `PROMPT_REPRISE.md` à la racine est TOUJOURS le prompt actif.
Les fichiers dans `.prompts/` sont les archives des sessions précédentes.

---

## ✅ US-5 TERMINÉE - Vidéos 18+ avec Vérification d'Âge

**Fichiers créés** :
- `AgeRestrictedVideoException.kt` : Exception avec raisons (AGE_VERIFICATION_REQUIRED, MINOR_ACCOUNT, AGE_GATE_UNCIRCUMVENTABLE, SIGN_IN_REQUIRED)
- `AgeVerificationHandler.kt` : Détection et gestion des erreurs 18+
- `AgeVerificationHandlerTest.kt` : 16 tests unitaires

**Fichiers modifiés** :
- `DownloadMusicTask.kt` : Intégration du handler dans la boucle de téléchargement
- `YouTubeDownloader.kt` : Détection lors de l'extraction des infos vidéo
- `NewpipeManager.kt` : Ajout de `getCurrentSession()` pour l'handling d'erreurs

**Backlog** : 67% complété (22/37 points)

**Build** : ✅ BUILD SUCCESSFUL - Tous les tests passent (27+ tests)

**Dernier commit** : `feat(US-5): Add age-restricted video detection and handling`

---

## 🎯 US-8 : Support des Playlists Privées (À TRAITER)

**Priorité** : 🟡 Basse  
**Points** : 5  
**Statut** : ⏳ À FAIRE

**Objectif** : Permettre le téléchargement des playlists privées YouTube avec authentification

### Contexte

Les playlists privées sur YouTube ne sont accessibles qu'avec le compte qui les a créées. Avec l'authentification OAuth2 déjà implémentée, nous pouvons accéder à ces playlists si la bonne session est utilisée.

**Problèmes à gérer** :
1. Détection des playlists privées (vs publiques)
2. Association playlist → session appropriée
3. Gestion des erreurs d'accès (403 Forbidden)
4. Support des playlists collaboratives
5. Messages clairs pour l'utilisateur

### Fichiers à créer

```
newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/
├── PrivatePlaylistHandler.kt    # Gestion des playlists privées
└── PrivatePlaylistException.kt  # Exception dédiée
```

### Critères d'acceptation

```bash
# 1. Configuration musics.yml avec session explicite
playlists:
  - url: "https://www.youtube.com/playlist?list=PRIVATE_ID"
    session: "compte-principal"

# 2. Commande
./gradlew downloadMusic

# 3. Sortie attendue :
# ✓ Playlist privée détectée
# ✓ Session 'compte-principal' utilisée
# ✓ 15 vidéos téléchargées avec succès
```

### Scénarios de test

```gherkin
Scenario: Playlist privée avec bonne session
  Given une playlist privée sur le compte 'compte-principal'
  And sessions.yml contient 'compte-principal' avec refreshToken
  When je lance ./gradlew downloadMusic
  Then la playlist est téléchargée avec succès
  And toutes les vidéos sont récupérées

Scenario: Playlist privée avec mauvaise session
  Given une playlist privée sur le compte 'compte-A'
  And sessions.yml contient seulement 'compte-B'
  When je lance ./gradlew downloadMusic
  Then un message indique "Playlist not accessible with this account"
  And le téléchargement est skipé

Scenario: Playlist privée sans session
  Given une playlist privée
  And aucune session authentifiée
  When je lance ./gradlew downloadMusic
  Then un message indique "Private playlist requires authentication"
  And le téléchargement est skipé

Scenario: Playlist publique sans session
  Given une playlist publique
  And aucune session authentifiée
  When je lance ./gradlew downloadMusic
  Then la playlist est téléchargée en mode anonyme
```

### Tâches techniques

- [ ] Analyser comment NewPipe Extractor gère les playlists privées
- [ ] Créer `PrivatePlaylistException.kt`
- [ ] Créer `PrivatePlaylistHandler.kt` avec détection et gestion
- [ ] Modifier `Models.kt` pour supporter `session` optionnel dans la config
- [ ] Modifier `DownloadMusicTask.kt` pour intégrer le handler
- [ ] Tests unitaires pour chaque scénario
- [ ] Tests d'intégration avec vraies playlists privées

### Implémentation suggérée

```kotlin
// PrivatePlaylistHandler.kt
class PrivatePlaylistHandler {
    
    fun handlePrivatePlaylistError(
        playlistUrl: String,
        session: Session?,
        error: Throwable
    ): PrivatePlaylistResult {
        return when {
            error.isPrivatePlaylist() && session == null -> 
                PrivatePlaylistResult.UNAUTHENTICATED
            
            error.isPrivatePlaylist() && !session.canAccessPlaylist() -> 
                PrivatePlaylistResult.WRONG_ACCOUNT
            
            error.isPrivatePlaylist() -> 
                PrivatePlaylistResult.SUCCESS
            
            else -> 
                PrivatePlaylistResult.NOT_PRIVATE
        }
    }
    
    fun logPrivatePlaylistResult(result: PrivatePlaylistResult, playlistUrl: String) {
        // Logs structurés avec emoji
    }
}
```

### Ressources

- **NewPipe Extractor** : Voir `PlaylistExtractor` pour détection playlists privées
- **YouTube API** : `privacyStatus` field dans Playlist resource
- **Tests** : Créer une playlist de test privée sur un compte dédié

---

## 📝 État du Code

**Branch** : `main`  
**Build** : ✅ BUILD SUCCESSFUL  
**Tests** : ✅ 27 tests unitaires + tests d'intégration

**Dernier commit** : `feat(US-5): Add age-restricted video detection and handling`

---

## 🚀 Commandes Utiles

```bash
# Build + Tests
./gradlew -p newpipe-plugin clean build test

# Voir l'état des sessions
./gradlew sessionStatus

# Authentifier un compte (prérequis pour US-8)
./gradlew buildSessions
./gradlew authSessions

# Tester avec une playlist privée (après implémentation)
./gradlew downloadMusic
```

---

## 📊 Progression du Backlog

| US | Titre | Statut | Points |
|----|-------|--------|--------|
| US-1 | Authentification Initiale | ✅ FAIT | 3 |
| US-2 | Téléchargement Authentifié | ✅ FAIT | 5 |
| US-3 | Refresh Automatique Tokens | ✅ FAIT | 5 |
| US-4 | Gestion Erreurs Auth | ✅ FAIT | 3 |
| US-5 | Vidéos 18+ | ✅ FAIT | 8 |
| US-6 | Monitoring Sessions | ✅ FAIT | 3 |
| US-7 | Tests Intégration | ✅ FAIT | 5 |
| US-8 | Playlists Privées | ⏳ **EN COURS** | 5 |

**Total** : 22/37 points (59%)  
**Sprint 1** : 22/22 points (100%) ✅  
**Sprint 2** : 0/13 points (0%)

---

## 🔐 Rappel Sécurité

```bash
# NE JAMAIS COMMIT
git update-index --assume-unchanged sessions.yml
git update-index --assume-unchanged client_secrets/*.json
```

---

**Pour commencer US-8** :
1. Archiver ce prompt : `cp PROMPT_REPRISE.md .prompts/PROMPT_REPRISE_2026-04-15_US8-Playlists-Privees.md`
2. Créer une playlist privée de test sur un compte Google
3. Analyser les erreurs NewPipeExtractor pour playlists privées
4. Implémenter `PrivatePlaylistHandler.kt` et `PrivatePlaylistException.kt`
5. Modifier `Models.kt` pour supporter le champ `session` optionnel
6. Ajouter tests unitaires et d'intégration
