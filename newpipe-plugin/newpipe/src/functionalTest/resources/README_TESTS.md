# Tests d'Intégration YouTube - Guide de Configuration

## 📋 Vue d'Ensemble

Les tests d'intégration valident l'authentification OAuth2 avec de **vrais comptes Google** pour s'assurer que :
- Les vidéos réservées aux membres sont téléchargeables
- Le refresh des tokens fonctionne correctement
- Le fallback en mode anonyme fonctionne quand toutes les sessions sont invalides

**Tag** : `@Tag("real-youtube")` - Ces tests ne sont **PAS** exécutés en CI par défaut.

---

## 🚀 Prérequis

### 1. Compte Google de Test

Vous devez avoir un compte Google avec :
- Un projet Google Cloud Console configuré pour OAuth2
- YouTube Data API v3 activée
- Credentials OAuth2 de type "Desktop app"

### 2. Obtenir les Credentials

#### Étape 1 : Google Cloud Console
1. Aller sur https://console.cloud.google.com/
2. Créer un nouveau projet (ou utiliser un projet existant)
3. Activer **YouTube Data API v3** :
   ```
   API & Services → Library → YouTube Data API v3 → Enable
   ```

#### Étape 2 : Créer les Credentials OAuth2
1. `API & Services → Credentials → Create Credentials → OAuth client ID`
2. Type d'application : **Desktop app**
3. Télécharger le JSON → `client_secret_XXX.json`

#### Étape 3 : Obtenir le Refresh Token
1. Lancer la tâche d'authentification :
   ```bash
   ./gradlew buildSessions
   ./gradlew authSessions
   ```
2. Suivre le Device Flow avec le code affiché
3. Récupérer le `refreshToken` dans `sessions.yml`

---

## ⚙️ Configuration des Tests

### Option 1 : Variables d'Environnement (Recommandé)

```bash
export TEST_YOUTUBE_CLIENT_ID="123456789-xxx.apps.googleusercontent.com"
export TEST_YOUTUBE_CLIENT_SECRET="GOCSPX-xxx"
export TEST_YOUTUBE_REFRESH_TOKEN="1//xxx"
```

### Option 2 : Fichier .env.local (Pour développement)

Créer `.env.local` à la racine du projet :
```bash
TEST_YOUTUBE_CLIENT_ID=123456789-xxx.apps.googleusercontent.com
TEST_YOUTUBE_CLIENT_SECRET=GOCSPX-xxx
TEST_YOUTUBE_REFRESH_TOKEN=1//xxx
```

Puis charger les variables avant les tests :
```bash
set -a && source .env.local && set +a && ./gradlew functionalTest
```

---

## 🧪 Exécution des Tests

### Lancer tous les tests d'intégration
```bash
./gradlew functionalTest --tests "*YouTubeAuthIntegrationTest*" --include-tags "real-youtube"
```

### Lancer un test spécifique
```bash
# Test 1 : Télécharger une vidéo membre-only
./gradlew functionalTest --tests "*download.member-only*" --include-tags "real-youtube"

# Test 2 : Refresh automatique de token
./gradlew functionalTest --tests "*refresh.expired*" --include-tags "real-youtube"

# Test 3 : Fallback mode anonyme
./gradlew functionalTest --tests "*fallback.anonymous*" --include-tags "real-youtube"
```

### Lancer TOUS les tests (y compris integration)
```bash
./gradlew -p newpipe-plugin test functionalTest
```

---

## 📊 Tests Implémentés

### Test 1 : `download member-only video with authenticated session`
**Objectif** : Valider qu'une vidéo réservée aux membres est téléchargeable avec une session authentifiée

**Prérequis** :
- Session avec refreshToken valide
- URL d'une vidéo membre-only (à remplacer dans le test)

**Critère de succès** :
- Métadonnées récupérées avec succès
- Titre et uploader non vides

---

### Test 2 : `refresh expired token automatically`
**Objectif** : Valider que le token est refreshé automatiquement si nécessaire

**Prérequis** :
- Session avec refreshToken valide

**Critère de succès** :
- Vidéo téléchargée avec succès
- Token refreshé si nécessaire (transparent pour l'utilisateur)

---

### Test 3 : `fallback to anonymous when all sessions invalid`
**Objectif** : Valider que le téléchargement fonctionne en mode anonyme quand les sessions sont invalides

**Prérequis** :
- Session avec credentials invalides

**Critère de succès** :
- Vidéo publique téléchargée en mode anonyme
- Pas d'erreur bloquante

---

## 📁 Fichiers de Test

### test-sessions.yml (Template)
Fichier : `newpipe-plugin/newpipe/src/functionalTest/resources/test-sessions.yml`

```yaml
sessions:
  - id: "test-account"
    clientId: "XXX_CLIENT_ID_XXX"
    clientSecret: "XXX_CLIENT_SECRET_XXX"
    refreshToken: "XXX_REFRESH_TOKEN_XXX"
```

**⚠️ IMPORTANT** : Ce fichier est un template. Ne jamais commit les vraies valeurs !

---

## 🔐 Sécurité

### ⚠️ NE JAMAIS COMMIT
```bash
# Ignorer les fichiers sensibles
git update-index --assume-unchanged sessions.yml
git update-index --assume-unchanged client_secrets/*.json
```

### Fichiers .gitignore
```
sessions.yml
client_secrets/
.env.local
```

---

## 🐛 Debugging

### Les tests sont skippés
**Symptôme** : `SKIPPED` dans les résultats de tests

**Causes possibles** :
1. Variables d'environnement non définies
   ```bash
   echo $TEST_YOUTUBE_CLIENT_ID  # Doit afficher une valeur
   ```

2. Refresh token manquant dans sessions.yml
   ```bash
   ./gradlew authSessions  # Compléter le Device Flow
   ```

3. sessions.yml absent
   ```bash
   ./gradlew buildSessions  # Créer le fichier
   ```

### Échec avec HTTP 401
**Cause** : Token révoqué ou expiré

**Solution** :
```bash
./gradlew authSessions  # Re-authentifier
```

### Échec avec HTTP 403
**Cause** : Quota API dépassé ou permissions insuffisantes

**Solutions** :
1. Attendre 24h pour reset du quota
2. Vérifier que YouTube Data API v3 est activée
3. Vérifier les scopes OAuth2

---

## 📝 Commandes Utiles

```bash
# Vérifier les variables d'environnement
env | grep TEST_YOUTUBE

# Lancer les tests avec logs détaillés
./gradlew functionalTest --tests "*YouTubeAuthIntegrationTest*" --include-tags "real-youtube" --info

# Nettoyer et rebuild
./gradlew -p newpipe-plugin clean build

# Voir le rapport de tests
open newpipe-plugin/newpipe/build/reports/tests/functionalTest/index.html
```

---

## 📚 Références

- **OAuth2 Device Flow** : https://developers.google.com/identity/protocols/oauth2/limited-input-device
- **YouTube API Quotas** : https://developers.google.com/youtube/v3/getting-started#quota
- **NewPipe Extractor** : https://github.com/TeamNewPipe/NewPipeExtractor
- **User Story US-7** : `BACKLOG.md#us-7--tests-dintégration-avec-vrais-comptes`

---

**Dernière mise à jour** : 2026-04-15  
**Auteur** : OpenCode Agent + Cheroliv
