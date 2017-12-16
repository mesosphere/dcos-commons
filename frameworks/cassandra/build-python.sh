#!/bin/bash

set -e

# Creates a self-contained build of Python for inclusion on Cassandra nodes.
# A version of python must be available in order to invoke `cqlsh`, `aws`, and `az`.

VERSION=2.7.14
DIR_PREFIX="python-dist" # NOTE: also referenced in `svc.yml`

PYTHON_DIR="Python-${VERSION}"
PYTHON_TGZ_FILE="${PYTHON_DIR}.tgz"
TGZ_URL="https://www.python.org/ftp/python/${VERSION}/${PYTHON_TGZ_FILE}"
OUTPUT_TGZ_FILE="python-${VERSION}.tar.gz"

if [ "$(uname)" != "Linux" ]; then
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
# TODO?: ./configure LDFLAGS="-static" --disable-shared
./configure --enable-optimizations --prefix=/${DIR_PREFIX}

# run build using numcores, or THREADS if provided
if [ -z "$THREADS" ]; then
    THREADS=$(grep -P '^vendor_id' /proc/cpuinfo | wc -l)
fi
echo "Building with $THREADS threads..."
# TODO?: make LDFLAGS="-static" LINKFORSHARED=" "
make -j${THREADS}

# 'install' to Python-<VER>/python-dist/ (combination of DESTDIR+DIR_PREFIX) and make a tarball from that
DESTDIR=. make install

# Remove test/ directory as it just contains files for testing python itself.
# This saves around 6.7MB (20%) in the tarball.
rm -rf ${DIR_PREFIX}/lib/python*/test/

tar czf $OUTPUT_TGZ_FILE $DIR_PREFIX
echo "Build complete. Upload and update URL in resource.json: $(pwd)/$OUTPUT_TGZ_FILE"
