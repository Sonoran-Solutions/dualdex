#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build
"${CC:-cc}" -std=c11 -D_POSIX_C_SOURCE=200809L -O2 -Wall -Wextra -fPIC -shared -I ../../native/include \
  probe_host.c ../../native/src/libretro_host.c ../../native/src/gba_memory_map.c \
  ../../native/src/pokemon_reader.c ../../native/src/pokemon_text.c \
  -ldl -lpthread -o build/probe_host.so
