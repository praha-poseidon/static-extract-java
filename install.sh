#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN_DIR="${STATIC_EXTRACT_BIN_DIR:-${STATIC_EXTRACT_JAVA_BIN_DIR:-${JAVA_STATIC_EXTRACT_BIN_DIR:-$HOME/.local/bin}}}"
INSTALL_JAVA_CLI=1
INSTALL_TS_CLI=1
INSTALL_SKILLS=1
MVN_CMD=""

usage() {
  cat <<'USAGE'
Usage: ./install.sh [options]

Options:
  --bin-dir DIR       Install CLI commands into DIR.
  --no-cli            Do not build or install CLI commands.
  --no-java-cli       Do not build or install static-extract-java.
  --no-ts-cli         Do not install static-extract-ts.
  --no-skills         Do not install Codex/Claude skills.
  -h, --help          Show this help.

Environment:
  STATIC_EXTRACT_BIN_DIR           Default CLI install directory.
  STATIC_EXTRACT_JAVA_BIN_DIR      Legacy Java extractor CLI install directory.
  JAVA_STATIC_EXTRACT_BIN_DIR      Legacy alias for the Java CLI install directory.
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
      INSTALL_JAVA_CLI=0
      INSTALL_TS_CLI=0
      shift
      ;;
    --no-java-cli)
      INSTALL_JAVA_CLI=0
      shift
      ;;
    --no-ts-cli)
      INSTALL_TS_CLI=0
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

detect_maven() {
  if [[ -x "$ROOT_DIR/mvnw" ]]; then
    MVN_CMD="$ROOT_DIR/mvnw"
    return
  fi
  if command_exists mvn; then
    MVN_CMD="mvn"
    return
  fi
  die "Maven was not found. Install Maven, add it to PATH, or add a Maven wrapper as ./mvnw."
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

check_cli_prerequisites() {
  command_exists java || die "Java was not found. Install JDK 21 and make sure java is on PATH."
  command_exists javac || die "javac was not found. Install a full JDK 21, not only a JRE."
  detect_maven

  local major
  major="$(java_major_version javac || true)"
  if [[ -z "$major" || "$major" -lt 21 ]]; then
    die "JDK 21 or newer is required. Current javac version is: $(javac -version 2>&1)"
  fi
}

check_ts_prerequisites() {
  command_exists node || die "Node.js was not found. Install Node.js 20 or newer and make sure node is on PATH."

  local major
  major="$(node -p 'Number(process.versions.node.split(".")[0])' 2>/dev/null || true)"
  if [[ -z "$major" || "$major" -lt 20 ]]; then
    die "Node.js 20 or newer is required. Current node version is: $(node --version 2>&1)"
  fi
}

install_java_cli() {
  check_cli_prerequisites
  echo "Building static-extract Java extractor CLI..."
  (cd "$ROOT_DIR" && "$MVN_CMD" -pl java/cli -am package)

  local source_bin="$ROOT_DIR/java/cli/target/appassembler/bin/static-extract-java"
  if [[ ! -x "$source_bin" ]]; then
    echo "CLI script was not generated: $source_bin" >&2
    exit 1
  fi

  mkdir -p "$BIN_DIR"
  if ! ln -sfn "$source_bin" "$BIN_DIR/static-extract-java" 2>/dev/null; then
    echo "Symlink failed. Copying the command script instead..."
    cp "$source_bin" "$BIN_DIR/static-extract-java"
    chmod +x "$BIN_DIR/static-extract-java"
  fi
  echo "Installed command: $BIN_DIR/static-extract-java"
}

install_ts_cli() {
  check_ts_prerequisites

  local source_bin="$ROOT_DIR/ts/cli/static-extract-ts.mjs"
  if [[ ! -f "$source_bin" ]]; then
    die "TS CLI script was not found: $source_bin"
  fi
  if [[ ! -d "$ROOT_DIR/ts/node_modules/ts-morph" || ! -d "$ROOT_DIR/ts/node_modules/antlr4ng" ]]; then
    command_exists npm || die "npm was not found. Source install needs npm to install TS extractor dependencies."
    echo "Installing static-extract-ts dependencies..."
    (cd "$ROOT_DIR/ts" && npm install)
  fi
  if [[ ! -f "$ROOT_DIR/ts/dist/extractor/antlr-ser-parser.js" ]]; then
    echo "Building static-extract-ts generated SER parser..."
    (cd "$ROOT_DIR/ts" && npm run build)
  fi

  chmod +x "$source_bin"
  mkdir -p "$BIN_DIR"
  if ! ln -sfn "$source_bin" "$BIN_DIR/static-extract-ts" 2>/dev/null; then
    echo "Symlink failed. Copying the command script instead..."
    cp "$source_bin" "$BIN_DIR/static-extract-ts"
    chmod +x "$BIN_DIR/static-extract-ts"
  fi
  echo "Installed command: $BIN_DIR/static-extract-ts"
}

install_skill_dir() {
  local target_root="$1"
  local skill_name="$2"
  local source_dir="$ROOT_DIR/skills/$skill_name"
  local target_dir="$target_root/$skill_name"

  if [[ ! -d "$source_dir" ]]; then
    die "Skill source directory was not found: $source_dir"
  fi
  mkdir -p "$target_root"
  rm -rf "$target_dir"
  cp -R "$source_dir" "$target_dir"
  echo "Installed skill: $target_dir"
}

install_skills() {
  local codex_dir="${CODEX_SKILLS_DIR:-$HOME/.codex/skills}"
  local claude_dir="${CLAUDE_SKILLS_DIR:-$HOME/.claude/skills}"

  install_skill_dir "$codex_dir" "static-extract"
  install_skill_dir "$codex_dir" "ser-author"
  install_skill_dir "$claude_dir" "static-extract"
  install_skill_dir "$claude_dir" "ser-author"
}

if [[ "$INSTALL_JAVA_CLI" -eq 1 ]]; then
  install_java_cli
fi

if [[ "$INSTALL_TS_CLI" -eq 1 ]]; then
  install_ts_cli
fi

if [[ "$INSTALL_SKILLS" -eq 1 ]]; then
  install_skills
fi

cat <<EOF

Done.

Try:
  static-extract-java --help
  static-extract-ts --help

If commands are not found, add this to your shell profile:
  export PATH="$BIN_DIR:\$PATH"
EOF
