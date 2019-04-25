#!/bin/bash
# TODO: this is just a pass-through fow now, actual invocation of pipenv will
# be added after TeamCity configs are updated to use this file.
set -eu
function usage() {
	echo "Usage: $0 <PIPENV_PROJECT> <COMMAND> [ ARGS ... ]" >&2
	echo "Where:" >&2
	echo "  PIPENV_PROJECT is the name of a directory under /venvs" >&2
	echo "  COMMAND and ARGS will be run with pipenv using the project" >&2
}
pipenv_project="$1"
shift
if [[ $# == 0 ]]; then
	usage
fi
exec "$@"
