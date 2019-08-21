#!/usr/bin/env bash

set -euxo pipefail

SCRIPT_FILE="$(basename "${BASH_SOURCE[@]}")"
SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DCOS_COMMONS_DIRECTORY="$(cd "${SCRIPT_DIRECTORY}/../../.." && pwd)"

function was_run_from_submodule () {
  ! (cd "${DCOS_COMMONS_DIRECTORY}" && test -d .git)
}

if was_run_from_submodule; then
  if [ -z "${FRAMEWORK}" ]; then
    echo "${SCRIPT_FILE} depends on \$FRAMEWORK environment variable to be set if run in the context of a project"
    echo "Please export \$FRAMEWORK"
    exit 1
  fi
  PROJECT_DIRECTORY="$(cd "${DCOS_COMMONS_DIRECTORY}/.." && pwd)"
  PROJECT_ARGUMENTS="${FRAMEWORK} --project ${PROJECT_DIRECTORY}"
else
  PROJECT_DIRECTORY="${DCOS_COMMONS_DIRECTORY}"
  PROJECT_ARGUMENTS=""
fi

DOCKER_TAG="${DOCKER_TAG:-latest}"
DOCKER_IMAGE="${DOCKER_IMAGE:-mesosphere/dcos-commons:${DOCKER_TAG}}"
# shellcheck disable=SC2124,SC2089
# https://github.com/koalaman/shellcheck/wiki/SC2124
# https://github.com/koalaman/shellcheck/wiki/SC2089
DOCKER_COMMAND="bash -c \"
  set -x;
  pre-commit run --verbose ${*}
\""

export DOCKER_IMAGE
# shellcheck disable=SC2090
export DOCKER_COMMAND

exec "${DCOS_COMMONS_DIRECTORY}/run_container.sh" --headless ${PROJECT_ARGUMENTS}
