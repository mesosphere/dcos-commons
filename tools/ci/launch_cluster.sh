#!/bin/bash

LAUNCH_CONFIG_FILE=${1:-/build/config.yaml}
CLUSTER_INFO_FILE=${2:-/build/cluster_info.json}

set -e
LAUNCH_SUCCESS="False"
RETRY_LAUNCH="True"

while [ x"${LAUNCH_SUCCESS}" == x"False" ]; do
    rm -f ${CLUSTER_INFO_FILE} # dcos-launch complains if the file already exists
    dcos-launch create --config-path=${LAUNCH_CONFIG_FILE} --info-path=${CLUSTER_INFO_FILE}
    if [ x"$RETRY_LAUNCH" == x"True" ]; then
        set +e
    else
        set -e
    fi
    dcos-launch wait --info-path=${CLUSTER_INFO_FILE} 2>&1 | tee dcos-launch-wait-output.stdout

    # Grep exits with an exit code of 1 if no lines are matched. We thus need to
    # disable exit on errors.
    set +e
    ROLLBACK_FOUND=$(grep -o "Exception: StackStatus changed unexpectedly to: ROLLBACK_IN_PROGRESS" dcos-launch-wait-output.stdout)
    if [ -n "${ROLLBACK_FOUND}" ]; then

        if [ x"${RETRY_LAUNCH}" == x"False" ]; then
            set -e
            echo "Cluster launch failed"
            exit 1
        fi
        # TODO: This would be a good place to add some form of alerting!
        # We could add a cluster_failure.sh callback, for example.

        # We only retry once!
        RETRY_LAUNCH="False"
        set -e

        # We need to wait for the current stack to be deleted
        dcos-launch delete --info-path=${CLUSTER_INFO_FILE}
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
