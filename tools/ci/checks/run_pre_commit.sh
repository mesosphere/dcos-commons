#!/usr/bin/env bash

set -euxo pipefail

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DCOS_COMMONS_DIRECTORY="$(cd "${SCRIPT_DIRECTORY}/../../.." && pwd)"

function was_run_from_submodule () {
  ! (cd "${DCOS_COMMONS_DIRECTORY}" && test -d .git)
}

CONTAINER_WORKDIR="/build"

if was_run_from_submodule; then
  PROJECT_DIRECTORY="$(cd "${DCOS_COMMONS_DIRECTORY}/.." && pwd)"
  PROJECT_ARGUMENTS="--project ${PROJECT_DIRECTORY}"
  CONTAINER_DCOS_COMMONS_DIRECTORY="/dcos-commons-git-repo"
  DOCKER_OPTIONS="-v ${PROJECT_DIRECTORY}/.git/modules/dcos-commons:${CONTAINER_DCOS_COMMONS_DIRECTORY}"
else
  PROJECT_DIRECTORY="${DCOS_COMMONS_DIRECTORY}"
  PROJECT_ARGUMENTS=""
  CONTAINER_DCOS_COMMONS_DIRECTORY="${CONTAINER_WORKDIR}"
  DOCKER_OPTIONS=""
fi

DOCKER_TAG="${DOCKER_TAG:-elastic-standalone}" # TODO: change back to "latest"
DOCKER_IMAGE="${DOCKER_IMAGE:-mesosphere/dcos-commons:${DOCKER_TAG}}"
# shellcheck disable=SC2124,SC2089
DOCKER_COMMAND="bash -c \"
  set -x;

  export PYTHONPATH=${CONTAINER_WORKDIR}/testing;
  export GIT_DIR=${CONTAINER_DCOS_COMMONS_DIRECTORY};
  export GIT_WORK_TREE=${CONTAINER_DCOS_COMMONS_DIRECTORY};

  pre-commit run ${@}
\""

export DOCKER_IMAGE
export DOCKER_OPTIONS
# shellcheck disable=SC2090
export DOCKER_COMMAND

exec "${DCOS_COMMONS_DIRECTORY}/test.sh" ${PROJECT_ARGUMENTS}
