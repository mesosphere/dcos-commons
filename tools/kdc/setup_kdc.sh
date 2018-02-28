#!/bin/bash

# setup the database used by the KDC
/setup_kdc_db.sh

sed -i "s/PORT_KDC/$PORT_KDC/g" /kdc/kdc.conf
sed -i "s/PORT_KDC/$PORT_KDC/g; s/REALM/$REALM/g" /kdc/krb5.conf
krb5kdc
tail -f /var/log/krb5kdc.log
