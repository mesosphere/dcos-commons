#!/bin/bash
set -euo pipefail
set -x

echo "proxy-lite: STARTING" >&2

if [ ! -n "${BACKENDS-}" ]; then
  echo 'Must define $BACKENDS' >&2
  exit 1
fi

HAPROXYCFG="/proxy-lite/haproxy.cfg"
RAWHAPROXYCFG="${HAPROXYCFG}.raw"

echo "proxy-lite: SKIPPING CONFIGURATION" >&2
#python3 /proxy-lite/configure.py "$RAWHAPROXYCFG" "$HAPROXYCFG" "$PORT_PROXY" "${BACKENDS}"

echo "proxy-lite: WROTE CONFIGURATION" >&2

exec "$(which haproxy-systemd-wrapper)" -p /run/haproxy.pid -f "$HAPROXYCFG"
