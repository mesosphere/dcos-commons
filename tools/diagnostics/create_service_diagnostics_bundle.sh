#!/usr/bin/env bash

set -eu -o pipefail

readonly SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
readonly DCOS_COMMONS_SCRIPT_PATH="tools/diagnostics"

function is_dcos_commons_repository_script() {
  [[ ${SCRIPT_DIRECTORY} = *${DCOS_COMMONS_SCRIPT_PATH} ]]
}

function is_development_mode() {
  is_dcos_commons_repository_script
}

if is_development_mode; then
  echo "dcos-commons repository detected, running in development mode"
  echo
  echo "In development mode all Python modules will be picked up from your current"
  echo "dcos-commons repository instead of the Docker image's /dcos-commons-dist "
  echo "directory that contains a static git checkout"
  echo
  set -x
  readonly CONTAINER_DCOS_COMMONS_DIRECTORY="/dcos-commons"
  readonly CONTAINER_DCOS_COMMONS_VOLUME_MOUNT="-v $(pwd):${CONTAINER_DCOS_COMMONS_DIRECTORY}:ro"
else
  readonly CONTAINER_DCOS_COMMONS_DIRECTORY="/dcos-commons-dist"
  # We don't mount the dcos-commons directory in the container because the
  # script will use /dcos-commons-dist which is added to the Docker image during
  # build time.
  readonly CONTAINER_DCOS_COMMONS_VOLUME_MOUNT=
fi

readonly VERSION='v0.1.0'

function version () {
  echo "${VERSION}"
}

if [ "${#}" -eq 1 ] && [[ "${1}" =~ ^(--version|-version|version|--v|-v)$ ]]; then
  version
  exit 0
fi

readonly REQUIREMENTS='docker'

for requirement in ${REQUIREMENTS}; do
  if ! [[ -x $(command -v "${requirement}") ]]; then
    echo "You need to install '${requirement}' to run this script"
    exit 1
  fi
done

readonly BUNDLES_DIRECTORY="service-diagnostic-bundles"
readonly DOCKER_IMAGE="mesosphere/dcos-commons:diagnostics-${VERSION}"
readonly SCRIPT_NAME="create_service_diagnostics_bundle.py"

readonly HOST_DCOS_CLI_DIRECTORY="${HOME}/.dcos"

readonly CONTAINER_BUNDLES_DIRECTORY="/${BUNDLES_DIRECTORY}"
readonly CONTAINER_SCRIPT_DIRECTORY="${CONTAINER_DCOS_COMMONS_DIRECTORY}/${DCOS_COMMONS_SCRIPT_PATH}"
readonly CONTAINER_SCRIPT_PATH="${CONTAINER_SCRIPT_DIRECTORY}/${SCRIPT_NAME}"
readonly CONTAINER_PYTHONPATH="${CONTAINER_DCOS_COMMONS_DIRECTORY}/testing:${CONTAINER_SCRIPT_DIRECTORY}"
readonly CONTAINER_DCOS_CLI_DIRECTORY_RO="/dcos-cli-directory"
readonly CONTAINER_DCOS_CLI_DIRECTORY="/root/.dcos"

mkdir -p "${BUNDLES_DIRECTORY}"

function container_run () {
  local -r command="${*:-}"
  docker run \
         -it \
         --rm \
         -v "$(pwd)/${BUNDLES_DIRECTORY}:${CONTAINER_BUNDLES_DIRECTORY}" \
         -v "${HOME}/.dcos:${CONTAINER_DCOS_CLI_DIRECTORY_RO}":ro \
         ${CONTAINER_DCOS_COMMONS_VOLUME_MOUNT} \
         "${DOCKER_IMAGE}" \
         bash -l -c "${command}"
}

function usage () {
  container_run "PYTHONPATH=${CONTAINER_PYTHONPATH} ${CONTAINER_SCRIPT_PATH} --help"
}

if [ "${#}" -eq 1 ] && [[ "${1}" =~ ^(--help|-help|help|--h|-h)$ ]]; then
  usage
  exit 0
fi

container_run "rm -rf ${CONTAINER_DCOS_CLI_DIRECTORY}
               cp -r ${CONTAINER_DCOS_CLI_DIRECTORY_RO} ${CONTAINER_DCOS_CLI_DIRECTORY}
               find ${CONTAINER_DCOS_CLI_DIRECTORY} \
                 -type f \
                 -exec sed -i -e 's|${HOST_DCOS_CLI_DIRECTORY}|${CONTAINER_DCOS_CLI_DIRECTORY}|g' {} +
               PYTHONPATH=${CONTAINER_PYTHONPATH} ${CONTAINER_SCRIPT_PATH} ${*} \
                 --bundles-directory ${CONTAINER_BUNDLES_DIRECTORY}"
