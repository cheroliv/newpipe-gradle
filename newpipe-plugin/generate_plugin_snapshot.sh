#!/usr/bin/env bash
# generate_plugin_snapshot.sh

set -euo pipefail

PLUGIN_DIR="${1:-.}"
OUTPUT_FILE="${2:-plugin_snapshot.adoc}"

cd "$PLUGIN_DIR"
PROJECT_ROOT=$(pwd)

# ── 1. Génération de l'arborescence (Console + AsciiDoc) ──────────────────────
# On capture l'arborescence filtrée pour l'afficher partout
# On exclut les répertoires inutiles pour la clarté
TREE_VIEW=$(find . -type d \( -name "build" -o -name ".gradle" -o -name ".git" -o -name "node_modules" \) -prune -o -print | sed -e 's;[^/]*/;|____;g;s;____|; |;g')

echo "Structure du projet :"
echo "$TREE_VIEW"

# ── 2. Initialisation du fichier AsciiDoc ─────────────────────────────────────
cat > "$OUTPUT_FILE" << HEADER
= Plugin Source Snapshot
:toc:
:toclevels: 3
:source-highlighter: highlight.js

== Project Structure
[listing]
----
$TREE_VIEW
----

HEADER

# ── 3. Fonction pour ajouter un fichier ───────────────────────────────────────
append_file() {
    local filepath="$1"
    [ ! -f "$filepath" ] && return

    local relpath="${filepath#$PROJECT_ROOT/}"
    local extension="${filepath##*.}"

    local lang
    case "$extension" in
        kts|kt)     lang="kotlin" ;;
        toml)       lang="toml"   ;;
        adoc)       lang="asciidoc" ;;
        yml|yaml)   lang="yaml"   ;;
        properties) lang="properties" ;;
        *)          lang="text"   ;;
    esac

    cat >> "$OUTPUT_FILE" << SECTION
== $relpath

[source,$lang]
----
$(cat "$filepath")
----

SECTION
}

# ── 4. Collecte des fichiers selon tes critères ──────────────────────────────

# Étape A : Tous les fichiers dans les dossiers "src" (récursif)
while IFS= read -r -d '' f; do
    append_file "$f"
done < <(find "$PROJECT_ROOT" -path "*/src/*" -type f -print0 | sort -z)

# Étape B : Fichiers *.kts, *.yml, *.properties au même niveau que les dossiers "src"
# On cherche les dossiers "src", on regarde leurs parents, et on liste les fichiers spécifiques
while IFS= read -r -d '' src_dir; do
    parent_dir=$(dirname "$src_dir")

    # On cherche uniquement au premier niveau du parent (maxdepth 1)
    while IFS= read -r -d '' extra_file; do
        append_file "$extra_file"
    done < <(find "$parent_dir" -maxdepth 1 -type f \( -name "*.kts" -o -name "*.yml" -o -name "*.yaml" -o -name "*.properties" \) -print0)

done < <(find "$PROJECT_ROOT" -type d -name "src" -print0)

echo "✔ Snapshot généré avec succès : $OUTPUT_FILE"