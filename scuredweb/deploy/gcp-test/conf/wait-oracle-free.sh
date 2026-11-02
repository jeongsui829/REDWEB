#!/bin/sh
set -eu

attempt=0
while [ "$attempt" -lt 60 ]; do
    health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' oracle-free 2>/dev/null || true)"
    if [ "$health" = "healthy" ]; then
        exit 0
    fi
    attempt=$((attempt + 1))
    sleep 5
done

echo "Oracle Free did not become healthy within 300 seconds." >&2
exit 1
