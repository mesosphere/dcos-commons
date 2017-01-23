#!/bin/bash
set -euo pipefail
set -x

env

echo "Starting sidecar application"
/app/init.rb
