#!/usr/bin/env bash
# Verifies environment and launches docker to execute test_runner.sh

# 1. I can pick up a brand new laptop, and as long as I have docker installed, everything will just work if I do ./test.sh <fw>
# 2. I want test.sh to default to running _all_ tests for that framework.
# 3. I want to be able to pass -m or -k to pytest
# 4. If I pass `all` instead of a fw name, it will run all frameworks
# 5. test.sh should validate i have the AWS keys, and a CLUSTER_URL set, but it need not verify the azure keys / security / etc

# Exit immediately on errors
set -e

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
if [ -d $REPO_ROOT_DIR/frameworks ]; then
    FRAMEWORK_LIST=$(ls $REPO_ROOT_DIR/frameworks | sort)
else
    FRAMEWORK_LIST=$(basename $(pwd))
fi

# Set default values
security="permissive"
pytest_m="sanity and not azure"
pytest_k=""
azure_args=""
gradle_cache="$(pwd)/.gradle_cache"
ssh_path="${HOME}/.ssh/ccm.pem"
aws_credentials_file="${HOME}/.aws/credentials"
aws_profile="default"
enterprise="true"
interactive="false"
headless="false"
package_registry="false"

function abs_path()
{
    if [[ -d $1 ]]; then
        cd $(dirname $1)
        echo $(pwd)/$(basename $1)
    else
        echo ""
    fi
}

function usage()
{
    echo "Usage: $0 [-m MARKEXPR] [-k EXPRESSION] [-p PATH] [-s] [-i|--interactive] [--headless] [--package-registry] [--dcos-files-path DCOS_FILES_PATH] [--aws|-a PATH] [--aws-profile PROFILE] [all|<framework-name>]"
    echo "-m passed to pytest directly [default -m \"${pytest_m}\"]"
    echo "-k passed to pytest directly [default NONE]"
    echo "   Additional pytest arguments can be passed in the PYTEST_ARGS"
    echo "   enviroment variable:"
    echo "      PYTEST_ARGS=$PYTEST_ARGS"
    echo "-p PATH to cluster SSH key [default ${ssh_path}]"
    echo "-s run in strict mode (sets \$SECURITY=\"strict\")"
    echo "--interactive start a docker container in interactive mode"
    echo "--headless leave STDIN available (mutually exclusive with --interactive)"
    echo "--package-registry Use package registry to install packages"
    echo "--dcos-files-path DCOS_FILES_PATH sets the path to look for .dcos files. If empty, use stub universe urls to build .dcos file(s)."
    echo "--gradle-cache PATH sets the gradle cache to the specified path [default ${gradle_cache}]."
    echo "               Setting PATH to \"\" will disable the cache."
    echo "--aws-profile PROFILE the AWS profile to use [default ${aws_profile}]"
    echo "--aws|a PATH to an AWS credentials file [default ${aws_credentials_file}]"
    echo "        (AWS credentials must be present in this file)"
    echo "Azure tests will run if these variables are set:"
    echo "      \$AZURE_CLIENT_ID"
    echo "      \$AZURE_CLIENT_SECRET"
    echo "      \$AZURE_TENANT_ID"
    echo "      \$AZURE_STORAGE_ACCOUNT"
    echo "      \$AZURE_STORAGE_KEY"
    echo "  (changes the -m default to \"sanity\")"
    echo ""
    echo "Cluster must be created and \$CLUSTER_URL set"
    echo ""
    echo "Set \$STUB_UNIVERSE_URL to bypass build"
    echo "  (invalid when building all frameworks)"
    echo ""
    echo "Current frameworks:"
    for framework in $FRAMEWORK_LIST; do
        echo "       $framework"
    done
}

if [ x"${1//-/}" == x"help" -o x"${1//-/}" == x"h" ]; then
    usage
    exit 1
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
    enterprise="false"
    ;;
    -p)
    ssh_path="$2"
    shift # past argument
    ;;
    -i|--interactive)
    interactive="true"
    ;;
    --headless)
    headless="true"
    ;;
    --package-registry)
    package_registry="true"
    ;;
    --dcos-files-path)
    dcos_files_path="$(abs_path "$2")"
    shift
    ;;
    --gradle-cache)
    gradle_cache="$2"
    shift
    ;;
    -a|--aws)
    aws_credentials_file="$2"
    shift # past argument
    ;;
    --aws-profile)
    aws_profile="$2"
    shift
    ;;
    -*)
    echo "Unknown option: $key"
    usage
    exit 1
    ;;
    *)
    frameworks+=("$key")
    ;;
esac
shift # past argument or value
done

if [ -f "$ssh_path" ]; then
    ssh_key_args="-v $ssh_path:/ssh/key" # pass provided key into docker env
else
    if [ -n "$CLUSTER_URL" ]; then
        # If the user is providing us with a cluster, we require the SSH key for that cluster.
        echo "The specified CCM key ($ssh_path) does not exist or is not a file."
        echo "This is required for communication with provided CLUSTER_URL=$CLUSTER_URL"
        exit 1
    fi
    ssh_key_args="" # test_runner.sh will extract the key after cluster launch, nothing to pass in
fi


if [ -f "${aws_credentials_file}" ]; then
    # Pick a profile from the creds file
    PROFILES=$( grep -oE "^\[\S+\]" $aws_credentials_file )
    if [ "$( echo "$PROFILES" | grep "\[${aws_profile}\]" )" != "[${aws_profile}]" ]; then
        echo "The specified profile (${aws_profile}) was not found in the file $aws_credentials_file"

        if [ $( echo "$PROFILES" | wc -l ) == "1" ]; then
            PROFILES="${PROFILES/#[/}"
            aws_profile="${PROFILES/%]/}"
            echo "Using single profile: ${aws_profile}"
        elif [ -n $AWS_PROFILE ]; then
            echo "Use AWS_PROFILE"
            aws_profile=$AWS_PROFILE
        else
            echo "Found:"
            echo "$PROFILES"
            echo ""
            echo "Specify the correct profile using the --aws-profile command line option"
            exit 1
        fi
    fi
else
    # CI environments may have creds in AWS_DEV_* envvars:
    if [ -n "${AWS_DEV_ACCESS_KEY_ID}" -a -n "${AWS_DEV_SECRET_ACCESS_KEY}}" ]; then # CI environment (direct invocation)
	export AWS_ACCESS_KEY_ID=${AWS_DEV_ACCESS_KEY_ID} AWS_SECRET_ACCESS_KEY=${AWS_DEV_SECRET_ACCESS_KEY}
    fi
    # Check AWS_* envvars for credentials, create temp creds file using those credentials:
    if [ -n "${AWS_ACCESS_KEY_ID}" -a -n "${AWS_SECRET_ACCESS_KEY}}" ]; then # CI environment (via docker run)
        aws_credentials_file=$(mktemp /tmp/awscreds-XXXXXX)
	echo "Writing AWS env credentials to: ${aws_credentials_file}"
        cat > $aws_credentials_file <<EOF
[default]
aws_access_key_id = ${AWS_ACCESS_KEY_ID}
aws_secret_access_key = ${AWS_SECRET_ACCESS_KEY}
EOF
    else
        echo "AWS credentials file '${aws_credentials_file}' was not found,"
        echo "and there were no AWS credentials in the environment."
        echo "Try running 'maws' to log in."
        exit 1
    fi
fi

echo "interactive=$interactive"
echo "headless=$headless"
echo "package-registry=${package_registry}"
echo "security=$security"
echo "enterprise=$enterprise"

DOCKER_ARGS=
if [ -n $gradle_cache ]; then
    echo "Setting Gradle cache to ${gradle_cache}"
    DOCKER_ARGS="${DOCKER_ARGS} -v ${gradle_cache}:/root/.gradle"
else
    echo "Disabling Gradle cache"
fi

# Some automation contexts (e.g. Jenkins) will be unhappy
# if STDIN is not available. The --headless command accomodates
# such contexts.
if [ x"$headless" == x"true" ]; then
    if [ x"$interactive" == "true" ]; then
        echo "Both --headless and -i|--interactive cannot be used at the same time."
        exit 1
    fi
    DOCKER_INTERACTIVE_FLAGS=""
else
    DOCKER_INTERACTIVE_FLAGS="-i"
fi


WORK_DIR="/build"

if [ x"$interactive" == x"false" ]; then
    framework="$frameworks"
    if [ "$framework" = "all" -a -n "$STUB_UNIVERSE_URL" ]; then
        echo "Cannot set \$STUB_UNIVERSE_URL when building all frameworks"
        exit 1
    fi
    framework_args="-e FRAMEWORK=$framework"
    DOCKER_COMMAND="bash /build-tools/test_runner.sh $WORK_DIR"
else
    # interactive mode
    # framework_args="-u $(id -u):$(id -g) -e DCOS_DIR=/build/.dcos-in-docker"
    framework_args=""
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

if [ x"$package_registry" == x"true" ]; then
    if [ -z "$PACKAGE_REGISTRY_STUB_URL" ]; then
        echo "PACKAGE_REGISTRY_STUB_URL not found in environment. Exiting..."
        exit 1
    fi
fi

if [ -n "$dcos_files_path" ]; then
    dcos_files_path_args="-e DCOS_FILES_PATH=\"${dcos_files_path}\" -v \"${dcos_files_path}\":\"${dcos_files_path}\""
fi

if [ -n "$TEAMCITY_VERSION" ]; then
    # Teamcity python module treats a present-but-empty envvar as "ENABLED!"
    teamcity_args="-e TEAMCITY_VERSION=\"${TEAMCITY_VERSION}\""
fi
docker run --rm \
    -v ${aws_credentials_file}:/root/.aws/credentials:ro \
    -e AWS_PROFILE="${aws_profile}" \
    -e DCOS_ENTERPRISE="$enterprise" \
    -e DCOS_LOGIN_USERNAME="$DCOS_LOGIN_USERNAME" \
    -e DCOS_LOGIN_PASSWORD="$DCOS_LOGIN_PASSWORD" \
    -e CLUSTER_URL="$CLUSTER_URL" \
    -e S3_BUCKET="$S3_BUCKET" \
    ${azure_args} \
    -e SECURITY="$security" \
    -e PYTEST_ARGS="$PYTEST_ARGS" \
    ${teamcity_args} \
    ${framework_args} \
    -e STUB_UNIVERSE_URL="$STUB_UNIVERSE_URL" \
    -e PACKAGE_REGISTRY_ENABLED="${package_registry}" \
    -e PACKAGE_REGISTRY_STUB_URL="${PACKAGE_REGISTRY_STUB_URL}" \
    ${dcos_files_path_args} \
    ${CUSTOM_DOCKER_ARGS} \
    -v $(pwd):$WORK_DIR \
    ${ssh_key_args} \
    -w $WORK_DIR \
    -t \
    ${DOCKER_INTERACTIVE_FLAGS} \
    ${DOCKER_ARGS} \
    mesosphere/dcos-commons:latest \
    ${DOCKER_COMMAND}
