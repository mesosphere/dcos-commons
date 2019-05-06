#!/bin/bash -e

echo
echo KDC LOGS
echo ========
(while true; do /kdc/api-server; sleep 1; done)&
exec /usr/lib/heimdal-servers/kdc --config-file=/etc/heimdal-kdc/kdc.conf --ports=$PORT_KDC
