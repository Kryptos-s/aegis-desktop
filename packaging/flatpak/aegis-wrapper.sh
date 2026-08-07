#!/bin/sh
# No core files: a core dump of a running Aegis is the vault key and every secret in it.
ulimit -c 0 2>/dev/null || true
exec /app/aegis/bin/aegis "$@"
