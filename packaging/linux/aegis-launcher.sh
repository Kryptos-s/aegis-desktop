#!/bin/sh
#
# Wrapper around the jpackage launcher that closes the two ways an unlocked vault leaks without
# anyone attacking it.
#
# A core dump of a running Aegis contains the master key and every OTP secret, written to wherever
# kernel.core_pattern points — often a world-readable directory, sometimes a crash reporter that
# uploads it. The JVM has no API for setrlimit, so the limit has to be set before it starts.
#
# ptrace_scope is the other one: with it set permissively, any process running as this user can
# attach to Aegis and read its memory. This cannot be changed per-process from userspace, so the
# script only reports it — the fix is a system setting, and telling the user is more useful than
# pretending.

set -eu

# No core files, for this process and everything it starts.
ulimit -c 0 2>/dev/null || true

if [ "${AEGIS_SKIP_HARDENING_CHECK:-0}" != "1" ] && [ -r /proc/sys/kernel/yama/ptrace_scope ]; then
    scope=$(cat /proc/sys/kernel/yama/ptrace_scope 2>/dev/null || echo 1)
    if [ "$scope" = "0" ]; then
        echo "aegis: kernel.yama.ptrace_scope is 0, so any process running as you can read this" >&2
        echo "aegis: application's memory, including your vault key. Consider setting it to 1:" >&2
        echo "aegis:   sudo sysctl -w kernel.yama.ptrace_scope=1" >&2
    fi
fi

dir=$(dirname "$(readlink -f "$0")")
exec "$dir/aegis-bin" "$@"
