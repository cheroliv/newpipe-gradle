# 🚀 Commandes Principales - NewPipe Gradle Ecosystem

**Dernière mise à jour** : 2026-04-14

---

## Authentification Google (🔴 Priorité 1)

### Construire les sessions YouTube

```bash
cd /home/cheroliv/workspace/__repositories__/newpipe-gradle
./gradlew buildSessions
```

**Ce que fait cette tâche** :
- Lit `client_secrets/client_secrets.json`
- Génère une configuration de session dans `sessions.yml`

---

### Authentifier les sessions

```bash
./gradlew authSessions
```

**Ce que fait cette tâche** :
- Pour chaque session sans `refreshToken` :
  - Affiche un code d'authentification
  - Attend que l'utilisateur complète le Device Flow sur google.com/device
  - Stocke le `refreshToken` dans `sessions.yml`

---

### Télécharger avec session authentifiée

```bash
# Vidéo unique
./gradlew downloadMusic --url="https://www.youtube.com/watch?v=dQw4w9WgXcQ"

# Depuis musics.yml
./gradlew downloadMusic
```

**Ce que fait cette tâche** :
- Utilise les sessions authentifiées pour télécharger
- Round-Robin entre les sessions disponibles
- Fallback anonyme si toutes sessions invalides

---

### Nettoyer les sessions (optionnel)

```bash
./gradlew cleanSessions
```

**Ce que fait cette tâche** :
- Supprime les tokens de `sessions.yml`
- Nécessite ré-authentification

---

## Tâches IA (mp3-organizer - Référence)

### Chat interactif

```bash
cd /home/cheroliv/Musique/abdo/mp3-organizer
./gradlew llmChat --args="quelle est la structure de la DB ?"
```

**Sortie attendue** :
```
> Chat interactif avec LLM (gemma4:e4b-it-q4_K_M)
> Tapez 'quit' pour quitter

Vous : quelle est la structure de la DB ?
LLM  : La base de données contient 3 tables principales :
       - artists : Informations sur les artistes
       - albums  : Albums avec référence vers artists
       - tracks  : Pistes avec référence vers albums
       
       Schéma :
       artists(id, name, formed_year, genre)
       albums(id, artist_id, title, release_year)
       tracks(id, album_id, title, duration, path)
```

---

### Génération SQL

```bash
./gradlew sqlPrompt --args="top 10 artistes par nombre de tracks"
```

**Sortie attendue** :
```sql
SELECT a.name, COUNT(t.id) as track_count
FROM artists a
JOIN albums al ON a.id = al.artist_id
JOIN tracks t ON al.id = t.album_id
GROUP BY a.id, a.name
ORDER BY track_count DESC
LIMIT 10;
```

---

### Playlist intelligente + VLC

```bash
./gradlew playSmart --args="jazz piano détente pour coder"
```

**Ce que fait cette tâche** :
1. Analyse le prompt avec LLM
2. Génère une requête SQL
3. Exécute la requête dans PostgreSQL
4. Crée un fichier playlist.xspf
5. Lance VLC avec la playlist

---

### Playlist par artiste

```bash
./gradlew playArtist --args="Miles Davis"
```

---

### Playlist par genre

```bash
./gradlew playGenre --args="jazz"
```

---

## Build & Test

### newpipe-gradle

```bash
# Build complet
./gradlew clean build

# Tests unitaires
./gradlew test

# Tests fonctionnels
./gradlew functionalTest

# Coverage report
./gradlew koverHtmlReport

# Publier en local
./gradlew publishToMavenLocal
```

---

### mp3-organizer

```bash
# Build complet
./gradlew clean build

# Tests unitaires
./gradlew test

# Tests d'intégration (nécessite DB + Ollama)
./gradlew integrationTest

# Coverage report
./gradlew koverHtmlReport
```

---

## Tâches Futures (À Créer)

### Suggérer des sessions YouTube

```bash
./gradlew suggestSessions --args="jazz piano années 60"
```

**Ce que fera cette tâche** :
- Analyse le prompt avec LLM
- Génère une configuration YAML avec filtres YouTube
- Écrit dans `sessions/suggest-jazz-piano-60s.yml`

---

### Générer playlist YouTube

```bash
./gradlew generatePlaylist --args="électro française"
```

**Ce que fera cette tâche** :
- Analyse le prompt avec LLM
- Recherche des vidéos YouTube avec NewPipeExtractor
- Crée un fichier playlist avec les URLs
- Optionnel : Télécharge les vidéos

---

### Chat avec la codebase

```bash
./gradlew chatWithCodebase --args="comment fonctionne l'authentification ?"
```

**Ce que fera cette tâche** :
- Indexe le code source du plugin
- Répond aux questions avec le contexte du code
- Utilise RAG (Retrieval-Augmented Generation)

---

### Analyser le code

```bash
./gradlew analyzeCode
```

**Ce que fera cette tâche** :
- Scan le code pour dettes techniques
- Suggère des refactorings
- Vérifie les dépendances obsolètes

---

## Setup & Vérifications

### Vérifier Ollama

```bash
# Liste des modèles installés
ollama list

# Doit afficher : gemma4:e4b-it-q4_K_M

# Si absent, installer
ollama pull gemma4:e4b-it-q4_K_M

# Démarrer le serveur
ollama serve
```

---

### Vérifier PostgreSQL (mp3-organizer)

```bash
# Voir les containers Docker
docker ps | grep postgres

# Si absent, démarrer
docker-compose up -d postgres

# Tester la connexion
psql -U postgres -d mp3_organizer -c "SELECT COUNT(*) FROM artists;"
```

---

### Vérifier secrets OAuth (newpipe-gradle)

```bash
# Vérifier que le fichier existe
ls -la client_secrets/client_secrets.json

# Si absent, créer dans Google Cloud Console
# Voir : doc/AUTH_GUIDE.md
```

---

### Vérifier sessions.yml

```bash
# Voir les sessions configurées
cat sessions.yml

# Doit afficher au moins une session avec refreshToken
```

---

## Variables d'Environnement

### newpipe-gradle

```bash
# Optionnel : Chemin vers sessions.yml
export NEWPIPE_SESSIONS_PATH="/chemin/vers/sessions.yml"

# Optionnel : Chemin vers output
export NEWPIPE_OUTPUT_PATH="/chemin/vers/downloads"
```

---

### mp3-organizer

```bash
# Requis : Database credentials
export DB_HOST="localhost"
export DB_PORT="5432"
export DB_NAME="mp3_organizer"
export DB_USER="postgres"
export DB_PASSWORD="secret"

# Optionnel : Ollama configuration
export OLLAMA_BASE_URL="http://localhost:11434"
export OLLAMA_MODEL="gemma4:e4b-it-q4_K_M"
```

---

## Voir Aussi

- **Guide d'authentification** : `doc/AUTH_GUIDE.md`
- **Architecture technique** : `doc/ARCHITECTURE.md`
- **Structure des projets** : `doc/PROJECT_STRUCTURE.md`
- **Démarrage rapide** : `doc/QUICK_START.md`

---

**Dernière mise à jour** : 2026-04-14
