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
FRAMEWORK_LIST=$(if [ -d $REPO_ROOT_DIR/frameworks ]; then ls $REPO_ROOT_DIR/frameworks | sort; fi)

# Set default values
security="permissive"
pytest_m="sanity and not azure"
pytest_k=""
azure_args=""
ssh_path="${HOME}/.ssh/ccm.pem"
DCOS_ENTERPRISE=true
interactive=

function usage()
{
    echo "Usage: $0 [-m MARKEXPR] [-k EXPRESSION] [-p PATH] [-s] all|<framework-name> [-i|--interactive]"
    echo "-m passed to pytest directly [default -m \"${pytest_m}\"]"
    echo "-k passed to pytest directly [default NONE]"
    echo "   Additional pytest arguments can be passed in the PYTEST_ARGS"
    echo "   enviroment variable:"
    echo "      PYTEST_ARGS=$PYTEST_ARGS"
    echo "-p PATH to cluster SSH key [default ${ssh_path}]"
    echo "-s run in strict mode (sets \$SECURITY=\"strict\")"
    echo "--interactive start a docker container in interactive mode"
    echo "Cluster must be created and \$CLUSTER_URL set"
    echo "AWS credentials must exist in the variables:"
    echo "      \$AWS_ACCESS_KEY_ID"
    echo "      \$AWS_SECRET_ACCESS_KEY"
    echo "Azure tests will run if these variables are set:"
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

if [ "$#" -eq "0" -o x"${1//-/}" == x"help" -o x"${1//-/}" == x"h" ]; then
    usage
    exit 1
fi


if [ -z "$AWS_ACCESS_KEY_ID" -o -z "$AWS_SECRET_ACCESS_KEY" ]; then
    CREDENTIALS_FILE="$HOME/.aws/credentials"

    PROFILES=$( grep -oE "^\[\S+\]" $CREDENTIALS_FILE )
    if [ $( echo "$PROFILES" | wc -l ) != "1" ]; then
        echo "Only single profile credentials files are supported"
        echo "Found:"
        echo "$PROFILES"
        exit 1
    fi

    if  [ -f "$CREDENTIALS_FILE" ]; then
        echo "Checking $CREDENTIALS_FILE"
        SED_ARGS='s/^.*=\s*//g'
        AWS_ACCESS_KEY_ID=$( grep -oE "^aws_access_key_id\s*=\s*\S+" $CREDENTIALS_FILE | sed $SED_ARGS )
        AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID// /}
        AWS_SECRET_ACCESS_KEY=$( grep -oE "^aws_secret_access_key\s*=\s*\S+" $CREDENTIALS_FILE | sed $SED_ARGS )
        AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY// /}
    fi
    if [ -z "$AWS_ACCESS_KEY_ID" -o -z "$AWS_SECRET_ACCESS_KEY" ]; then
        echo "AWS credentials not found (\$AWS_ACCESS_KEY_ID and \$AWS_SECRET_ACCESS_KEY)."
        exit 1
    fi
fi


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

frameworks=()

while [[ $# -gt 0 ]]; do
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
    ;;
    -o|--open)
    DCOS_ENTERPRISE=false
    ;;
    -p)
    ssh_path="$2"
    shift # past argument
    ;;
    -i|--interactive)
    interactive="true"
    ;;
    -*)
    usage
    exit 1
            # unknown option
    ;;
    *)
    frameworks+=("$key")
    ;;
esac
shift # past argument or value
done

if [ ! -f "$ssh_path" ]; then
    echo "The specified CCM key ($ssh_path) does not exist or is not a file"
    exit 1
fi

echo "interactive=$interactive"
echo "security=$security"
if [ -z $interactive ]; then
    if [ -z "$CLUSTER_URL" ]; then
        echo "Cluster not found. Create and configure one then set \$CLUSTER_URL."
        exit 1
    else
        if [[ x"$security" == x"strict" ]] && [[ $CLUSTER_URL != https* ]]; then
            echo $CLUSTER_URL
            echo "CLUSTER_URL must be https in strict mode"
            exit 1
        fi
    fi

    if [ -z "$frameworks" ]; then
        usage
        exit 1
    fi

    framework="$frameworks"
    if [ "$framework" = "all" -a -n "$STUB_UNIVERSE_URL" ]; then
        echo "Cannot set \$STUB_UNIVERSE_URL when building all frameworks"
        exit 1
    fi
    FRAMEWORK_ARGS="-e FRAMEWORK=$framework"
    DOCKER_COMMAND="bash test-runner.sh"
else
# interactive mode
    FRAMEWORK_ARGS="-u $(id -u):$(id -g) -e DCOS_DIR=/build/.dcos-in-docker"
    FRAMEWORK_ARGS=""
    framework="NOT_SPECIFIED"
    DOCKER_COMMAND="bash"
fi

if [ -n "$pytest_k" ]; then
    if [ -n "$PYTEST_ARGS" ]; then
        PYTEST_ARGS="$PYTEST_ARGS "
    fi
    PYTEST_ARGS="$PYTEST_ARGS-k \"$pytest_k\""
fi

if [ -n "$pytest_m" ]; then
    if [ -n "$PYTEST_ARGS" ]; then
        PYTEST_ARGS="$PYTEST_ARGS "
    fi
    PYTEST_ARGS="$PYTEST_ARGS-m \"$pytest_m\""
fi


docker run --rm \
    -e AWS_ACCESS_KEY_ID="$AWS_ACCESS_KEY_ID" \
    -e AWS_SECRET_ACCESS_KEY="$AWS_SECRET_ACCESS_KEY" \
    -e DCOS_ENTERPRISE="$DCOS_ENTERPRISE" \
    -e DCOS_LOGIN_USERNAME="$DCOS_LOGIN_USERNAME" \
    -e DCOS_LOGIN_PASSWORD="$DCOS_LOGIN_PASSWORD" \
    -e CLUSTER_URL="$CLUSTER_URL" \
    $azure_args \
    -e SECURITY="$security" \
    -e PYTEST_ARGS="$PYTEST_ARGS" \
    $FRAMEWORK_ARGS \
    -e STUB_UNIVERSE_URL="$STUB_UNIVERSE_URL" \
    -v $(pwd):/build \
    -v $ssh_path:/ssh/key \
    -w /build \
    -t \
    -i \
    mesosphere/dcos-commons:latest \
    $DOCKER_COMMAND
