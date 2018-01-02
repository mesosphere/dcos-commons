#!/bin/bash -e

echo
echo KDC LOGS
echo ========
exec /usr/lib/heimdal-servers/kdc --config-file=/etc/heimdal-kdc/kdc.conf --ports=$PORT_KDC
