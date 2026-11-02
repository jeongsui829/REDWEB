#!/bin/sh
set -eu
[ "$(docker inspect -f '{{.HostConfig.RestartPolicy.Name}}' oracle-free)" = no ] || exit 1
[ "$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/opt/oracle/oradata"}}{{.Source}}{{end}}{{end}}' oracle-free)" = /srv/redweb/oracle ] || exit 1
