# 🚀 Guide de démarrage rapide

## Installation en 3 étapes

### 1️⃣ Vérifier les prérequis

```bash
# Vérifier Java (requis Java 11+)
java -version

# Vérifier FFmpeg (optionnel mais recommandé)
ffmpeg -version
```

**Installer FFmpeg si nécessaire:**
- **Ubuntu/Debian:** `sudo apt-get install ffmpeg`
- **macOS:** `brew install ffmpeg`
- **Windows:** Télécharger depuis [ffmpeg.org](https://ffmpeg.org/download.html)

### 2️⃣ Builder le projet

```bash
# Première fois uniquement
./gradlew build
```

### 3️⃣ Télécharger votre première vidéo

```bash
./gradlew downloadMusic --url="https://www.youtube.com/watch?v=dQw4w9WgXcQ"
```

Le fichier MP3 sera créé dans `./downloads/`

---

## 📝 Commandes essentielles

### Téléchargement basique
```bash
./gradlew downloadMusic --url="URL_YOUTUBE"
```

### Avec dossier personnalisé
```bash
./gradlew downloadMusic --url="URL_YOUTUBE" --output="./ma_musique"
```

### Script interactif
```bash
./example-usage.sh
```

---

## 🎯 Résultat attendu

```
============================================================
YouTube MP3 Downloader
============================================================
URL: https://www.youtube.com/watch?v=dQw4w9WgXcQ
Destination: /home/user/downloads
============================================================

[1/4] Extraction des informations de la vidéo...
✓ Titre: Rick Astley - Never Gonna Give You Up
✓ Artiste: Rick Astley
✓ Durée: 03:33

[2/4] Sélection du meilleur flux audio...
✓ Format: M4A
✓ Bitrate: 128 kbps

[3/4] Téléchargement de l'audio...
  Progression: 100% (5.12 MB / 5.12 MB)
✓ Téléchargement terminé: 5 MB

[4/4] Conversion en MP3 et ajout des métadonnées...
✓ Conversion terminée

============================================================
✓ SUCCÈS!
Fichier: /home/user/downloads/Rick Astley - Never Gonna Give You Up.mp3
Taille: 5 MB
============================================================
```

---

## ❓ Problèmes courants

### "FFmpeg non disponible"
➜ Le fichier sera téléchargé en format d'origine (M4A/WebM) sans conversion MP3
➜ Installez FFmpeg pour activer la conversion

### "Unable to extract video info"
➜ Vérifiez que l'URL est valide
➜ La vidéo peut être privée ou géo-restreinte

### Erreur de build Gradle
```bash
# Nettoyer et rebuilder
./gradlew clean build
```

---

## 🎓 Prochaines étapes

1. **Lire le [README.md](README.md)** pour la documentation complète
2. **Personnaliser** le bitrate dans `Mp3Converter.kt`
3. **Contribuer** avec de nouvelles fonctionnalités

Bon téléchargement! 🎵
