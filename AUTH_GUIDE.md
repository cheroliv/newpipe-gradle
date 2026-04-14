# 🔐 Guide d'Authentification Google OAuth2

**Dernière mise à jour** : 2026-04-14  
**Statut** : 🔴 Priorité 1

---

## 📋 Vue d'Ensemble

L'authentification Google OAuth2 permet au plugin NewPipe de :
- Télécharger des vidéos réservées aux membres d'une chaîne
- Contourner les restrictions géographiques (geo-blocked)
- Accéder aux vidéos avec restriction d'âge (age-restricted)
- Éviter les limitations de taux (rate limiting) de YouTube
- Télécharger des playlists privées

---

## 🚀 Setup Google Cloud Console

### Étape 1 : Créer un Projet Google Cloud

1. Aller sur https://console.cloud.google.com/
2. Cliquer sur "Sélectionner un projet" → "Nouveau projet"
3. Nommer le projet (ex: `newpipe-youtube-downloader`)
4. Cliquer sur "Créer"

### Étape 2 : Activer YouTube Data API v3

1. Dans le tableau de bord, cliquer sur "Activer des API et services"
2. Rechercher "YouTube Data API v3"
3. Cliquer sur "Activer"

### Étape 3 : Créer les Credentials OAuth2

1. Aller dans "Identifiants" (menu de gauche)
2. Cliquer sur "Créer des identifiants" → "ID client OAuth"
3. Si demandé, configurer l'écran de consentement OAuth :
   - Type d'application : "Externe"
   - Nom de l'application : `NewPipe Gradle Plugin`
   - Email de support : votre email
4. Type d'application : **Application de bureau**
5. Nom : `newpipe-gradle-desktop`
6. Cliquer sur "Créer"
7. Télécharger le fichier JSON → `client_secrets.json`

### Étape 4 : Placer le Fichier de Secrets

```bash
# Créer le dossier s'il n'existe pas
mkdir -p /home/cheroliv/workspace/__repositories__/newpipe-gradle/client_secrets

# Copier le fichier téléchargé
cp ~/Téléchargements/client_secrets.json \
   /home/cheroliv/workspace/__repositories__/newpipe-gradle/client_secrets/
```

**Structure** :
```
client_secrets/
├── client_secrets.json          # Credentials OAuth2
└── .gitkeep                     # Garder le dossier dans git
```

---

## 🔒 Sécurité

### ⚠️ NE JAMAIS COMMIT

```bash
# Vérifier que le dossier est dans .gitignore
cat .gitignore | grep client_secrets

# Si ce n'est pas le cas, ajouter
echo "client_secrets" >> .gitignore

# Vérifier que git ignore bien le dossier
git check-ignore client_secrets/
```

### Marquer comme "assume-unchanged" (optionnel)

```bash
# Si le fichier a déjà été commit par erreur
git update-index --assume-unchanged client_secrets/client_secrets.json
```

---

## 🎯 Flux d'Authentification

### Tâche Gradle : buildSessions

```bash
cd /home/cheroliv/workspace/__repositories__/newpipe-gradle
./gradlew buildSessions
```

**Ce que fait cette tâche** :
1. Lit `client_secrets/client_secrets.json`
2. Génère une configuration de session dans `sessions.yml`
3. Affiche un code d'authentification et une URL

### Tâche Gradle : authSessions

```bash
./gradlew authSessions
```

**Ce que fait cette tâche** :
1. Pour chaque session sans `refreshToken` :
   - Affiche un code à 12 chiffres (ex: `ABCD-EFGH-IJKL`)
   - Affiche une URL : `https://google.com/device`
2. L'utilisateur doit :
   - Aller sur l'URL
   - Entrer le code
   - Accepter les permissions YouTube
3. La tâche reçoit le `refreshToken` et le stocke dans `sessions.yml`

### Exemple de Sortie

```
============================================================
Checking 2 session(s)…
============================================================

[compte-principal] Checking…

┌─────────────────────────────────────────────────────────┐
│  Compte : compte-principal                              │
│                                                         │
│  1. Allez sur  : https://google.com/device              │
│  2. Entrez le code : ABCD-EFGH-IJKL                     │
│                                                         │
│  En attente de l'authentification…                      │
└─────────────────────────────────────────────────────────┘

[compte-principal] ✓ Authenticated — refresh token obtained

[compte-secondaire] Checking…
[compte-secondaire] ✓ Refresh token present — skipping

============================================================
✓ sessions.yml updated with new refresh token(s)
============================================================
```

---

## 📄 Structure de sessions.yml

```yaml
sessions:
  - id: "compte-principal"
    clientId: "123456789-abcdefghijk.apps.googleusercontent.com"
    clientSecret: "GOCSPX-abcdefghijklmnop"
    refreshToken: "1//0abcdefghijklmnop..."
    
  - id: "compte-secondaire"
    clientId: "987654321-zyxwvutsrq.apps.googleusercontent.com"
    clientSecret: "GOCSPX-zyxwvutsrqponmlk"
    refreshToken: "1//0zyxwvutsrqponmlk..."
```

**Champs** :
- `id` : Nom unique de la session (utilisé dans les logs)
- `clientId` : Client ID OAuth2
- `clientSecret` : Client Secret OAuth2
- `refreshToken` : Token de rafraîchissement (obtenu via Device Flow)

**⚠️ IMPORTANT** :
- `accessToken` n'est PAS stocké (obtenu à chaque session via refresh)
- `refreshToken` est valide 1 an (puis nécessite ré-authentification)

---

## 🧪 Vérification

### Vérifier que les secrets sont présents

```bash
ls -la client_secrets/
# Doit afficher : client_secrets.json
```

### Vérifier que sessions.yml est configuré

```bash
cat sessions.yml
# Doit afficher au moins une session avec refreshToken
```

### Tester l'authentification

```bash
./gradlew authSessions
```

### Télécharger une vidéo avec session authentifiée

```bash
./gradlew downloadMusic --url="https://www.youtube.com/watch?v=dQw4w9WgXcQ"
```

---

## 🔄 Refresh Automatique des Tokens

### Comment ça marche

1. **Au démarrage** : `SessionManager` charge `sessions.yml`
2. **Avant chaque requête** :
   - Vérifie si `accessToken` est expiré
   - Si oui, utilise `refreshToken` pour en obtenir un nouveau
   - Si `refreshToken` est expiré, marque la session invalide
3. **Fallback** : Si toutes sessions invalides → mode anonyme

### Durée de Vie des Tokens

| Token | Durée de vie | Stocké dans |
|-------|--------------|-------------|
| `accessToken` | 1 heure | Mémoire uniquement |
| `refreshToken` | 1 an | `sessions.yml` |

### Quand Ré-authentifier

- **Tous les 12 mois** : `refreshToken` expire
- **Si révoqué** : L'utilisateur révoque l'accès dans son compte Google
- **Si erreur 401/403** : Token invalide au moment de la requête

---

## 🛠️ Commandes Utiles

### Construire les sessions

```bash
./gradlew buildSessions
```

### Authentifier les sessions

```bash
./gradlew authSessions
```

### Vérifier l'état des sessions (à implémenter)

```bash
./gradlew sessionStatus
```

### Nettoyer les sessions

```bash
./gradlew cleanSessions
```

### Télécharger avec session

```bash
# Vidéo unique
./gradlew downloadMusic --url="https://www.youtube.com/watch?v=VIDEO_ID"

# Depuis musics.yml
./gradlew downloadMusic
```

---

## 🐛 Dépannage

### Erreur : "client_secret invalide"

**Cause** : Le fichier `client_secrets.json` est corrompu ou inexistant

**Solution** :
```bash
# Vérifier le fichier
cat client_secrets/client_secrets.json

# Si vide ou absent, recréer dans Google Cloud Console
```

### Erreur : "Token révoqué par l'utilisateur"

**Cause** : L'utilisateur a révoqué l'accès dans son compte Google

**Solution** :
```bash
# Ré-authentifier
./gradlew authSessions
```

### Erreur : "Quota API dépassé"

**Cause** : Limite quotidienne de l'API YouTube atteinte (1000 requêtes/jour)

**Solution** :
1. Attendre 24h (reset du quota)
2. OU ajouter un deuxième compte Google dans `sessions.yml`
3. OU demander une augmentation de quota dans Google Cloud Console

### Erreur : "Toutes sessions invalides"

**Cause** : Tous les tokens sont expirés ou révoqués

**Solution** :
```bash
# Ré-authentifier tous les comptes
./gradlew authSessions
```

---

## 📚 Références

- **OAuth2 Device Flow** : https://developers.google.com/identity/protocols/oauth2/limited-input-device
- **YouTube API Quotas** : https://developers.google.com/youtube/v3/getting-started#quota
- **Google Cloud Console** : https://console.cloud.google.com/
- **NewPipeExtractor** : https://github.com/TeamNewPipe/NewPipeExtractor

---

## 🔗 Voir Aussi

- **Architecture technique** : `doc/ARCHITECTURE.md`
- **Structure des projets** : `doc/PROJECT_STRUCTURE.md`
- **Commandes principales** : `doc/COMMANDS.md`
- **EPIC Download Authenticated** : `doc/EPIC_DOWNLOAD_AUTHENTICATED.md`

---

**Dernière mise à jour** : 2026-04-14
