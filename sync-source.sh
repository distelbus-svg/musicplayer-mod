#!/usr/bin/env bash
set -euo pipefail

# sync-source.sh — Copy a source file from 26.1.2 to all backport versions.
#
# Usage:
#   ./sync-source.sh <relative-path>
#
# Example:
#   ./sync-source.sh src/main/java/com/musicplayer/AudioEngine.java
#
# The file is copied to every Backports/<version>/<relative-path> that
# already has a matching file.  New files are NOT created — you must
# first add the file to each backport manually if it doesn't exist.

SRC="26.1.2/$1"

if [ ! -f "$SRC" ]; then
    echo "ERROR: source file not found: $SRC"
    exit 1
fi

echo "Syncing $SRC → Backports/*/$1"
echo "---"

for TGT in Backports/*/"$1"; do
    if [ -f "$TGT" ]; then
        cp "$SRC" "$TGT"
        echo "  OK  $TGT"
    else
        echo "  SKIP (no such file)"
    fi
done

echo "---"
echo "Done."
