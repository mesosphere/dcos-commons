#!/usr/bin/env bash

# This script sets up a Python virtualenv for the Framework tests.
# This creates a new virtualenv and installs the necessary Python
# dependencies inside the virtualenv.

set -e
trap "exit 1" INT

CURRDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

: ${FRAMEWORK_NAME:="dcos-commons"}

: ${VIRTUALENV_NAME:="${FRAMEWORK_NAME}-tests"}
: ${VIRTUALENV_DIRECTORY:="${CURRDIR}/.virtualenv"}

: ${PYTHON:="$(which python3)"}

# If we already have a virtual environment activated,
# bail out and advise the user to deactivate.
if [ "${VIRTUAL_ENV}" != "" ]; then
  echo "Please deactivate your current virtual environment in order to continue!"
  echo "    $ deactivate"
  exit 1
fi

# Verify that python is installed and is a compatible version.
if [ "${PYTHON}" = "" ]; then
  echo "You must have python installed in order to continue."
  exit 1
fi

PYTHON_MAJOR=$(${PYTHON} -c 'import sys; print(sys.version_info[0])')
PYTHON_MINOR=$(${PYTHON} -c 'import sys; print(sys.version_info[1])')

if [ "${PYTHON_MAJOR}" != "3" ] || [ "${PYTHON_MINOR}" -lt "6" ]; then
  echo "You must be running python 3.6 or newer in order to continue."
  echo "Consider running as 'PYTHON=python3.6 ./bootstrap.sh' or similar."
  exit 1
fi

# Set up a virtual environment the framework tests.
${PYTHON} -m venv --clear --prompt="${VIRTUALENV_NAME}" ${VIRTUALENV_DIRECTORY}

source ${VIRTUALENV_DIRECTORY}/bin/activate
pip install --upgrade pip
python -m pip install -r ${CURRDIR}/requirements.txt
deactivate

echo ""
echo "Setup complete!"
echo ""
echo "To begin working, simply activate your virtual"
echo "environment and deactivate it when you are done."
echo ""

if [ "${VIRTUALENV_DIRECTORY}" = "${CURRDIR}/.virtualenv" ]; then
  # Print some info about the sucess of the installation.
  echo "    $ source activate"
  echo "    $ ..."
  echo "    $ deactivate"
  echo ""
fi
