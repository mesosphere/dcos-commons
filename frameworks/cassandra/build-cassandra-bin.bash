#!/bin/bash

# DESCRIPTION:
#  Creates a customized apache-cassandra-VERSION-bin.tar.gz,
#  which includes additional libs that aren't present in the stock version.
# USAGE:
#  $ ./build-cassandra-bin.bash
#  [...]
#  Created: /path/to/dcos-cassandra-service/cassandra-bin-tmp/apache-cassandra-2.2.5-bin-dcos.tar.gz
#  Summary of lib/ changes:
#  [... diff between original apache-cassandra-x.y.z-bin.tar.gz and new apache-cassandra-x.y.z-bin-dcos.tar.gz ... ]
# CUSTOMIZATION:
#  See variables below, in particular CASSANDRA_VERSION for bumping the version of cassandra to package.

set -o errexit -o nounset -o pipefail

# Check to see if we should use gsed or sed
SED=$([ "$(uname)" == "Darwin" ] && echo "$(which gsed)" || echo "$(which sed)")
if [ -z "$SED" ]; then
    echo "Please install GNU sed by running brew install gnu-sed"
    exit 1
fi

# VERSION SETTINGS
CASSANDRA_VERSION="3.0.13"
METRICS_INTERFACE_VERSION="3" # Cassandra 2.2+ uses metrics3, while <= 2.1 uses metrics2.
SEED_PROVIDER_VERSION="1.0.30"
STATSD_REPORTER_VERSION="4.1.2-SNAPSHOT" # Custom version due to usage of metrics-core-3.1.0 interface in Cassandra 2.2+
REPORTER_CONFIG_VERSION="3.0.3"
READYTALK_MVN_REPO_DOWNLOAD_URL="https://dl.bintray.com/readytalk/maven/com/readytalk"
MVN_CENTRAL_DOWNLOAD_URL="https://repo1.maven.org/maven2"

# PATHS AND FILENAME SETTINGS
CASSANDRA_DIST_NAME="apache-cassandra-${CASSANDRA_VERSION}"
CASSANDRA_STOCK_IMAGE="${CASSANDRA_DIST_NAME}-bin.tar.gz"
CASSANDRA_CUSTOM_IMAGE="${CASSANDRA_DIST_NAME}-bin-dcos.tar.gz"
CASSANDRA_STOCK_IMAGE_DOWNLOAD_URL="https://archive.apache.org/dist/cassandra/${CASSANDRA_VERSION}/${CASSANDRA_STOCK_IMAGE}"

function _sha1sum {
    # Try 'sha1sum' (Linux) with fallback to 'shasum' (OSX)
    SHA1SUM_EXE=$(which sha1sum || which shasum)
    $SHA1SUM_EXE $@
}

function _download {
    if [ ! -f "$2" ]; then
        wget --progress=dot -e dotbytes=1M -O $2 $1
        _sha1sum $2
    fi
}

function _package_github {
    PROJECT_NAME=$(basename $1) # user/pkg => pkg
    if [ ! -f "$PROJECT_NAME/$3" ]; then
        echo "Building $PROJECT_NAME/$3 from $1:$2"
        if [ ! -d "$PROJECT_NAME" ]; then
            time git clone "https://github.com/$1" --branch "$2" --single-branch --depth 1  && cd "$PROJECT_NAME"
        else
            cd "$PROJECT_NAME"
        fi
        time ./gradlew build
        cd ..
    fi
}

# Build seed-provider jar
echo "##### Build seed-provider jar #####"
../../gradlew :cassandra-seed-provider:jar

###
# Go into tmp dir
###

mkdir -p "cassandra-bin-tmp"
cd "cassandra-bin-tmp"

###
# Download and unpack stock cassandra-bin and verify with downloaded sha1 file
###

_download $CASSANDRA_STOCK_IMAGE_DOWNLOAD_URL.sha1 "${CASSANDRA_STOCK_IMAGE}.sha1"
_download $CASSANDRA_STOCK_IMAGE_DOWNLOAD_URL "${CASSANDRA_STOCK_IMAGE}"
if [ "$(_sha1sum $CASSANDRA_STOCK_IMAGE | awk '{print $1}')" != "$(cat ${CASSANDRA_STOCK_IMAGE}.sha1)" ]; then
    echo "SHA1 MISMATCH: ${CASSANDRA_STOCK_IMAGE}"
    exit 1
else
    echo "Verified: ${CASSANDRA_STOCK_IMAGE}"
fi
rm -rf $CASSANDRA_DIST_NAME
tar xzf $CASSANDRA_STOCK_IMAGE

###
# Modify stock cassandra config
###

CONF_DIR="${CASSANDRA_DIST_NAME}/conf"
_sha1sum "${CONF_DIR}"/* &> confdir-before.txt || true

# Remove default cassandra-rackdc.properties and cassandra-topology.properties.
# The framework will provide this configuration on-the-fly.

echo "##### Remove cassandra-[rackdc|topology].properties #####"

rm -v "${CONF_DIR}"/cassandra-rackdc.properties
rm -v "${CONF_DIR}"/cassandra-topology.properties

# Make a copy of cassandra-env.sh named cassandra-env.sh.bak, then comment out any JMX_PORT
# configuration. This value is customized by the framework.

echo "##### Disable JMX_PORT in cassandra-env.sh #####"

# "JMX_PORT=???" => "#DISABLED FOR DC/OS\n#JMX_PORT=???"
$SED -i "s/\(^JMX_PORT=.*\)/#DISABLED FOR DC\/OS:\n#\1/g" "${CONF_DIR}"/cassandra-env.sh

_sha1sum "${CONF_DIR}"/* &> confdir-after.txt || true

LIB_DIR="${CASSANDRA_DIST_NAME}/lib"
_sha1sum "${LIB_DIR}"/*.jar &> libdir-before.txt || true

echo "##### Install custom seed provider #####"
cp -v ../seed-provider/build/libs/cassandra-seed-provider-${SEED_PROVIDER_VERSION}.jar ${LIB_DIR}

echo "##### Install StatsD metrics support #####"

# StatsD Reporter: custom build with newer version of metrics-core
METRICS_STATSD_JAR="metrics${METRICS_INTERFACE_VERSION}-statsd/build/libs/metrics${METRICS_INTERFACE_VERSION}-statsd-${STATSD_REPORTER_VERSION}.jar"
METRICS_STATSD_COMMON_JAR="metrics-statsd-common/build/libs/metrics-statsd-common-${STATSD_REPORTER_VERSION}.jar"
_package_github "mesosphere/metrics-statsd" "metrics-core-3.1.0" ${METRICS_STATSD_JAR}
cp -v "metrics-statsd/${METRICS_STATSD_JAR}" ${LIB_DIR}
cp -v "metrics-statsd/${METRICS_STATSD_COMMON_JAR}" ${LIB_DIR}

# Metrics Reporter Config: add stock libraries from maven repo
_download "${MVN_CENTRAL_DOWNLOAD_URL}/com/addthis/metrics/reporter-config${METRICS_INTERFACE_VERSION}/${REPORTER_CONFIG_VERSION}/reporter-config${METRICS_INTERFACE_VERSION}-${REPORTER_CONFIG_VERSION}.jar" "reporter-config${METRICS_INTERFACE_VERSION}-${REPORTER_CONFIG_VERSION}.jar"

_download "${MVN_CENTRAL_DOWNLOAD_URL}/com/addthis/metrics/reporter-config-base/${REPORTER_CONFIG_VERSION}/reporter-config-base-${REPORTER_CONFIG_VERSION}.jar" "reporter-config-base-${REPORTER_CONFIG_VERSION}.jar"

rm -vf "${LIB_DIR}"/reporter-config*.jar
cp -v \
   "reporter-config-base-${REPORTER_CONFIG_VERSION}.jar" \
   "${LIB_DIR}/reporter-config-base-${REPORTER_CONFIG_VERSION}.jar"
cp -v \
   "reporter-config${METRICS_INTERFACE_VERSION}-${REPORTER_CONFIG_VERSION}.jar" \
   "${LIB_DIR}/reporter-config${METRICS_INTERFACE_VERSION}-${REPORTER_CONFIG_VERSION}.jar"

_sha1sum "${LIB_DIR}"/*.jar &> libdir-after.txt || true

###
# Rebuild cassandra-bin package with config and library changes
###

rm -vf ${CASSANDRA_CUSTOM_IMAGE}
tar czf ${CASSANDRA_CUSTOM_IMAGE} ${CASSANDRA_DIST_NAME}

echo ""
echo "#####"
echo ""
echo "CREATED: $(pwd)/${CASSANDRA_CUSTOM_IMAGE}"
echo ""
pwd
_sha1sum ${CASSANDRA_DIST_NAME}*.tar.gz
ls -l ${CASSANDRA_DIST_NAME}*.tar.gz
echo ""
echo "Summary of conf/ changes:"
diff confdir-before.txt confdir-after.txt || true
echo ""
echo "Summary of lib/ changes:"
diff libdir-before.txt libdir-after.txt || true
