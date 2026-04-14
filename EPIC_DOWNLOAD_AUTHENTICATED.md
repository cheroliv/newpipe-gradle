# EPIC : Download with NewPipe while Authenticated on YouTube

**Projet Cible** : `/home/cheroliv/workspace/__repositories__/newpipe-gradle`  
**Statut** : 🔴 À faire  
**Dernière mise à jour** : 2026-04-14

---

## 🎯 Objectif

Permettre le téléchargement de vidéos YouTube en étant authentifié avec des comptes Google, ce qui permet :
- D'accéder aux vidéos réservées aux membres d'une chaîne
- De contourner les restrictions géographiques (geo-blocked)
- D'accéder aux vidéos en âge restreint (age-restricted)
- D'éviter les limitations de taux (rate limiting) de YouTube
- De télécharger des playlists privées

---

## 📋 Contexte Actuel

### Architecture Existant
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

### Composants Déjà Implémentés
- ✅ `AuthSessionTask.kt` : OAuth2 Device Flow pour authentifier les comptes
- ✅ `SessionManager.kt` : Gestion du Round-Robin entre sessions
- ✅ `DownloaderImpl.kt` : Injection des tokens dans les requêtes
- ✅ `SessionConfig` : Structure YAML pour stocker les credentials
- ✅ `buildSessions` : Génération des secrets clients OAuth2

### Limitations Actuelles
- ⚠️ Pas de refresh automatique des tokens expirés
- ⚠️ Pas de gestion des échecs d'authentification
- ⚠️ Pas de tests d'intégration avec de vrais comptes
- ⚠️ Pas de monitoring de l'état des sessions
- ⚠️ Pas de support pour les vidéos 18+ avec vérification d'âge

---

## 🗺️ User Stories

### US-1 : Authentification Initiale des Comptes Google
**Priorité** : 🔴 Haute  
**Estimation** : 3 points  
**Statut** : ✅ FAIT (AuthSessionTask.kt)

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

---

### US-2 : Téléchargement avec Sessions Authentifiées
**Priorité** : 🔴 Haute  
**Estimation** : 5 points  
**Statut** : ✅ FAIT (DownloadMusicTask.kt + SessionManager.kt)

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

---

### US-3 : Refresh Automatique des Tokens Expirés
**Priorité** : 🟠 Moyenne  
**Estimation** : 5 points  
**Statut** : ⏳ À FAIRE

**En tant que** utilisateur authentifié  
**Je veux** que les tokens d'accès soient refreshés automatiquement  
**Afin de** ne pas avoir à relancer `authSessions` manuellement

**Critères d'acceptation** :
```kotlin
// Comportement attendu
class SessionManager {
    // Avant chaque requête :
    // 1. Vérifier si accessToken est expiré
    // 2. Si oui, utiliser refreshToken pour en obtenir un nouveau
    // 3. Si refreshToken est expiré, marquer session invalide
    // 4. Retry avec une autre session
}
```

**Scénarios de test** :
```gherkin
Scenario: Refresh automatique d'un token expiré
  Given une session avec accessToken expiré
  And une session avec refreshToken valide
  When une requête YouTube est effectuée
  Then un nouvel accessToken est obtenu via refreshToken
  And la requête est retryée avec le nouveau token
  And sessions.yml n'est PAS modifié (tokens en mémoire seulement)

Scenario: Session marquée invalide après refresh échoué
  Given une session avec refreshToken expiré
  When le refresh retourne HTTP 400
  Then la session est marquée invalide dans SessionManager
  And une autre session est utilisée
  And le log indique "Session 'compte-principal' marked invalid"

Scenario: Transparent pour l'utilisateur
  Given 2 sessions valides
  And musics.yml contient 5 vidéos
  When je lance ./gradlew downloadMusic
  And 1 session nécessite un refresh
  Then le téléchargement continue sans interruption
  And l'utilisateur ne voit pas le refresh dans les logs
```

**Tâches techniques** :
- [ ] Ajouter `accessToken` et `accessTokenExpiry` dans `Session` (en mémoire)
- [ ] Implémenter `refreshAccessToken()` dans `SessionManager`
- [ ] Intercepter les HTTP 401 dans `DownloaderImpl`
- [ ] Retry automatique avec nouveau token
- [ ] Tests unitaires avec WireMock

**Fichiers à créer/modifier** :
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/SessionManager.kt` (modifier)
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/TokenRefresher.kt` (créer)
- `newpipe-plugin/newpipe/src/test/kotlin/com/cheroliv/newpipe/TokenRefreshTest.kt` (créer)

---

### US-4 : Gestion des Erreurs d'Authentification
**Priorité** : 🟠 Moyenne  
**Estimation** : 3 points  
**Statut** : ⏳ À FAIRE

**En tant que** utilisateur du plugin  
**Je veux** être informé clairement des problèmes d'authentification  
**Afin de** pouvoir les résoudre rapidement

**Critères d'acceptation** :
```bash
# Scénarios d'erreur à gérer :
# 1. refreshToken révoqué par l'utilisateur
# 2. refreshToken expiré (1 an)
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
- [ ] Mapper les codes erreur OAuth2 → messages utilisateur
- [ ] Ajouter des logs structurés avec emoji pour lisibilité
- [ ] Créer un rapport de fin de téléchargement avec état des sessions
- [ ] Tests unitaires pour chaque scénario d'erreur

**Fichiers à créer/modifier** :
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/AuthErrorHandler.kt` (créer)
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/SessionManager.kt` (modifier)
- `newpipe-plugin/newpipe/src/test/kotlin/com/cheroliv/newpipe/AuthErrorTest.kt` (créer)

---

### US-5 : Support des Vidéos 18+ avec Vérification d'Âge
**Priorité** : 🟡 Basse  
**Estimation** : 8 points  
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

---

### US-6 : Monitoring de l'État des Sessions
**Priorité** : 🟡 Basse  
**Estimation** : 3 points  
**Statut** : ⏳ À FAIRE

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
- [ ] Créer `SessionStatusTask.kt`
- [ ] Tracker le nombre de requêtes par session
- [ ] Calculer la date d'expiration des tokens
- [ ] Formattage tableau avec bordures
- [ ] Tests unitaires

**Fichiers à créer** :
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/SessionStatusTask.kt`
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/SessionMonitor.kt`

---

### US-7 : Tests d'Intégration avec Vrais Comptes
**Priorité** : 🟠 Moyenne  
**Estimation** : 5 points  
**Statut** : ⏳ À FAIRE

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

---

### US-8 : Support des Playlists Privées
**Priorité** : 🟡 Basse  
**Estimation** : 5 points  
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

---

## 📊 Tableau de Suivi

| US | Titre | Priorité | Points | Statut | Fichiers |
|----|-------|----------|--------|--------|----------|
| US-1 | Authentification Initiale | 🔴 Haute | 3 | ✅ FAIT | AuthSessionTask.kt |
| US-2 | Téléchargement Authentifié | 🔴 Haute | 5 | ✅ FAIT | DownloadMusicTask.kt, SessionManager.kt |
| US-3 | Refresh Automatique Tokens | 🟠 Moyenne | 5 | ⏳ À FAIRE | TokenRefresher.kt |
| US-4 | Gestion Erreurs Auth | 🟠 Moyenne | 3 | ⏳ À FAIRE | AuthErrorHandler.kt |
| US-5 | Vidéos 18+ | 🟡 Basse | 8 | ⏳ À FAIRE | AgeVerificationHandler.kt |
| US-6 | Monitoring Sessions | 🟡 Basse | 3 | ⏳ À FAIRE | SessionStatusTask.kt |
| US-7 | Tests Intégration | 🟠 Moyenne | 5 | ⏳ À FAIRE | YouTubeAuthIntegrationTest.kt |
| US-8 | Playlists Privées | 🟡 Basse | 5 | ⏳ À FAIRE | PrivatePlaylistHandler.kt |

**Total** : 37 points  
**Faits** : 8 points (22%)  
**Reste** : 29 points (78%)

---

## 🚀 Roadmap Recommandée

### Phase 1 : Consolidation (Sprint 1)
- [x] US-1 : Authentification Initiale ✅
- [x] US-2 : Téléchargement Authentifié ✅
- [ ] US-4 : Gestion Erreurs Auth (3 points)
- [ ] US-7 : Tests Intégration (5 points)

**Objectif** : Rendre l'existant fiable et testable

### Phase 2 : Amélioration UX (Sprint 2)
- [ ] US-3 : Refresh Automatique Tokens (5 points)
- [ ] US-6 : Monitoring Sessions (3 points)

**Objectif** : Réduire la maintenance manuelle

### Phase 3 : Fonctionnalités Avancées (Sprint 3)
- [ ] US-5 : Vidéos 18+ (8 points)
- [ ] US-8 : Playlists Privées (5 points)

**Objectif** : Support cas d'usage avancés

---

## 🔗 Liens Utiles

- **OAuth2 Device Flow** : https://developers.google.com/identity/protocols/oauth2/limited-input-device
- **YouTube API Quotas** : https://developers.google.com/youtube/v3/getting-started#quota
- **NewPipe Extractor** : https://github.com/TeamNewPipe/NewPipeExtractor
- **Sessions YAML** : `newpipe-plugin/sessions.yml`
- **Code Existant** : `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/`

---

## 📝 Notes de Session

### Session Actuelle - 2026-04-14
**Objectif** : Découpage de l'épic en user-stories  
**Réalisé** :
- [x] Analyse du code existant
- [x] Identification des US-1 et US-2 comme "FAIT"
- [x] Création de 6 nouvelles US (US-3 à US-8)
- [x] Estimation des points et priorités
- [x] Définition des critères d'acceptation

**Prochaine session** :
- [ ] Commencer US-4 : Gestion des erreurs d'authentification
- [ ] Ou US-7 : Tests d'intégration pour valider l'existant

---

**Dernière mise à jour** : 2026-04-14  
**Statut** : 🟢 User-stories définies et prêtes pour implémentation
