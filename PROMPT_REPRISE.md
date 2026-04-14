# 🔄 Prompt de Reprise de Session - NewPipe Gradle

**À copier-coller au début d'une nouvelle session OpenCode**

---

## Contexte du Projet

Je travaille sur **newpipe-gradle**, un plugin Gradle qui télécharge de la musique depuis YouTube avec authentification OAuth2.

**Localisation** : `/home/cheroliv/workspace/__repositories__/newpipe-gradle`

---

## État Actuel du Projet

### ✅ Ce qui est implémenté
- **AuthSessionTask.kt** : OAuth2 Device Flow pour authentifier les comptes Google
- **SessionManager.kt** : Round-Robin entre sessions authentifiées
- **DownloadMusicTask.kt** : Téléchargement avec support multi-sessions
- **Documentation complète** : 8 fichiers de documentation à la racine

### ⏳ En cours / À faire
- **US-3** : Refresh automatique des tokens expirés
- **US-4** : Gestion des erreurs d'authentification
- **US-7** : Tests d'intégration avec vrais comptes

**Voir** : `EPIC_DOWNLOAD_AUTHENTICATED.md` pour le détail des 8 user-stories

---

## Comment Reprendre le Travail

### Option 1 : Continuer sur l'Authentification (Priorité 1)

```bash
cd /home/cheroliv/workspace/__repositories__/newpipe-gradle

# Vérifier l'existant
cat EPIC_DOWNLOAD_AUTHENTICATED.md | grep "US-3\|US-4"

# Commencer US-4 (Gestion des erreurs) - 3 points
# Ou US-3 (Refresh automatique) - 5 points
```

**Fichiers à modifier** :
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/SessionManager.kt`
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/TokenRefresher.kt` (à créer)

---

### Option 2 : Migration IA (Priorité 2)

```bash
# Voir la matrice de migration
cat MIGRATION_MATRIX.md

# Commencer par Version Catalog
cp /home/cheroliv/Musique/abdo/mp3-organizer/gradle/libs.versions.toml \
   /home/cheroliv/workspace/__repositories__/newpipe-gradle/gradle/
```

**Fichiers à créer** :
- `gradle/libs.versions.toml`
- `newpipe-plugin/newpipe/src/main/kotlin/com/cheroliv/newpipe/ai/LlmClient.kt`

---

### Option 3 : Tests (Consolidation)

```bash
# Vérifier les tests existants
ls -la newpipe-plugin/newpipe/src/test/

# Exécuter les tests
./gradlew test

# Voir la stratégie de tests
cat TESTING_STRATEGY.md
```

---

## Fichiers de Référence

| Besoin | Fichier |
|--------|---------|
| **Architecture technique** | `ARCHITECTURE.md` |
| **Structure des projets** | `PROJECT_STRUCTURE.md` |
| **Guide authentification** | `AUTH_GUIDE.md` |
| **User-stories détaillées** | `EPIC_DOWNLOAD_AUTHENTICATED.md` |
| **Commandes disponibles** | `COMMANDS.md` |
| **Stratégie de tests** | `TESTING_STRATEGY.md` |
| **Migration à faire** | `MIGRATION_MATRIX.md` |
| **Contexte IA (opencode)** | `AGENT.md` |

---

## Commandes Utiles pour Démarrer

```bash
# Vérifier que tout est en ordre
git status

# Voir les dernières modifications
git log --oneline -5

# Vérifier les sessions configurées
cat sessions.yml 2>/dev/null || echo "Aucune session configurée"

# Vérifier les secrets OAuth
ls -la client_secrets/ 2>/dev/null || echo "Aucun secret OAuth"
```

---

## Pièges à Éviter

1. ⚠️ **Ne jamais commit** `client_secrets/` ou `sessions.yml`
2. ⚠️ **Vérifier Ollama** avant tests IA : `ollama list | grep gemma4`
3. ⚠️ **Consulter AGENT.md** pour le contexte IA complet
4. ⚠️ **Ne pas overlaper** avec AGENT.md (ce fichier est un complément)

---

## Exemple de Prompt pour OpenCode

```
Je veux continuer le travail sur l'EPIC Download Authenticated.

Contexte :
- Projet : /home/cheroliv/workspace/__repositories__/newpipe-gradle
- User-stories : Voir EPIC_DOWNLOAD_AUTHENTICATED.md
- État actuel : US-1 et US-2 sont ✅ FAITES
- Prochaine tâche : US-4 (Gestion des erreurs d'authentification)

Peux-tu m'aider à :
1. Implémenter la gestion des erreurs OAuth2
2. Ajouter des messages utilisateur clairs
3. Créer les tests unitaires associés

Références :
- ARCHITECTURE.md pour les patterns
- TESTING_STRATEGY.md pour les tests
- AGENT.md pour le contexte IA complet
```

---

## Checklist de Fin de Session

À faire avant de quitter :

- [ ] Commiter les changements avec message conventionnel
- [ ] Mettre à jour `EPIC_DOWNLOAD_AUTHENTICATED.md` (statut des US)
- [ ] Noter les prochaines étapes dans ce fichier
- [ ] Vérifier que `client_secrets/` et `sessions.yml` ne sont pas stagés
- [ ] Push vers remote (si pertinent)

---

**Dernière mise à jour** : 2026-04-14  
**Prochaine session** : Commencer US-4 ou US-3
