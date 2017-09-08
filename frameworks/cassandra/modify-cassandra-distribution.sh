#!/usr/bin/env bash

# This script downloads a Cassandra distribution and adds the extra statsd jars we rely on.

# Download the user supplied distro
if [ "$#" -ne 1 ]; then
    echo "Usage: ./modify-cassandra-distribution.sh <url of cassandra distribution>"
    exit 1
fi

# Check to see if we should use gsed or sed
SED=$([ "$(uname)" == "Darwin" ] && echo "$(which gsed)" || echo "$(which sed)")
if [ -z "$SED" ]; then
    echo "Please install GNU sed by running brew install gnu-sed"
    exit 1
fi

URL=$1
FILE=`basename $URL`
REPLACE=""
UNPACKED_FILE="${FILE/-bin.tar.gz/$REPLACE}"

echo "Proceeding with:"
echo "url: $URL"
echo "file: $FILE"
echo "unpacked file: $UNPACKED_FILE"

echo $UNPACKED_FILE

`wget $URL -O /tmp/$FILE`

`tar -zxvf /tmp/$FILE -C /tmp`

echo "Removing old versions of the ReporterConfig library..."

`rm /tmp/$UNPACKED_FILE/lib/reporter-config*`

echo "Copying over the additional libraries..."

`cp lib/* /tmp/$UNPACKED_FILE/lib/`

echo "Removing extra config files..."

`rm /tmp/$UNPACKED_FILE/conf/cassandra-rackdc.properties`
`rm /tmp/$UNPACKED_FILE/conf/cassandra-topology.properties`

echo "Modifying cassandra-env.sh"

$SED -i "s/\(^JMX_PORT=.*\)/#DISABLED FOR DC\/OS:\n#\1/g" "/tmp/$UNPACKED_FILE/conf"/cassandra-env.sh

echo "Generating binary..."

cd /tmp

`tar -cvzf $UNPACKED_FILE-bin-dcos.tar.gz $UNPACKED_FILE`

echo "Cleaning up..."
`rm -rf /tmp/$UNPACKED_FILE/`
`rm /tmp/$FILE`

echo "All done. Use: /tmp/$UNPACKED_FILE-bin-dcos.tar.gz"




