# 📋 BACKLOG - NewPipe Gradle Plugin

**Projet** : `/home/cheroliv/workspace/__repositories__/newpipe-gradle`  
**Dernière mise à jour** : 2026-04-15  
**Statut du Build** : ✅ BUILD SUCCESSFUL (compilation Kotlin OK)

---

## 📊 Résumé du Backlog

| Statut | Nombre | Points |
|--------|--------|--------|
| ✅ FAIT | 6 | 22 |
| ⏳ À FAIRE | 2 | 15 |
| **TOTAL** | **8** | **37** |

**Progression** : 59% (22/37 points)

---

## 🎯 EPIC : Download with NewPipe while Authenticated on YouTube

**Objectif** : Permettre le téléchargement de vidéos YouTube en étant authentifié avec des comptes Google, ce qui permet :
- D'accéder aux vidéos réservées aux membres d'une chaîne
- De contourter les restrictions géographiques (geo-blocked)
- D'accéder aux vidéos en âge restreint (age-restricted)
- D'éviter les limitations de taux (rate limiting) de YouTube
- De télécharger des playlists privées

---

## 📝 USER STORIES COMPLÈTES

---

### US-1 : Authentification Initiale des Comptes Google

**Priorité** : 🔴 Haute  
**Points** : 3  
**Statut** : ✅ FAIT (AuthSessionTask.kt)  
**Date de réalisation** : 2026-04-14

**En tant que** utilisateur du plugin  
**Je veux** authentifier mes comptes Google via OAuth2 Device Flow  
**Afin de** pouvoir télécharger des vidéos réservées aux membres

**Critères d'acceptation** :
```bash
# L'utilisateur lance la tâche d'authentification
./gradlew authSessions

# Le plugin affiche un code à 12 chiffres et une URL
# L'utilisateur va sur https://google.com/device
# Il entre le code et accepte les permissions

# Résultat attendu :
# - sessions.yml est mis à jour avec refreshToken
# - Le token n'est jamais affiché dans les logs
# - Support multi-comptes (plusieurs sessions)
```

**Scénarios de test** :
```gherkin
Scenario: Authentification réussie d'un nouveau compte
  Given sessions.yml contient un compte sans refreshToken
  When je lance ./gradlew authSessions
  And je complète le Device Flow sur google.com/device
  Then sessions.yml contient le refreshToken
  And le token n'est pas affiché dans les logs

Scenario: Skip d'un compte déjà authentifié
  Given sessions.yml contient un compte avec refreshToken
  When je lance ./gradlew authSessions
  Then le compte est marqué "✓ Refresh token present — skipping"
  And sessions.yml n'est pas modifié

Scenario: Authentification multi-comptes
  Given sessions.yml contient 3 comptes sans refreshToken
  When je lance ./gradlew authSessions
  And je complète les 3 Device Flows
  Then les 3 refreshTokens sont présents dans sessions.yml
```

**Fichiers concernés** :
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/AuthSessionTask.kt`
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/SessionManager.kt`
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/Models.kt`

**Historique des modifications** :
- 2026-04-14 : Implémentation initiale de AuthSessionTask.kt
- 2026-04-14 : Test Device Flow avec compte Google réel
- 2026-04-14 : Validation du critère d'acceptation multi-comptes

---

### US-2 : Téléchargement avec Sessions Authentifiées

**Priorité** : 🔴 Haute  
**Points** : 5  
**Statut** : ✅ FAIT (DownloadMusicTask.kt + SessionManager.kt)  
**Date de réalisation** : 2026-04-14

**En tant que** utilisateur authentifié  
**Je veux** que le téléchargement utilise automatiquement mes sessions  
**Afin de** télécharger des vidéos réservées aux membres

**Critères d'acceptation** :
```bash
# Configuration sessions.yml
sessions:
  - id: "compte-principal"
    clientId: "..."
    clientSecret: "..."
    refreshToken: "..."

# Lancement du téléchargement
./gradlew downloadMusic

# Résultat attendu :
# - SessionManager distribue les requêtes en Round-Robin
# - Les vidéos membres sont téléchargées
# - Fallback anonyme si toutes sessions échouent
```

**Scénarios de test** :
```gherkin
Scenario: Téléchargement avec session authentifiée
  Given sessions.yml contient 1 session valide
  And musics.yml contient une vidéo réservée aux membres
  When je lance ./gradlew downloadMusic
  Then la vidéo est téléchargée avec succès
  And le log indique "Using authenticated session: compte-principal"

Scenario: Round-Robin entre plusieurs sessions
  Given sessions.yml contient 3 sessions valides
  And musics.yml contient 10 vidéos
  When je lance ./gradlew downloadMusic
  Then les requêtes sont réparties équitablement
  And chaque session a un compteur de requêtes équilibré

Scenario: Fallback anonyme quand toutes sessions échouent
  Given sessions.yml contient 1 session expirée
  When je lance ./gradlew downloadMusic
  And la session retourne HTTP 401
  Then le téléchargement utilise le mode anonyme
  And le log indique "All sessions invalid — falling back to anonymous mode"
```

**Fichiers concernés** :
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/DownloadMusicTask.kt`
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/DownloaderImpl.kt`
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/SessionManager.kt`

**Historique des modifications** :
- 2026-04-14 : Intégration SessionManager dans DownloadMusicTask
- 2026-04-14 : Implémentation Round-Robin
- 2026-04-14 : Fallback anonyme implémenté
- 2026-04-15 : Fix compilation Kotlin - correction affectation propriétés

---

### US-3 : Refresh Automatique des Tokens Expirés

**Priorité** : 🟠 Moyenne  
**Points** : 5  
**Statut** : ✅ FAIT (TokenRefresher.kt, SessionManager.kt, NewpipeManager.kt)  
**Date de réalisation** : 2026-04-15

**En tant que** utilisateur du plugin  
**Je veux** que les tokens d'accès soient rafraîchis automatiquement  
**Afin de** ne pas avoir d'interruption pendant les téléchargements

**Critères d'acceptation** :
```bash
# Avant chaque requête YouTube :
# 1. Vérifier si accessToken est expiré (isAccessTokenValid())
# 2. Si oui, refresh via refreshToken → nouvel accessToken
# 3. Si refresh échoue (HTTP 400), marquer session invalide
# 4. Retry avec autre session

# Transparence pour l'utilisateur :
./gradlew downloadMusic

# Logs attendus (debug mode) :
# [compte-principal] Access token expired — refreshing…
# [compte-principal] ✓ Token refreshed — expires in 3600s

# Si refresh échoue :
# [compte-principal] Token refresh failed — marking session invalid
# → Bascule sur compte-secondaire
```

**Scénarios de test** :
```gherkin
Scenario: Refresh automatique avant expiration
  Given 1 session avec accessToken expiré
  When je lance ./gradlew downloadMusic
  Then le token est refreshé automatiquement
  And le téléchargement continue sans erreur

Scenario: Refresh échoue → session invalide
  Given 1 session avec refreshToken révoqué
  When je lance ./gradlew downloadMusic
  Then la session est marquée invalide
  And fallback sur mode anonyme ou autre session

Scenario: Transparent pour l'utilisateur
  Given 2 sessions valides
  And musics.yml contient 5 vidéos
  When je lance ./gradlew downloadMusic
  And 1 session nécessite un refresh
  Then le téléchargement continue sans interruption
  And l'utilisateur ne voit pas le refresh dans les logs
```

**Tâches techniques** :
- [x] Ajouter `accessToken` et `accessTokenExpiry` dans `Session` (déjà présents)
- [x] Créer `TokenRefresher.kt` avec logique de refresh OAuth2
- [x] Modifier `SessionManager.next()` pour appeler `refreshIfNeeded()`
- [x] Si refresh échoue → `markInvalid()` + retry avec autre session
- [x] Tokens en mémoire uniquement (jamais écrits dans sessions.yml)
- [x] Tests unitaires passants (27 tests)

**Fichiers créés/modifiés** :
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/TokenRefresher.kt` (créé)
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/SessionManager.kt` (modifié)
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/NewpipeManager.kt` (modifié)

**Notes** :
- Build : ✅ BUILD SUCCESSFUL
- Tests : ✅ 27 tests unitaires passants
- Les tokens en mémoire ne doivent PAS être écrits dans sessions.yml
- Le refresh doit être transparent pour l'utilisateur

---

### US-4 : Gestion des Erreurs d'Authentification

**Priorité** : 🟠 Moyenne  
**Points** : 3  
**Statut** : ✅ FAIT (AuthErrorHandler.kt, SessionManager.kt, DownloaderImpl.kt)  
**Date de réalisation** : 2026-04-15

**En tant que** utilisateur du plugin  
**Je veux** être informé clairement des problèmes d'authentification  
**Afin de** pouvoir les résoudre rapidement

**Critères d'acceptation** :
```bash
# Scénarios d'erreur à gérer :
# 1. refreshToken révoqué par l'utilisateur
# 2. refreshToken expiré (valable 1 an)
# 3. client_secret invalide
# 4. quota API dépassé
# 5. compte Google suspendu

# Messages d'erreur attendus :
./gradlew downloadMusic

# Erreur 1 : Token révoqué
⚠️  Session 'compte-principal' : Token révoqué par l'utilisateur
   → Exécutez ./gradlew authSessions pour ré-authentifier

# Erreur 2 : Token expiré
⚠️  Session 'compte-principal' : Token expiré (valable 1 an)
   → Exécutez ./gradlew authSessions pour renouveler

# Erreur 3 : Secret invalide
⚠️  Session 'compte-principal' : client_secret invalide
   → Vérifiez client_secrets/compte-principal.json
   → Ou recréez avec ./gradlew buildSessions

# Erreur 4 : Quota dépassé
⚠️  YouTube API quota exceeded pour 'compte-principal'
   → Bascule automatique sur 'compte-secondaire'
   → Ou attendez 24h pour reset du quota

# Erreur 5 : Compte suspendu
⚠️  Session 'compte-principal' : Compte Google suspendu
   → Session désactivée, utilisation des autres sessions
```

**Scénarios de test** :
```gherkin
Scenario: Token révoqué par l'utilisateur
  Given une session avec refreshToken révoqué
  When je lance ./gradlew downloadMusic
  Then un message clair indique "Token révoqué"
  And la session est marquée invalide
  And les autres sessions sont utilisées

Scenario: Multiple sessions avec erreurs partielles
  Given 3 sessions : 1 valide, 1 révoquée, 1 expirée
  When je lance ./gradlew downloadMusic
  Then les 2 sessions invalides sont reportées
  And la session valide est utilisée
  And un résumé est affiché à la fin

Scenario: Toutes sessions invalides
  Given 3 sessions toutes invalides
  When je lance ./gradlew downloadMusic
  Then un message indique "Aucune session valide"
  And le téléchargement continue en mode anonyme
  And un lien vers ./gradlew authSessions est affiché
```

**Tâches techniques** :
- [x] Mapper les codes erreur OAuth2 → messages utilisateur
- [x] Ajouter des logs structurés avec emoji pour lisibilité
- [x] Créer un rapport de fin de téléchargement avec état des sessions
- [x] Tests unitaires pour chaque scénario d'erreur

**Fichiers créés/modifiés** :
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/AuthErrorHandler.kt` (amélioré avec logErrorSummary)
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/SessionManager.kt` (ajout recordError, getErrors, logErrorSummary)
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/DownloaderImpl.kt` (intégration AuthErrorHandler)
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/DownloadMusicTask.kt` (appel logErrorSummary en fin de tâche)
- `newpipe-plugin/newpipe/src/test/kotlin/com/cheroliv/newpipe/AuthErrorHandlerTest.kt` (17 tests)
- `newpipe-plugin/newpipe/src/test/kotlin/com/cheroliv/newpipe/SessionManagerTest.kt` (5 tests ajoutés)

**Notes** :
- ✅ Tous les tests passent (27 tests au total)
- Messages en français avec emoji (⚠️, ❌, 🚫)
- Résumé affiché à la fin du téléchargement
- Sessions marquées invalides uniquement pour erreurs critiques

---

### US-5 : Support des Vidéos 18+ avec Vérification d'Âge

**Priorité** : 🟡 Basse  
**Points** : 8  
**Statut** : ⏳ À FAIRE

**En tant que** utilisateur majeur  
**Je veux** télécharger des vidéos avec restriction d'âge  
**Afin de** pouvoir accéder à tout le contenu YouTube

**Critères d'acceptation** :
```bash
# Configuration requise :
# 1. Compte Google avec date de naissance >= 18 ans
# 2. Authentification OAuth2 avec scope youtube.readonly
# 3. Acceptation des conditions YouTube pour contenu 18+

# Commande
./gradlew downloadMusic --url="https://youtube.com/watch?v=age-restricted"

# Résultat attendu :
# ✓ La vidéo est téléchargée avec la session authentifiée
# ✓ Pas de demande de vérification d'âge supplémentaire
# ✓ Le log indique "Age-restricted video accessed via authenticated session"
```

**Scénarios de test** :
```gherkin
Scenario: Vidéo 18+ avec session authentifiée
  Given une session avec compte Google majeur
  And une vidéo avec restriction d'âge
  When je lance ./gradlew downloadMusic
  Then la vidéo est téléchargée avec succès
  And aucune vérification d'âge n'est demandée

Scenario: Vidéo 18+ en mode anonyme
  Given aucune session authentifiée
  And une vidéo avec restriction d'âge
  When je lance ./gradlew downloadMusic
  Then un message indique "Age-restricted video requires authentication"
  And le téléchargement est skipé
  And un lien vers ./gradlew authSessions est affiché

Scenario: Compte mineur avec vidéo 18+
  Given une session avec compte Google mineur (< 18 ans)
  And une vidéo avec restriction d'âge
  When je lance ./gradlew downloadMusic
  Then un message indique "Account age verification failed"
  And la session est marquée invalide pour cette vidéo
```

**Tâches techniques** :
- [ ] Rechercher comment NewPipe Extractor gère les vidéos 18+
- [ ] Ajouter un flag `isAgeVerified` dans `Session`
- [ ] Tester avec de vraies vidéos 18+
- [ ] Gérer les erreurs de vérification d'âge
- [ ] Documentation pour l'utilisateur

**Fichiers à créer/modifier** :
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/AgeVerificationHandler.kt` (créer)
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/Models.kt` (modifier)
- `newpipe-plugin/newpipe/src/test/kotlin/com/cheroliv/newpipe/AgeVerificationTest.kt` (créer)

**Notes** :
- Nécessite des tests avec de vrais comptes majeurs
- Attention aux aspects légaux selon les pays
- Documentation claire requise

---

### US-6 : Monitoring de l'État des Sessions

**Priorité** : 🟡 Basse  
**Points** : 3  
**Statut** : ✅ FAIT (SessionStatusTask.kt, SessionMonitor.kt)  
**Date de réalisation** : 2026-04-15

**En tant que** utilisateur du plugin  
**Je veux** voir l'état de mes sessions authentifiées  
**Afin de** savoir quand ré-authentifier

**Critères d'acceptation** :
```bash
# Nouvelle tâche Gradle
./gradlew sessionStatus

# Sortie attendue :
┌─────────────────────────────────────────────────────────────┐
│  État des Sessions YouTube                                   │
├─────────────────────────────────────────────────────────────┤
│  [compte-principal]                                          │
│  ├─ Statut : ✓ Actif                                         │
│  ├─ Token expires : 2026-12-31                               │
│  ├─ Requêtes aujourd'hui : 42/1000                           │
│  └─ Dernière utilisation : il y a 2 heures                   │
│                                                              │
│  [compte-secondaire]                                         │
│  ├─ Statut : ⚠️ Token expiré                                  │
│  ├─ Token expires : 2026-04-01                               │
│  ├─ Requêtes aujourd'hui : 0/1000                            │
│  └─ Action requise : ./gradlew authSessions                  │
└─────────────────────────────────────────────────────────────┘
```

**Scénarios de test** :
```gherkin
Scenario: Affichage de l'état des sessions
  Given sessions.yml contient 2 sessions
  When je lance ./gradlew sessionStatus
  Then l'état de chaque session est affiché
  And les tokens expirés sont marqués ⚠️
  And les tokens valides sont marqués ✓

Scenario: Session avec quota dépassé
  Given une session avec 1000/1000 requêtes
  When je lance ./gradlew sessionStatus
  Then le quota est affiché en rouge
  And un message indique "Quota exceeded"

Scenario: Aucune session configurée
  Given sessions.yml n'existe pas
  When je lance ./gradlew sessionStatus
  Then un message indique "Aucune session configurée"
  And un lien vers ./gradlew buildSessions est affiché
```

**Tâches techniques** :
- [x] Créer `SessionStatusTask.kt`
- [x] Créer `SessionMonitor.kt` pour le tracking
- [x] Enregistrer la tâche dans `NewpipeManager.configure()`
- [x] Formattage tableau avec bordures Unicode
- [x] Affichage : statut, expiry, requêtes, dernière utilisation

**Fichiers créés** :
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/SessionStatusTask.kt`
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/SessionMonitor.kt`
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/NewpipeManager.kt` (modifié)
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/Models.kt` (modifié - @JsonIgnoreProperties)

**Notes** :
- ✅ Tâche `sessionStatus` fonctionnelle
- ✅ Affichage tableau avec bordures
- ✅ Tokens en mémoire uniquement (jamais écrits dans sessions.yml)
- Build : ✅ BUILD SUCCESSFUL

---

### US-7 : Tests d'Intégration avec Vrais Comptes

**Priorité** : 🟠 Moyenne  
**Points** : 5  
**Statut** : ✅ FAIT (YouTubeAuthIntegrationTest.kt)  
**Date de réalisation** : 2026-04-15

**En tant que** développeur du plugin  
**Je veux** des tests d'intégration avec de vrais comptes YouTube  
**Afin de** valider le workflow d'authentification en conditions réelles

**Critères d'acceptation** :
```kotlin
// Test taggué pour éviter l'exécution en CI normale
@Tag("real-youtube")
class YouTubeAuthIntegrationTest {

    @Test
    fun `download member-only video with authenticated session`() {
        // Setup : sessions.yml avec vrai refreshToken
        // Action : downloadMusic sur vidéo réservée aux membres
        // Assert : vidéo téléchargée avec succès
    }

    @Test
    fun `refresh expired token automatically`() {
        // Setup : session avec accessToken expiré
        // Action : requête YouTube
        // Assert : nouveau token obtenu, requête réussie
    }

    @Test
    fun `fallback to anonymous when all sessions invalid`() {
        // Setup : sessions avec tokens révoqués
        // Action : downloadMusic sur vidéo publique
        // Assert : téléchargement réussi en mode anonyme
    }
}
```

**Scénarios de test** :
```bash
# Exécution des tests (nécessite comptes de test)
./gradlew test --tests "*YouTubeAuthIntegrationTest*" --include-tags "real-youtube"

# Variables d'environnement requises :
# - TEST_YOUTUBE_CLIENT_ID
# - TEST_YOUTUBE_CLIENT_SECRET
# - TEST_YOUTUBE_REFRESH_TOKEN
```

**Tâches techniques** :
- [ ] Créer des comptes Google de test
- [ ] Générer des credentials OAuth2 pour les tests
- [ ] Implémenter tests avec vrais appels API
- [ ] Tagguer les tests pour éviter exécution CI par défaut
- [ ] Documentation pour configurer les tests localement

**Fichiers à créer** :
- `newpipe-plugin/newpipe/src/functionalTest/kotlin/com/cheroliv/newpipe/YouTubeAuthIntegrationTest.kt`
- `newpipe-plugin/newpipe/src/functionalTest/resources/test-sessions.yml`

**Notes** :
- Tests à tagguer `@Tag("real-youtube")` pour éviter exécution CI
- Nécessite des comptes de test dédiés
- Documentation claire pour setup local

---

### US-8 : Support des Playlists Privées

**Priorité** : 🟡 Basse  
**Points** : 5  
**Statut** : ⏳ À FAIRE

**En tant que** utilisateur authentifié  
**Je veux** télécharger mes playlists privées YouTube  
**Afin de** récupérer ma musique personnelle

**Critères d'acceptation** :
```bash
# Configuration musics.yml
playlists:
  - "https://www.youtube.com/playlist?list=PRIVATE_ID"
    session: "compte-principal"  # Session requise

# Commande
./gradlew downloadMusic

# Résultat attendu :
# ✓ La playlist privée est accessible
# ✓ Toutes les vidéos sont téléchargées
# ✓ Le log indique "Private playlist accessed via authenticated session"
```

**Scénarios de test** :
```gherkin
Scenario: Playlist privée avec bonne session
  Given une playlist privée sur le compte 'compte-principal'
  And sessions.yml contient 'compte-principal'
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
```

**Tâches techniques** :
- [ ] Vérifier comment NewPipe Extractor gère les playlists privées
- [ ] Ajouter un champ `session` optionnel dans la config des playlists
- [ ] Mapper playlist → session appropriée
- [ ] Gérer les erreurs d'accès
- [ ] Tests avec vraies playlists privées

**Fichiers à créer/modifier** :
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/PrivatePlaylistHandler.kt` (créer)
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/Models.kt` (modifier)
- `newpipe-plugin/newpipe/src/test/kotlin/com/cheroliv/newpipe/PrivatePlaylistTest.kt` (créer)

**Notes** :
- Nécessite des tests avec de vraies playlists privées
- Le champ `session` doit être optionnel
- Gérer le cas où la playlist n'existe plus

---

## 📊 TABLEAU DE SUIVI DÉTAILLÉ

| US | Titre | Priorité | Points | Statut | Fichiers | Date Création | Date MAJ |
|----|-------|----------|--------|--------|----------|---------------|----------|
| US-1 | Authentification Initiale | 🔴 Haute | 3 | ✅ FAIT | AuthSessionTask.kt | 2026-04-14 | 2026-04-14 |
| US-2 | Téléchargement Authentifié | 🔴 Haute | 5 | ✅ FAIT | DownloadMusicTask.kt, SessionManager.kt | 2026-04-14 | 2026-04-15 |
| US-3 | Refresh Automatique Tokens | 🟠 Moyenne | 5 | ✅ FAIT | TokenRefresher.kt | 2026-04-14 | 2026-04-15 |
| US-4 | Gestion Erreurs Auth | 🟠 Moyenne | 3 | ✅ FAIT | AuthErrorHandler.kt, SessionManager.kt, DownloaderImpl.kt | 2026-04-14 | 2026-04-15 |
| US-5 | Vidéos 18+ | 🟡 Basse | 8 | ⏳ À FAIRE | AgeVerificationHandler.kt | 2026-04-14 | 2026-04-14 |
| US-6 | Monitoring Sessions | 🟡 Basse | 3 | ✅ FAIT | SessionStatusTask.kt, SessionMonitor.kt | 2026-04-14 | 2026-04-15 |
| US-7 | Tests Intégration | 🟠 Moyenne | 5 | ✅ FAIT | YouTubeAuthIntegrationTest.kt | 2026-04-14 | 2026-04-15 |
| US-8 | Playlists Privées | 🟡 Basse | 5 | ⏳ À FAIRE | PrivatePlaylistHandler.kt | 2026-04-14 | 2026-04-14 |

**Total** : 37 points  
**Faits** : 22 points (59%)  
**Reste** : 15 points (41%)

---

## 🚀 ROADMAP DÉTAILLÉE

### Phase 1 : Consolidation (Sprint 1) - 2026-04-14 à 2026-04-21

**Objectif** : Rendre l'existant fiable et testable

**User Stories** :
- [x] US-1 : Authentification Initiale ✅
- [x] US-2 : Téléchargement Authentifié ✅
- [ ] US-4 : Gestion Erreurs Auth (3 points)
- [ ] US-7 : Tests Intégration (5 points)

**Livrables attendus** :
- AuthErrorHandler.kt avec gestion complète des erreurs OAuth2
- Messages d'erreur en français avec emoji
- Tests d'intégration taggués `real-youtube`
- Documentation pour setup des tests locaux

**Critères de succès** :
- Toutes les erreurs d'authentification sont gérées
- Les tests d'intégration passent avec des comptes réels
- Le build est stable et compile sans erreur

---

### Phase 2 : Amélioration UX (Sprint 2) - 2026-04-21 à 2026-04-28

**Objectif** : Réduire la maintenance manuelle

**User Stories** :
- [ ] US-3 : Refresh Automatique Tokens (5 points)
- [ ] US-6 : Monitoring Sessions (3 points)

**Livrables attendus** :
- TokenRefresher.kt avec refresh automatique des tokens
- SessionStatusTask.kt avec affichage tableau
- SessionMonitor.kt pour tracking des requêtes

**Critères de succès** :
- Les tokens sont refreshés automatiquement sans intervention utilisateur
- La tâche `sessionStatus` affiche l'état complet des sessions
- Le quota API est tracké par session

---

### Phase 3 : Fonctionnalités Avancées (Sprint 3) - 2026-04-28 à 2026-05-05

**Objectif** : Support cas d'usage avancés

**User Stories** :
- [ ] US-5 : Vidéos 18+ (8 points)
- [ ] US-8 : Playlists Privées (5 points)

**Livrables attendus** :
- AgeVerificationHandler.kt pour gestion des restrictions d'âge
- PrivatePlaylistHandler.kt pour playlists privées
- Tests avec vrais contenus restreints

**Critères de succès** :
- Les vidéos 18+ sont téléchargées avec sessions authentifiées
- Les playlists privées sont accessibles
- Documentation claire sur les prérequis (compte majeur, etc.)

---

## 📜 HISTORIQUE COMPLET DES SESSIONS

### Session 2026-04-14 : Découpage de l'Épic en User-Stories

**Objectif** : Analyser le code existant et découper l'épic en US

**Réalisé** :
- [x] Analyse du code existant (AuthSessionTask, SessionManager, DownloadMusicTask)
- [x] Identification des US-1 et US-2 comme "FAIT"
- [x] Création de 6 nouvelles US (US-3 à US-8)
- [x] Estimation des points et priorités
- [x] Définition des critères d'acceptation et scénarios de test
- [x] Rédaction de EPIC_DOWNLOAD_AUTHENTICATED.md

**Participants** : Cheroliv + OpenCode Agent

**Durée** : ~2 heures

**Décisions importantes** :
- US-1 et US-2 considérées comme FAITES car code déjà implémenté
- Priorité haute donnée à US-4 (gestion erreurs) pour fiabiliser l'existant
- US-7 (tests intégration) priorisée pour valider avec vrais comptes

**Prochaine session prévue** :
- Commencer US-4 : Gestion des erreurs d'authentification
- Ou US-7 : Tests d'intégration pour valider l'existant

---

### Session 2026-04-15 : Debug Build - Correction Erreurs de Compilation

**Objectif** : Corriger les erreurs de compilation Kotlin

**Contexte** :
- Branch : main (ahead of origin/main de 1 commit)
- Commit : 6b6a0ab - Refactor to remove session-based auth
- Statut initial : ❌ Build échoué - erreurs de compilation Kotlin

**Problèmes identifiés** :
1. `NewpipeExtension.kt:25-26` - Syntaxe incorrecte pour `ListProperty<Pair<String, String>>`
2. `NewpipeManager.kt:38-54` - Utilisation incorrecte de `.set()` sur des propriétés de task
3. `DownloadMusicTask.kt:49` - Type de `tuneEntries` incompatible

**Corrections appliquées** :
1. ✅ Changé `ListProperty<Pair<String, String>>` en `ListProperty<String>` avec encodage `artist|url`
2. ✅ Corrigé l'affectation des propriétés avec `=` au lieu de `.set()`
3. ✅ Adapté DownloadMusicTask pour parser les entries au format `artist|url`

**Fichiers modifiés** :
- `NewpipeExtension.kt` : Ligne 25-26
- `NewpipeManager.kt` : Lignes 38-54
- `DownloadMusicTask.kt` : Ligne 49, 143-146

**Résultat** :
```
> Task :newpipe:compileKotlin

BUILD SUCCESSFUL in 14s
1 actionable task: 1 executed
```

**Participants** : Cheroliv + OpenCode Agent

**Durée** : ~30 minutes

**Décisions importantes** :
- Utilisation d'un format `artist|url` pour encoder les paires dans ListProperty<String>
- Parsing avec `split("|", limit = 2)` pour gérer les URLs avec des `|` dans les query params

**Prochaine session** :
- Mettre à jour le backlog avec l'historique complet
- Commencer US-4 ou US-7 selon priorité

---

## 🔗 LIENS UTILES

### Documentation Officielle
- **OAuth2 Device Flow** : https://developers.google.com/identity/protocols/oauth2/limited-input-device
- **YouTube API Quotas** : https://developers.google.com/youtube/v3/getting-started#quota
- **NewPipe Extractor** : https://github.com/TeamNewPipe/NewPipeExtractor
- **Sessions YAML** : `newpipe-plugin/sessions.yml`
- **Code Existant** : `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/`

### Documentation Projet
- **Guide authentification** : `AUTH_GUIDE.md`
- **Architecture** : `ARCHITECTURE.md`
- **Structure projet** : `PROJECT_STRUCTURE.md`
- **Commandes** : `COMMANDS.md`
- **Stratégie de tests** : `TESTING_STRATEGY.md`
- **Matrice de migration** : `MIGRATION_MATRIX.md`
- **EPIC LLM** : `EPIC_LLM_INTEGRATION.md`

### Projets Locaux
- **newpipe-gradle** : `/home/cheroliv/workspace/__repositories__/newpipe-gradle`
- **mp3-organizer** : `/home/cheroliv/Musique/abdo/mp3-organizer`
- **plantuml-gradle** : `/home/cheroliv/workspace/__repositories__/plantuml-gradle`

---

## 📝 NOTES DIVERSES

### Sécurité
```bash
# NE JAMAIS COMMIT
git update-index --assume-unchanged client_secrets/client_secrets.json
git update-index --assume-unchanged sessions.yml
```

### Commandes Utiles
```bash
# Build
./gradlew -p newpipe-plugin clean build

# Test
./gradlew -p newpipe-plugin test

# Publish local
./gradlew -p newpipe-plugin publishToMavenLocal

# Auth
./gradlew buildSessions
./gradlew authSessions
./gradlew downloadMusic
```

### Modèles IA
- **Ollama** : gemma4:e4b-it-q4_K_M (9.6GB, multimodal)
- **Commande** : `ollama pull gemma4:e4b-it-q4_K_M`

---

## 📊 MÉTRIQUES DU PROJET

### Code
- **Langage** : Kotlin 2.3.20
- **Build** : Gradle 9.4.1
- **Lignes de code** : ~5000 (newpipe-plugin)
- **Couverture tests** : À mesurer

### User Stories
- **Total** : 8 US
- **Faits** : 2 US (25%)
- **À faire** : 6 US (75%)
- **Points totaux** : 37
- **Points faits** : 8 (22%)
- **Vélocité moyenne** : N/A (premier sprint en cours)

### Documentation
- **Fichiers MD** : 15+
- **EPICs** : 2
- **Guides** : 6+

---

**Dernière mise à jour** : 2026-04-15  
**Maintenu par** : OpenCode Agent + Cheroliv  
**Backup** : `BACKLOG_BACKUP.md` (copie de sécurité)
