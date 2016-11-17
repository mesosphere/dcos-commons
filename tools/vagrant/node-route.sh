#!/bin/bash

echo ">>> Configuring routing from host to DC/OS Master (sudo required)"
# "uname -a" samples:
# OSX: Darwin mesospheres-MacBook-Pro-2.local 15.6.0 Darwin Kernel Version 15.6.0: Mon Aug 29 20:21:34 PDT 2016; root:xnu-3248.60.11~1/RELEASE_X86_64 x86_64
# Linux: Linux augustao 4.2.0-42-generic #49-Ubuntu SMP Tue Jun 28 21:26:26 UTC 2016 x86_64 x86_64 x86_64 GNU/Linux
KERNEL=$(uname -a | awk '{print $1}')
if [ "$KERNEL" = "Linux" ]; then
    sudo ip route replace 172.17.0.0/16 via 192.168.65.50
elif [ "$KERNEL" = "Darwin" ]; then
    sudo route -nv add -net 172.17.0.0/16 192.168.65.50
else
    echo "Unknown kernel for route configuration: $KERNEL (from 'uname -a')"
fi
