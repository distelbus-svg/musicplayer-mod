#!/usr/bin/env bash
set -euo pipefail

# sync-source.sh — Copy a source file from 26.3 to all other versions.
#
# Usage:
#   ./sync-source.sh <relative-path>
#
# Example:
#   ./sync-source.sh src/main/java/com/musicplayer/AudioEngine.java
#
# The file is copied to every other version directory that already has a
# matching file.  New files are NOT created — you must first add the file
# to each version manually if it doesn't exist.
#
# Note: 26.1.2 has a different screen API (Minecraft.setScreen vs
# Minecraft.gui.setScreen), so files with screen-related calls may need
# manual adjustment after sync.

SRC="26.3/$1"

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
