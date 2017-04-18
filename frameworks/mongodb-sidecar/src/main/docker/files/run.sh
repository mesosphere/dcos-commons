#!/bin/bash
set -euo pipefail
set -x

env

cd /app

echo "Starting sidecar application"
/app/sidecar.rb
