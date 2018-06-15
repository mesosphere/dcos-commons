#!/bin/bash
# Run flake8 in a docker container.

TOOL_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

${TOOL_DIR}/run_check_in_docker.sh flake8 $*
