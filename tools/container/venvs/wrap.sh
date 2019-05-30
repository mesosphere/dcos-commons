#!/bin/bash
set -eu

function usage() {
	echo "Usage: $0 <PIPENV_PROJECT> <COMMAND> [ ARGS ... ]" >&2
	echo "Where:" >&2
	echo "  PIPENV_PROJECT is the name of a directory under /venvs" >&2
	echo "  COMMAND and ARGS will be run with pipenv using the project" >&2
}

pipenv_project="$1"
shift
export PIPENV_PIPFILE="/venvs/${pipenv_project}/Pipfile"
if [[ $# == 0 || ! -e ${PIPENV_PIPFILE} ]]; then
	usage
fi

echo "Running following command using ${PIPENV_PIPFILE}:" "$@" >&2
exec pipenv run "$@"
