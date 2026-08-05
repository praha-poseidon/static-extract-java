#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="${1:-}"

if [[ -z "$VERSION" ]]; then
  VERSION="$(sed -n '0,/<version>\([^<]*\)<\/version>/s//\1/p' "$ROOT_DIR/pom.xml")"
fi
if [[ -z "$VERSION" ]]; then
  echo "ERROR: Could not determine project version." >&2
  exit 1
fi

DIST_DIR="$ROOT_DIR/dist"
WORK_DIR="$ROOT_DIR/target/release"
PACKAGE_NAME="static-extract-java-$VERSION"
PACKAGE_DIR="$WORK_DIR/$PACKAGE_NAME"

command -v zip >/dev/null 2>&1 || { echo "ERROR: zip not found" >&2; exit 1; }

echo "Building Java CLI distribution..."
(cd "$ROOT_DIR" && mvn -B -pl cli -am package)

echo "Assembling release package: $PACKAGE_NAME"
rm -rf "$PACKAGE_DIR"
mkdir -p "$PACKAGE_DIR/bin" "$DIST_DIR"

if [[ -d "$ROOT_DIR/cli/target/appassembler" ]]; then
  cp -R "$ROOT_DIR/cli/target/appassembler/." "$PACKAGE_DIR/"
fi

(cd "$WORK_DIR" && zip -qr "$DIST_DIR/$PACKAGE_NAME.zip" "$PACKAGE_NAME")
echo "Wrote $DIST_DIR/$PACKAGE_NAME.zip"
