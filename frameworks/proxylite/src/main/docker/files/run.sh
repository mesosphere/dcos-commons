#!/bin/bash
set -euo pipefail
set -x

SELFNAME="proxylite"
LOGNAME="[$SELFNAME $0]"
SRCDIR="/$SELFNAME"
MSANDBOX='/mnt/mesos/sandbox'

set +u
if [ -z ${OVERRIDE_CFG+x} ]; then
    ARG_OVERRIDE_CFG=false
else
    ARG_OVERRIDE_CFG=true
fi
if [ -z ${OVERRIDE_RAW_CFG+x} ]; then
    ARG_OVERRIDE_RAW_CFG=false
else
    ARG_OVERRIDE_RAW_CFG=true
fi
if [ -z ${NO_RUN_PROXY+x} ]; then
    ARG_NO_RUN_PROXY=false
else
    ARG_NO_RUN_PROXY=true
fi
set -u

if [ "$ARG_OVERRIDE_CFG" = "true" ]; then
    echo "$LOGNAME: OVERRIDING CONFIG" >&2
    HAPROXYCFG="$OVERRIDE_CFG"
else
    HAPROXYCFG="$SRCDIR/haproxy.cfg"
    if [ "$ARG_OVERRIDE_RAW_CFG" = "true" ]; then
        RAWHAPROXYCFG="$OVERRIDE_RAW_CFG"
    else
        RAWHAPROXYCFG="${HAPROXYCFG}.raw"
    fi
fi

echo "$LOGNAME: STARTING" >&2

# A hack to redirect syslog to stdout
ln -sf /proc/$$/fd/1 /var/log/container_stdout

echo "$LOGNAME: STARTING SYSLOG" >&2
/usr/sbin/syslogd -f "$SRCDIR/syslog.conf"

if [ "${ARG_OVERRIDE_CFG}" = "false" ]; then
    echo "$LOGNAME: CONFIGURING HAPROXY" >&2
    python3 "$SRCDIR/configure.py" \
        "$SELFNAME" \
        "$RAWHAPROXYCFG" \
        "$HAPROXYCFG" \
        "$PORT_PROXYLITE" \
        "$EXTERNAL_ROUTES" \
        "$INTERNAL_ROUTES" \
        "$ROOT_REDIRECT"

    if [ -d "$MSANDBOX" ]; then
        cp "$HAPROXYCFG" "$MSANDBOX"
    fi
fi

if [ "${ARG_NO_RUN_PROXY}" = "true" ]; then
    echo "$LOGNAME: SLEEPING FOREVER" >&2
    set +x
    while :; do sleep 0.5; done
    set -x
else
    echo "$LOGNAME: RUNNING HAPROXY" >&2
    "$(which haproxy-systemd-wrapper)" -p /run/haproxy.pid -f "$HAPROXYCFG"
fi
