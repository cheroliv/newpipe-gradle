# 🔄 Prompt de Reprise de Session - NewPipe Gradle

**À copier-coller au début d'une nouvelle session OpenCode**

---

## Contexte

Plugin Gradle pour télécharger de la musique YouTube avec authentification OAuth2.

**Projet** : `/home/cheroliv/workspace/__repositories__/newpipe-gradle`

---

## Backlog - État des User Stories

| US | Titre | Statut | Fichiers |
|----|-------|--------|----------|
| US-1 | Authentification Initiale | ✅ FAIT | AuthSessionTask.kt |
| US-2 | Téléchargement Authentifié | ✅ FAIT | DownloadMusicTask.kt, SessionManager.kt |
| US-3 | Refresh Automatique Tokens | ⏳ À FAIRE | TokenRefresher.kt (à créer) |
| US-4 | Gestion Erreurs Auth | ⏳ À FAIRE | AuthErrorHandler.kt (à créer) |
| US-5 | Vidéos 18+ | ⏳ À FAIRE | - |
| US-6 | Monitoring Sessions | ⏳ À FAIRE | SessionStatusTask.kt (à créer) |
| US-7 | Tests Intégration | ⏳ À FAIRE | YouTubeAuthIntegrationTest.kt (à créer) |
| US-8 | Playlists Privées | ⏳ À FAIRE | - |

**Voir** : `EPIC_DOWNLOAD_AUTHENTICATED.md` pour détails complets

---

## Prochaines Tâches

### Priorité 1 : US-4 (Gestion des erreurs d'authentification)

```bash
cd /home/cheroliv/workspace/__repositories__/newpipe-gradle

# Consulter la stratégie de tests
cat TESTING_STRATEGY.md

# Voir les tests existants
ls -la newpipe-plugin/newpipe/src/test/
```

**À faire** :
1. Créer `AuthErrorHandler.kt` pour mapper erreurs OAuth2 → messages utilisateur
2. Améliorer `DownloaderImpl.kt` pour intercepter HTTP 401/403
3. Créer tests Cucumber : `src/test/features/4_auth_errors.feature`

**Messages d'erreur à implémenter** :
- Token révoqué → `./gradlew authSessions`
- Token expiré → `./gradlew authSessions`
- client_secret invalide → `./gradlew buildSessions`
- Quota API dépassé → Bascule sur autre session
- Compte suspendu → Session désactivée

---

### Priorité 2 : US-3 (Refresh automatique des tokens)

**À faire** :
1. Ajouter `accessToken` et `accessTokenExpiry` dans `Session` (mémoire seulement)
2. Implémenter `refreshAccessToken()` dans `SessionManager`
3. Intercepter HTTP 401 dans `DownloaderImpl`
4. Retry automatique avec nouveau token

---

### Priorité 3 : Tests d'intégration (US-7)

**À faire** :
1. Créer tests taggués `@Tag("real-youtube")`
2. Utiliser vrais comptes de test
3. Variables d'environnement pour credentials

---

## Commandes Utiles

```bash
# Build & Test
./gradlew -p newpipe-plugin compileKotlin
./gradlew -p newpipe-plugin test
./gradlew -p newpipe-plugin functionalTest

# Authentification (nécessite client_secrets/)
./gradlew buildSessions
./gradlew authSessions
./gradlew downloadMusic
```

---

## Fichiers de Référence

| Besoin | Fichier |
|--------|---------|
| User-stories détaillées | `EPIC_DOWNLOAD_AUTHENTICATED.md` |
| Stratégie de tests | `TESTING_STRATEGY.md` |
| Guide authentification | `AUTH_GUIDE.md` |
| Architecture | `ARCHITECTURE.md` |
| Commandes | `COMMANDS.md` |

⚠️ **Ne pas consulter** : `AGENT.md` (contexte IA général - déjà connu)

---

## Pièges à Éviter

1. ⚠️ **Ne jamais commit** `client_secrets/` ou `sessions.yml`
2. ⚠️ **Vérifier Ollama** avant tests IA : `ollama list | grep gemma4`
3. ⚠️ **Projet indépendant** : Toujours utiliser `-p newpipe-plugin` pour Gradle

---

## Exemple de Prompt pour Démarrer

```
Je veux implémenter la US-4 (Gestion des erreurs d'authentification).

Contexte :
- Projet : /home/cheroliv/workspace/__repositories__/newpipe-gradle
- User-story : Voir EPIC_DOWNLOAD_AUTHENTICATED.md → US-4
- Tests : US-4.1 déjà faits (SessionManagerTest.kt)

Tâches :
1. Créer AuthErrorHandler.kt pour mapper erreurs OAuth2
2. Améliorer DownloaderImpl.kt pour gérer 401/403
3. Créer scénarios Cucumber dans src/test/features/4_auth_errors.feature

Références :
- TESTING_STRATEGY.md pour patterns de tests
- ARCHITECTURE.md pour architecture
```

---

## Checklist de Fin de Session

- [ ] Commiter avec message conventionnel
- [ ] Mettre à jour `EPIC_DOWNLOAD_AUTHENTICATED.md` (statut des US)
- [ ] Noter prochaines étapes ici
- [ ] Vérifier que `client_secrets/` et `sessions.yml` ne sont pas stagés (`git status`)
- [ ] Push vers remote

---

**Dernière mise à jour** : 2026-04-14  
**Session en cours** : US-4.1 (tests créés) → Reste US-4.2, US-4.3, US-4.4
