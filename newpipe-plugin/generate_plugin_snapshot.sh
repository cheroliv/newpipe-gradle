#!/usr/bin/env bash
# generate_plugin_snapshot.sh
# Génère un fichier AsciiDoc listant les sources du plugin

set -euo pipefail

PLUGIN_DIR="${1:-.}"
OUTPUT_FILE="${2:-plugin_snapshot.adoc}"

cd "$PLUGIN_DIR"
PROJECT_ROOT=$(pwd)

cat > "$OUTPUT_FILE" << 'HEADER'
= Plugin Source Snapshot
:toc:
:toclevels: 3
:source-highlighter: highlight.js

HEADER

# ── Fonction pour ajouter un fichier au snapshot ──────────────────────────────
append_file() {
    local filepath="$1"
    local relpath="${filepath#$PROJECT_ROOT/}"
    local extension="${filepath##*.}"

    # Détermination du langage pour la coloration syntaxique
    local lang
    case "$extension" in
        kts|kt)  lang="kotlin" ;;
        toml)    lang="toml"   ;;
        adoc)    lang="asciidoc" ;;
        yml|yaml)lang="yaml"   ;;
        *)       lang="text"   ;;
    esac

    cat >> "$OUTPUT_FILE" << SECTION
== $relpath

[source,$lang]
----
$(cat "$filepath")
----

SECTION
}

# ── build.gradle.kts ──────────────────────────────────────────────────────────
[ -f "$PROJECT_ROOT/build.gradle.kts" ] \
    && append_file "$PROJECT_ROOT/build.gradle.kts"

# ── settings.gradle.kts ───────────────────────────────────────────────────────
[ -f "$PROJECT_ROOT/settings.gradle.kts" ] \
    && append_file "$PROJECT_ROOT/settings.gradle.kts"

# ── gradle/*.toml ─────────────────────────────────────────────────────────────
for f in "$PROJECT_ROOT"/gradle/*.toml; do
    [ -f "$f" ] && append_file "$f"
done

# ── src/**/* ──────────────────────────────────────────────────────────────────
# ── Recherche récursive de tous les dossiers src ──────────────────────────────
# On cherche tous les fichiers dans n'importe quel dossier "src",
# en excluant les dossiers de compilation (build) pour éviter les doublons.
while IFS= read -r -d '' f; do
    # Vérifie que c'est un fichier et pas un logback-test
    if [ -f "$f" ] && [[ "$f" != *"logback-test.xml" ]]; then
        append_file "$f"
    fi
done < <(find "$PROJECT_ROOT" -type d \( -name "build" -o -name ".gradle" -o -name ".git" \) -prune -o -path "*/src/*" -type f -print0 | sort -z)

echo "✔ Snapshot généré : $OUTPUT_FILE"