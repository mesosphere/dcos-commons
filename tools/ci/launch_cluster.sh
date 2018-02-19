#!/bin/bash

LAUNCH_CONFIG_FILE=${1:-/build/config.yaml}
CLUSTER_INFO_FILE=${2:-/build/cluster_info.json}

set -e
LAUNCH_SUCCESS="False"
if [ x"$SECURITY" == x"strict" ]; then
    # For the time being, only try to relaunch a cluster on strict mode.
    # This is where we are alerting. If this is successful, then we can move
    # this to the other clusters.
    RETRY_LAUNCH="True"
else
    RETRY_LAUNCH="False"
fi

while [ x"$LAUNCH_SUCCESS" == x"False" ]; do
    dcos-launch create -c $LAUNCH_CONFIG_FILE
    if [ x"$RETRY_LAUNCH" == x"True" ]; then
        set +e
    else
        set -e
    fi
    dcos-launch wait 2>&1 | tee dcos-launch-wait-output.stdout

    # Grep exits with an exit code of 1 if no lines are matched. We thus need to
    # disable exit on errors.
    set +e
    ROLLBACK_FOUND=$(grep -o "Exception: StackStatus changed unexpectedly to: ROLLBACK_IN_PROGRESS" dcos-launch-wait-output.stdout)
    if [ -n "$ROLLBACK_FOUND" ]; then
        # This would be a good place to add some form of alerting!

        # We only retry once!
        RETRY_LAUNCH="False"
        set -e

        # We need to wait for the current stack to be deleted
        dcos-launch delete
        rm -f ${CLUSTER_INFO_FILE}
        echo "Cluster creation failed. Retrying after 30 seconds"
        sleep 30
    else
        LAUNCH_SUCCESS="True"
    fi
done
set -e

# Print the cluster info.
echo "Printing ${CLUSTER_INFO_FILE}..."
cat ${CLUSTER_INFO_FILE}
