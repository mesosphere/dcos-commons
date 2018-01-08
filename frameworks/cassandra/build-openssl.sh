#!/bin/bash

set -e

# Creates a self-contained build of OpenSSL for inclusion on Cassandra nodes.
# A version of openssl must be available in order to invoke `cqlsh`, which imports `hashlib` for MD5.
# As of Python 2.7.14, hashlib in turn requires `libcrypto.so.1.0.0` in order for imports to succeed:
# bash-4.2$ ldd _hashlib.so
#     linux-vdso.so.1 =>  (0x00007ffee4941000)
#     libcrypto.so.1.0.0 => not found
#     libpthread.so.0 => /lib64/libpthread.so.0 (0x00007f9459753000)
#     libc.so.6 => /lib64/libc.so.6 (0x00007f9459390000)
#     /lib64/ld-linux-x86-64.so.2 (0x00005594e8975000)

# libcrypto.so.1.0.0 (reqd by hashlib in Python 2.7) is only produced by LTS OpenSSL 1.0.x.
# Python versions beyond 2.7 may instead require OpenSSL 1.1.x?
VERSION=1.0.2n

DIR_PREFIX="openssl-dist" # NOTE: also referenced in `svc.yml`

OPENSSL_DIR="openssl-${VERSION}"
OPENSSL_TGZ_FILE="${OPENSSL_DIR}.tar.gz"
TGZ_URL="https://www.openssl.org/source/${OPENSSL_TGZ_FILE}"
OUTPUT_TGZ_FILE="openssl-${VERSION}-libs.tar.gz"

if [ "$(uname)" != "Linux" ]; then
    # Built artifacts need to be runnable within a Linux container.
    echo "This needs to be run on Linux."
    exit 1
fi

# download distribution if not already downloaded
if [ ! -f "${OPENSSL_TGZ_FILE}" ]; then
    curl -O $TGZ_URL
fi

# delete any preexisting build dir and unpack
rm -rf ${OPENSSL_DIR}/
tar xzf ${OPENSSL_TGZ_FILE}

cd ${OPENSSL_DIR}

# configure build with phony prefixes: we are not installing after we build
./config shared no-ssl2 no-ssl3 no-psk no-srp no-weak-ssl-ciphers --prefix=/do_not_install --openssldir=/do_not_install
make depend

# run build using numcores, or THREADS if provided
if [ -z "$THREADS" ]; then
    THREADS=$(grep -P '^vendor_id' /proc/cpuinfo | wc -l)
fi
echo "Building with $THREADS threads..."
make -j${THREADS}

LIBCRYPTO_FILE=$(ls libcrypto.so.*)
LIBSSL_FILE=$(ls libssl.so.*)

cd ..

# create directory to be packaged, containing only libcrypto.so
rm -rf $DIR_PREFIX/
mkdir -p $DIR_PREFIX/
cp ${OPENSSL_DIR}/${LIBCRYPTO_FILE} ${OPENSSL_DIR}/${LIBSSL_FILE} $DIR_PREFIX/
strip $DIR_PREFIX/${LIBCRYPTO_FILE} $DIR_PREFIX/${LIBSSL_FILE}
ln -s ${LIBCRYPTO_FILE} ${DIR_PREFIX}/libcrypto.so
ln -s ${LIBSSL_FILE} ${DIR_PREFIX}/libssl.so

rm -f $OUTPUT_TGZ_FILE
tar czvf $OUTPUT_TGZ_FILE $DIR_PREFIX

echo "Build complete. Upload result, then update URL in resource.json:"
echo "  aws s3 cp --acl public-read $(pwd)/${OUTPUT_TGZ_FILE} s3://YOURBUCKET/${OUTPUT_TGZ_FILE}"
