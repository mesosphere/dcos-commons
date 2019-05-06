#!/bin/bash -e

echo
echo KDC WEB ADMIN
echo =============
(while true; /kdc/server; sleep 1; done)&

echo
echo KDC LOGS
echo ========
exec /usr/lib/heimdal-servers/kdc --config-file=/etc/heimdal-kdc/kdc.conf --ports=$PORT_KDC
