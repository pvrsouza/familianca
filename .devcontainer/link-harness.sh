#!/usr/bin/env bash
# Liga o harness global do Claude (skills, agents, commands, rules...) versionado no host
# (montado em ~/claude-harness) dentro de ~/.claude, que continua sendo um volume nomeado
# para credenciais, histórico e estado local. Idempotente: roda a cada start do container.
set -euo pipefail

HARNESS=/home/dev/claude-harness
CLAUDE_DIR=${CLAUDE_CONFIG_DIR:-/home/dev/.claude}

DIRS=(skills agents commands rules output-styles hooks)
FILES=(CLAUDE.md)

mkdir -p "$CLAUDE_DIR"

link() {
    local name=$1 kind=$2 src="$HARNESS/$1" dst="$CLAUDE_DIR/$1"
    # Se já existe algo real em ~/.claude e nada no harness, migra para o harness.
    if [ -e "$dst" ] && [ ! -L "$dst" ]; then
        if [ ! -e "$src" ]; then
            mv "$dst" "$src"
        else
            mv "$dst" "$dst.bak.$(date +%s)"
        fi
    fi
    if [ ! -e "$src" ]; then
        if [ "$kind" = dir ]; then mkdir -p "$src"; else touch "$src"; fi
    fi
    ln -sfn "$src" "$dst"
}

for d in "${DIRS[@]}"; do link "$d" dir; done
for f in "${FILES[@]}"; do link "$f" file; done
