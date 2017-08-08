#!/usr/bin/env bash
# Verifies environment and launches docker to execute test-runner.sh

# 1. I can pick up a brand new laptop, and as long as I have docker installed, everything will just work if I do ./test.sh <fw>
# 2. I want test.sh to default to running _all_ tests for that framework.
# 3. I want to be able to pass -m or -k to pytest
# 4. If I pass `all` instead of a fw name, it will run all frameworks
# 5. test.sh should validate i have the AWS keys, and a CLUSTER_URL set, but it need not verify the azure keys / security / etc

# Exit immediately on errors
set -e

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
FRAMEWORK_LIST=$(ls $REPO_ROOT_DIR/frameworks | sort)

function usage()
{
    echo "Usage: $0 [-m MARKEXPR] [-k EXPRESSION] [-p PATH] [-s] all|<framework-name>"
    echo "-m passed to pytest directly [default -m \"sanity and not azure\"]"
    echo "-k passed to pytest directly [default NONE]"
    echo "-p PATH to cluster SSH key [default ~/.ssh/ccm.pem]"
    echo "-s run in strict mode (sets \$SECURITY=\"strict\")"
    echo "Cluster must be created and \$CLUSTER_URL set"
    echo "AWS credentials must exist in the variables:"
    echo "      \$AWS_ACCESS_KEY_ID"
    echo "      \$AWS_SECRET_ACCESS_KEY"
    echo "Azure tests will run if thses variables are set:"
    echo "      \$AWS_ACCESS_KEY_ID"
    echo "      \$AWS_SECRET_ACCESS_KEY"
    echo "      \$AZURE_CLIENT_ID"
    echo "      \$AZURE_CLIENT_SECRET"
    echo "      \$AZURE_TENANT_ID"
    echo "      \$AZURE_STORAGE_ACCOUNT"
    echo "      \$AZURE_STORAGE_KEY"
    echo "  (changes the -m default to \"sanity\")"
    echo "Set \$STUB_UNIVERSE_URL to bypass build"
    echo "  (invalid when building all frameworks)"
    echo "Current frameworks:"
    for framework in $FRAMEWORK_LIST; do
        echo "       $framework"
    done
}

if [ "$#" -eq "0" ]; then
    usage
    exit 1
fi

if [ -z "$CLUSTER_URL" ]; then
    echo "Cluster not found. Create and configure one then set \$CLUSTER_URL."
    exit 1
fi

if [ -z "$AWS_ACCESS_KEY_ID" -o -z "$AWS_SECRET_ACCESS_KEY" ]; then
    echo "AWS credentials not found (\$AWS_ACCESS_KEY_ID and \$AWS_SECRET_ACCESS_KEY)."
    exit 1
fi

security="permissive"
pytest_m="sanity and not azure"
pytest_k=""
azure_args=""
ssh_path="${HOME}/.ssh/ccm.pem"

# If AZURE variables are given, change default -m and prepare args for docker
if [ -n "$AZURE_DEV_CLIENT_ID" -a -n "$AZURE_DEV_CLIENT_SECRET" -a \
        -n "$AZURE_DEV_TENANT_ID" -a -n "$AZURE_DEV_STORAGE_ACCOUNT" -a \
        -n "$AZURE_DEV_STORAGE_KEY" ]; then
azure_args=$(cat<<EOFF
    -e AZURE_CLIENT_ID=$AZURE_DEV_CLIENT_ID \
    -e AZURE_CLIENT_SECRET=$AZURE_DEV_CLIENT_SECRET \
    -e AZURE_TENANT_ID=$AZURE_DEV_TENANT_ID \
    -e AZURE_STORAGE_ACCOUNT=$AZURE_DEV_STORAGE_ACCOUNT \
    -e AZURE_STORAGE_KEY=$AZURE_DEV_STORAGE_KEY
EOFF
)
    pytest_m="sanity"
fi

while [[ $# -gt 1 ]]
do
key="$1"

case $key in
    -m)
    pytest_m="$2"
    shift # past argument
    ;;
    -k)
    pytest_k="$2"
    shift # past argument
    ;;
    -s)
    security="strict"
    [[ $CLUSTER_URL == https* ]] || echo "CLUSTER_URL must be https in strict mode" && exit 1
    ;;
    -p)
    ssh_path="$2"
    shift # past argument
    ;;
    *)
    usage
    exit 1
            # unknown option
    ;;
esac
shift # past argument or value
done

if [ -z "$1" ]; then
    usage
    exit 1
fi

framework=$1
if [ "$framework" = "all" -a -n "$STUB_UNIVERSE_URL" ]; then
    echo "Cannot set \$STUB_UNIVERSE_URL when building all frameworks"
    exit 1
fi

docker run --rm \
    -e AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID \
    -e AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY \
    -e CLUSTER_URL=$CLUSTER_URL \
    $azure_args \
    -e SECURITY=$security \
    -e "PYTEST_K=$pytest_k" \
    -e "PYTEST_M=$pytest_m" \
    -e FRAMEWORK=$framework \
    -e STUB_UNIVERSE_URL=$STUB_UNIVERSE_URL \
    -v $(pwd):/build \
    -v $ssh_path:/ssh/key \
    -w /build \
    michaelellenburg/dcos-commons:v3 \
    bash test-runner.sh
