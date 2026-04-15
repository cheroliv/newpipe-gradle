---
date: 2026-04-15
us: US-9
title: CLI Interactive - Tâche Gradle
status: in_progress
previous_us: ALL
---

# 🚀 US-9 : CLI Interactive (Tâche Gradle)

## 📋 Contexte

L'EPIC principale est **100% terminée** (37/37 points). Cette nouvelle US vise à améliorer l'expérience utilisateur avec une interface interactive.

## 🎯 Objectif

Créer une tâche Gradle interactive qui guide l'utilisateur dans l'utilisation du plugin.

---

## 📝 USER STORY

**En tant que** utilisateur du plugin  
**Je veux** une interface interactive en ligne de commande  
**Afin de** configurer et utiliser le plugin sans modifier manuellement les fichiers YAML

---

## ✅ Critères d'Acceptation

```bash
# Lancement de l'interface interactive
./gradlew newpipeInteractive

# Menu principal affiché :
╔══════════════════════════════════════════════════════════╗
║  NewPipe Gradle Plugin - Interface Interactive           ║
╠══════════════════════════════════════════════════════════╣
║  1. Configurer les sessions OAuth2                       ║
║  2. Lancer un téléchargement                             ║
║  3. Vérifier l'état des sessions                         ║
║  4. Ajouter une playlist                                 ║
║  5. Quitter                                              ║
╚══════════════════════════════════════════════════════════╝
→ Choisissez une option (1-5) :
```

### Scénarios de test

```gherkin
Scenario: Affichage du menu principal
  When je lance ./gradlew newpipeInteractive
  Then le menu principal est affiché
  And l'utilisateur peut choisir une option

Scenario: Configurer les sessions OAuth2
  When je choisis l'option 1
  Then le guide d'authentification est affiché
  And ./gradlew buildSessions est exécuté
  And ./gradlew authSessions est exécuté

Scenario: Lancer un téléchargement
  When je choisis l'option 2
  Then ./gradlew downloadMusic est exécuté
  And la progression est affichée

Scenario: Vérifier l'état des sessions
  When je choisis l'option 3
  Then ./gradlew sessionStatus est exécuté
  And l'état des sessions est affiché

Scenario: Ajouter une playlist
  When je choisis l'option 4
  Then l'URL de la playlist est demandée
  And la playlist est ajoutée à musics.yml
```

---

## 🔧 Tâches Techniques

- [ ] Créer `InteractiveCliTask.kt`
- [ ] Implémenter le menu principal (boucle interactive)
- [ ] Créer les sous-menus pour chaque option
- [ ] Gérer les entrées utilisateur (Scanner)
- [ ] Intégrer avec les tâches existantes
- [ ] Tests unitaires
- [ ] Documentation

---

## 📁 Fichiers à Créer/Modifier

| Fichier | Action | Description |
|---------|--------|-------------|
| `InteractiveCliTask.kt` | Créer | Tâche principale interactive |
| `InteractiveMenu.kt` | Créer | Gestion des menus et affichage |
| `NewpipeManager.kt` | Modifier | Enregistrer la tâche `newpipeInteractive` |
| `InteractiveCliTaskTest.kt` | Créer | Tests unitaires |

---

## 🎨 Maquette de l'Interface

### Menu Principal
```
╔══════════════════════════════════════════════════════════╗
║  NewPipe Gradle Plugin - Interface Interactive           ║
╠══════════════════════════════════════════════════════════╣
║  1. 🔐 Configurer les sessions OAuth2                    ║
║  2. 📥 Lancer un téléchargement                          ║
║  3. 📊 Vérifier l'état des sessions                      ║
║  4. 📝 Ajouter une playlist                              ║
║  5. ⚙️  Configuration                                    ║
║  6. ❓ Aide                                              ║
║  7. 🚪 Quitter                                           ║
╚══════════════════════════════════════════════════════════╝
→ Choisissez une option (1-7) :
```

### Sous-menu : Configurer les sessions
```
╔══════════════════════════════════════════════════════════╗
║  Configuration des Sessions OAuth2                       ║
╠══════════════════════════════════════════════════════════╣
║  1. Générer les fichiers client_secrets                  ║
║  2. Authentifier les comptes                             ║
║  3. Retour au menu principal                             ║
╚══════════════════════════════════════════════════════════╝
→ Choisissez une option (1-3) :
```

### Sous-menu : Télécharger
```
╔══════════════════════════════════════════════════════════╗
║  Options de Téléchargement                               ║
╠══════════════════════════════════════════════════════════╣
║  1. Télécharger toutes les playlists (downloadMusic)     ║
║  2. Télécharger une vidéo spécifique                     ║
║  3. Télécharger avec une session spécifique              ║
║  4. Retour au menu principal                             ║
╚══════════════════════════════════════════════════════════╝
→ Choisissez une option (1-4) :
```

---

## 📊 Estimation

**Points** : 5  
**Priorité** : 🟡 Moyenne  
**Complexité** : Moyenne  
**Risque** : Faible

---

## 📝 Notes

- Utiliser `java.util.Scanner` pour les entrées utilisateur
- Gérer proprement la fermeture du Scanner
- Messages colorés avec ANSI (optionnel)
- Compatible avec tous les terminaux
- Ne pas bloquer le processus Gradle

---

## 🔐 Sécurité

- Ne jamais afficher les tokens en clair
- Valider les URLs avant ajout
- Confirmer les actions critiques

---

**Dernière mise à jour** : 2026-04-15  
**Statut** : ⏳ À FAIRE
