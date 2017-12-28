#!/bin/bash

set -e

# Creates a self-contained build of Python for inclusion on Cassandra nodes.
# A version of python must be available in order to invoke `cqlsh`, `aws`, and `az`.
# This build will contain the `aws` and `az` commands out of the box, as configured below.

VERSION=2.7.14
DIR_PREFIX="python-dist" # NOTE: also referenced in `svc.yml`
PIP_PACKAGES="awscli==1.14.11 azure-cli==2.0.22" # NOTE: used for backup/restore in `svc.yml`

PYTHON_DIR="Python-${VERSION}"
PYTHON_TGZ_FILE="${PYTHON_DIR}.tgz"
TGZ_URL="https://www.python.org/ftp/python/${VERSION}/${PYTHON_TGZ_FILE}"
OUTPUT_TGZ_FILE="python-${VERSION}.tar.gz"

if [ "$(uname)" != "Linux" ]; then
    # Built artifacts need to be runnable within a Linux container.
    echo "This needs to be run on Linux."
    exit 1
fi

# download distribution if not already downloaded
if [ ! -f "${PYTHON_TGZ_FILE}" ]; then
    curl -O $TGZ_URL
fi

# delete any preexisting build dir and unpack
rm -rf ${PYTHON_DIR}/
tar xzf ${PYTHON_TGZ_FILE}
cd ${PYTHON_DIR}

# configure build
# NOTE: For a static build, use './configure LDFLAGS="-static" --disable-shared ...'
./configure --enable-optimizations --prefix=/${DIR_PREFIX}

# run build using numcores, or THREADS if provided
if [ -z "$THREADS" ]; then
    THREADS=$(grep -P '^vendor_id' /proc/cpuinfo | wc -l)
fi
echo "Building with $THREADS threads..."
# NOTE: For a static build, use 'make LDFLAGS="-static" LINKFORSHARED=" " ...'
make -j${THREADS}

# 'install' to Python-<VER>/python-dist/ (combination of DESTDIR+DIR_PREFIX) and make a tarball from that
DESTDIR=. make install

# Remove test/ directory as it just contains files for testing python itself.
# This saves around 6.7MB (20%) in the tarball.
rm -rf ${DIR_PREFIX}/lib/python*/test/

if [ -n "${PIP_PACKAGES}" ]; then
    # Install pip/setuptools/wheel into python-dist:
    curl -O https://bootstrap.pypa.io/get-pip.py
    ${DIR_PREFIX}/bin/python get-pip.py

    # Use pip to install packages into python-dist:
    for PIP_PACKAGE in $PIP_PACKAGES; do
        echo "Installing ${PIP_PACKAGE}..."
        ${DIR_PREFIX}/bin/pip install --no-cache-dir ${PIP_PACKAGE}
    done

    # HACK: pip updates shebang statements in <python>/bin/* to use an ABSOLUTE, LOCAL path to the
    # Python build (/home/you/path/to/local/python...). Override this with "/usr/bin/env python"
    # so that it works outside of THIS machine.
    # With this hack, invoking bin scripts (e.g. 'aws') should work as expected assuming "python"
    # is in PATH. Note: this doesn't affect the 'az' command as that's technically a bash script.
    sed -i "s:$(pwd)/${DIR_PREFIX}/bin/python:/usr/bin/env python:g" ${DIR_PREFIX}/bin/*
fi

rm -f $OUTPUT_TGZ_FILE
tar czf $OUTPUT_TGZ_FILE $DIR_PREFIX

echo "Build complete. Upload result, then update URL in resource.json:"
echo "  aws s3 cp --acl public-read $(pwd)/${OUTPUT_TGZ_FILE} s3://YOURBUCKET/${OUTPUT_TGZ_FILE}"
