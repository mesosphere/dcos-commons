#!/bin/bash -e

sed -e "s/{{LOG_LEVEL}}/$LOG_LEVEL/g" -i /etc/heimdal-kdc/kdc.conf
sed -e "s/{{ENC_TYPES}}/$ENC_TYPES/g" -i /etc/heimdal-kdc/kdc.conf

nohup python -m SimpleHTTPServer 8000 &

echo
echo KDC LOGS
echo ========
exec /usr/lib/heimdal-servers/kdc --config-file=/etc/heimdal-kdc/kdc.conf --ports=$PORT_KDC
