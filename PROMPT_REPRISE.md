---
date: 2026-04-15
us: US-5
title: Vidéos 18+ avec Vérification d'Âge
status: in_progress
next_us: US-8
---

# Prompt de Reprise - Session 2026-04-15 (Démarrage US-5)

## ✅ US-6 TERMINÉE - Monitoring de l'État des Sessions

**Fichiers créés** :
- `SessionMonitor.kt` : Tracking des sessions en mémoire
- `SessionStatusTask.kt` : Tâche Gradle avec affichage tableau

**Fichiers modifiés** :
- `NewpipeManager.kt` : Ajout de `sessionStatus` dans `configure()`
- `Models.kt` : Ajout de `@JsonIgnoreProperties` sur `SessionCredentials`
- `DownloaderPlugin.kt` : Appel de `NewpipeManager.configure()`

**Commande validée** :
```bash
./gradlew sessionStatus
```

**Backlog** : 59% complété (22/37 points)

---

## 🎯 US-5 : Vidéos 18+ avec Vérification d'Âge (À TRAITER)

**Priorité** : 🟡 Basse  
**Points** : 8  
**Statut** : ⏳ À FAIRE

**Objectif** : Support des vidéos avec restriction d'âge

### Contexte

YouTube bloque certaines vidéos pour les utilisateurs non authentifiés ou mineurs. Avec un compte Google majeur authentifié, ces vidéos deviennent accessibles.

**Problèmes à gérer** :
1. Détection des vidéos age-restricted
2. Vérification que le compte est majeur (date de naissance >= 18 ans)
3. Gestion des erreurs spécifiques (age-gate, contenu sensible)
4. Messages clairs pour l'utilisateur

### Fichiers à créer

```
newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/
├── AgeVerificationHandler.kt    # Gestion des erreurs age-restricted
└── AgeRestrictedVideoException.kt # Exception dédiée
```

### Critères d'acceptation

```bash
# 1. Détection automatique
./gradlew downloadMusic --url="https://youtube.com/watch?v=age-restricted"

# Sortie attendue :
# ✓ Vidéo age-restricted détectée
# ✓ Session authentifiée utilisée
# ✓ Téléchargement réussi

# 2. Compte mineur ou non authentifié
# ⚠️  Cette vidéo nécessite une vérification d'âge
#    → Authentifiez un compte Google majeur avec ./gradlew authSessions
#    → Ou utilisez le mode anonyme si disponible

# 3. Erreur age-gate non contournable
# ❌  Impossible d'accéder à cette vidéo (restriction géographique + âge)
#    → Essayez avec un autre compte ou un VPN
```

### Scénarios de test

```gherkin
Scenario: Vidéo 18+ avec compte authentifié majeur
  Given une session avec un compte Google majeur
  And une vidéo age-restricted sur YouTube
  When je lance ./gradlew downloadMusic --url="VIDEO_ID"
  Then la vidéo est téléchargée avec succès
  And aucun message d'erreur n'est affiché

Scenario: Vidéo 18+ sans authentification
  Given aucune session authentifiée
  And une vidéo age-restricted sur YouTube
  When je lance ./gradlew downloadMusic --url="VIDEO_ID"
  Then un message indique "Vérification d'âge requise"
  And un lien vers ./gradlew authSessions est affiché

Scenario: Vidéo 18+ avec compte mineur
  Given une session avec un compte Google mineur (< 18 ans)
  And une vidéo age-restricted sur YouTube
  When je lance ./gradlew downloadMusic --url="VIDEO_ID"
  Then un message indique "Compte mineur - Accès refusé"
  And la session est marquée inappropriée pour ce contenu
```

### Tâches techniques

- [ ] Analyser les erreurs NewPipeExtractor pour age-restricted
- [ ] Créer `AgeVerificationHandler.kt` avec détection des erreurs
- [ ] Ajouter des tests unitaires pour chaque scénario
- [ ] Modifier `DownloadMusicTask.kt` pour intégrer le handler
- [ ] Tests d'intégration avec vraies vidéos 18+

### Implémentation suggérée

```kotlin
// AgeVerificationHandler.kt
class AgeVerificationHandler {
    
    fun handleAgeRestrictedError(
        videoUrl: String, 
        session: Session?, 
        error: Throwable
    ): AgeVerificationResult {
        return when {
            error.isAgeRestricted() && session == null -> 
                AgeVerificationResult.UNAUTHENTICATED
            
            error.isAgeRestricted() && !session.isAccountAdult() -> 
                AgeVerificationResult.MINOR_ACCOUNT
            
            error.isAgeRestricted() -> 
                AgeVerificationResult.SUCCESS
            
            else -> 
                AgeVerificationResult.NOT_AGE_RESTRICTED
        }
    }
    
    fun logAgeVerificationResult(result: AgeVerificationResult, videoUrl: String) {
        // Logs structurés avec emoji
    }
}
```

### Ressources

- **Vidéos test 18+** : Identifier des URLs de test valides
- **NewPipeExtractor** : Voir comment gère age-restricted
- **OAuth2 Scope** : `youtube.readonly` suffit-il pour 18+ ?

---

## 📝 État du Code

**Branch** : `main`  
**Build** : ✅ BUILD SUCCESSFUL  
**Tests** : ✅ 27 tests unitaires + 3 tests d'intégration

**Fichiers non commités** :
- `SessionMonitor.kt` (nouveau)
- `SessionStatusTask.kt` (nouveau)
- `BACKLOG.md`, `AGENT.md`, `PROMPT_REPRISE.md` (modifiés)

---

## 🚀 Commandes Utiles

```bash
# Build + Tests
./gradlew -p newpipe-plugin clean build test

# Voir l'état des sessions
./gradlew sessionStatus

# Authentifier un compte (prérequis pour US-5)
./gradlew buildSessions
./gradlew authSessions

# Tester avec une vidéo 18+ (après implémentation)
./gradlew downloadMusic --url="VIDEO_ID_18+"
```

---

## 📊 Progression du Backlog

| US | Titre | Statut | Points |
|----|-------|--------|--------|
| US-1 | Authentification Initiale | ✅ FAIT | 3 |
| US-2 | Téléchargement Authentifié | ✅ FAIT | 5 |
| US-3 | Refresh Automatique Tokens | ✅ FAIT | 5 |
| US-4 | Gestion Erreurs Auth | ✅ FAIT | 3 |
| US-5 | Vidéos 18+ | ⏳ **EN COURS** | 8 |
| US-6 | Monitoring Sessions | ✅ FAIT | 3 |
| US-7 | Tests Intégration | ✅ FAIT | 5 |
| US-8 | Playlists Privées | ⏳ À FAIRE | 5 |

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

**Pour commencer US-5** :
1. Identifier des vidéos 18+ de test sur YouTube
2. Vérifier que le compte authentifié est majeur
3. Analyser les erreurs NewPipeExtractor pour age-restricted
4. Implémenter `AgeVerificationHandler.kt`
5. Ajouter tests unitaires et d'intégration
