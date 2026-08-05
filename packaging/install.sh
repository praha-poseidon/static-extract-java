#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN_DIR="${STATIC_EXTRACT_BIN_DIR:-$HOME/.local/bin}"
INSTALL_CLI=1
INSTALL_SKILLS=1

usage() {
  cat <<'USAGE'
Usage: ./install.sh [options]

Options:
  --bin-dir DIR       Install CLI commands into DIR.
  --no-cli            Do not install CLI commands.
  --no-skills         Do not install Codex/Claude skills.
  -h, --help          Show this help.

Environment:
  STATIC_EXTRACT_BIN_DIR           Default CLI install directory.
  CODEX_SKILLS_DIR                 Default: ~/.codex/skills
  CLAUDE_SKILLS_DIR                Default: ~/.claude/skills
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --bin-dir)
      if [[ $# -lt 2 ]]; then
        echo "Missing value for --bin-dir" >&2
        usage >&2
        exit 2
      fi
      BIN_DIR="$2"
      shift 2
      ;;
    --no-cli)
      INSTALL_CLI=0
      shift
      ;;
    --no-skills)
      INSTALL_SKILLS=0
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

die() {
  echo "ERROR: $*" >&2
  exit 1
}

command_exists() {
  command -v "$1" >/dev/null 2>&1
}

java_major_version() {
  local version
  version="$("$1" -version 2>&1 | sed -n 's/.*version "\([^"]*\)".*/\1/p' | head -n 1)"
  if [[ -z "$version" ]]; then
    version="$("$1" -version 2>&1 | awk '{print $2}' | head -n 1)"
  fi
  if [[ -z "$version" ]]; then
    return 1
  fi
  if [[ "$version" == 1.* ]]; then
    echo "$version" | cut -d. -f2
  else
    echo "$version" | cut -d. -f1
  fi
}

check_prerequisites() {
  command_exists java || die "Java was not found. Install JDK 21 or newer and make sure java is on PATH."
  command_exists node || die "Node.js was not found. Install Node.js 20 or newer and make sure node is on PATH."

  local java_major
  java_major="$(java_major_version java || true)"
  if [[ -z "$java_major" || "$java_major" -lt 21 ]]; then
    die "Java 21 or newer is required. Current java version is: $(java -version 2>&1 | head -n 1)"
  fi

  local node_major
  node_major="$(node -p 'Number(process.versions.node.split(".")[0])' 2>/dev/null || true)"
  if [[ -z "$node_major" || "$node_major" -lt 20 ]]; then
    die "Node.js 20 or newer is required. Current node version is: $(node --version 2>&1)"
  fi
}

install_command() {
  local name="$1"
  local source="$ROOT_DIR/bin/$name"

  if [[ ! -x "$source" ]]; then
    die "Command was not found in release package: $source"
  fi

  mkdir -p "$BIN_DIR"
  if ! ln -sfn "$source" "$BIN_DIR/$name" 2>/dev/null; then
    echo "Symlink failed for $name. Copying the command script instead..."
    cp "$source" "$BIN_DIR/$name"
    chmod +x "$BIN_DIR/$name"
  fi
  echo "Installed command: $BIN_DIR/$name"
}

install_skill() {
  local target_root="$1"
  local skill_name="$2"
  local source_dir="$ROOT_DIR/skills/$skill_name"
  local target_dir="$target_root/$skill_name"

  if [[ ! -d "$source_dir" ]]; then
    die "Skill source directory was not found in release package: $source_dir"
  fi
  mkdir -p "$target_root"
  rm -rf "$target_dir"
  cp -R "$source_dir" "$target_dir"
  echo "Installed skill: $target_dir"
}

if [[ "$INSTALL_CLI" -eq 1 ]]; then
  check_prerequisites
  install_command static-extract-java
  install_command static-extract-ts
fi

if [[ "$INSTALL_SKILLS" -eq 1 ]]; then
  install_skill "${CODEX_SKILLS_DIR:-$HOME/.codex/skills}" static-extract
  install_skill "${CODEX_SKILLS_DIR:-$HOME/.codex/skills}" ser-author
  install_skill "${CLAUDE_SKILLS_DIR:-$HOME/.claude/skills}" static-extract
  install_skill "${CLAUDE_SKILLS_DIR:-$HOME/.claude/skills}" ser-author
fi

cat <<EOF

Done.

Try:
  static-extract-java --help
  static-extract-ts --help

If commands are not found, add this to your shell profile:
  export PATH="$BIN_DIR:\$PATH"
EOF
