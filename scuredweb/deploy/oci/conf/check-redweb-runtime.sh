#!/bin/sh
set -eu
# Reject unresolved/unbounded session cap without echoing environment values.
case " ${CATALINA_OPTS:-} " in
  *" -Dredweb.maxActiveSessions="*) ;;
  *) exit 1;;
esac
cap=$(printf '%s' "$CATALINA_OPTS" | sed -n 's/.*-Dredweb.maxActiveSessions=\([0-9][0-9]*\).*/\1/p')
case "$cap" in ''|*[!0-9]*) exit 1;; esac
[ "$cap" -gt 0 ]
