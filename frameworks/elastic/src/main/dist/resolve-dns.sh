#!/usr/bin/env bash

if [ -z "$1" ]; then
    echo "No hostname argument provided for resolve-dns.sh"
    exit
fi
DNS_ADDRESS=$1
echo "Checking availability of Mesos DNS for $DNS_ADDRESS..."
while [ -z `dig +short ${DNS_ADDRESS}` ]; do
    echo "Waiting for $DNS_ADDRESS to resolve..."
    sleep 1
done
echo "Resolved name: $DNS_ADDRESS"