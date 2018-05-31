#!/usr/bin/env bash
# Verifies environment and launches docker to execute test_runner.sh

# 1. I can pick up a brand new laptop, and as long as I have docker installed, everything will just work if I do ./test.sh <fw>
# 2. I want test.sh to default to running _all_ tests for that framework.
# 3. I want to be able to pass -m or -k to pytest
# 4. If I pass `all` instead of a fw name, it will run all frameworks
# 5. test.sh should validate i have the AWS keys, and a CLUSTER_URL set, but it need not verify the azure keys / security / etc

# Exit immediately on errors
set -e

# Create a temp file for docker env.
# When the script exits (successfully or otherwise), clean up the file automatically.
credsfile=$(mktemp /tmp/sdk-test-creds-XXXXX.tmp)
envfile=$(mktemp /tmp/sdk-test-env-XXXXX.tmp)
function cleanup {
    rm -f ${credsfile}
    rm -f ${envfile}
}
trap cleanup EXIT

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
WORK_DIR="/build" # where REPO_ROOT_DIR is mounted within the image

# Find out what framework(s) are available.
# - If there's a <REPO>/frameworks directory, get values from there.
# - Otherwise just use the name of the repo directory.
# If there's multiple options, the user needs to pick one. If there's only one option then we'll use that automatically.
if [ -d $REPO_ROOT_DIR/frameworks ]; then
    # mono-repo (e.g. dcos-commons)
    FRAMEWORK_LIST=$(ls $REPO_ROOT_DIR/frameworks | sort | xargs echo -n)
else
    # standalone repo (e.g. spark-build)
    FRAMEWORK_LIST=$(basename ${REPO_ROOT_DIR})
fi


if [ -n "$AZURE_DEV_CLIENT_ID" -a -n "$AZURE_DEV_CLIENT_SECRET" -a \
        -n "$AZURE_DEV_TENANT_ID" -a -n "$AZURE_DEV_STORAGE_ACCOUNT" -a \
        -n "$AZURE_DEV_STORAGE_KEY" ]; then
    azure_enabled="true"
fi

# Set default values
security="permissive"
if [ -n "$azure_enabled" ]; then
    pytest_m="sanity"
else
    pytest_m="sanity and not azure"
fi
gradle_cache="${REPO_ROOT_DIR}/.gradle_cache"
ssh_path="${HOME}/.ssh/ccm.pem"
aws_creds_path="${HOME}/.aws/credentials"
enterprise="true"
headless="false"
interactive="false"
package_registry="false"
docker_command=${DOCKER_COMMAND:="bash /build-tools/test_runner.sh $WORK_DIR"}

function usage()
{
    echo "Usage: $0 [flags] [framework:$(echo $FRAMEWORK_LIST | sed 's/ /,/g')]"
    echo ""
    echo "Flags:"
    echo "  -m $pytest_m"
    echo "  -k <args>"
    echo "    Test filters passed through to pytest. Other arguments may be passed with PYTEST_ARGS."
    echo "  -s"
    echo "    Using a strict mode cluster: configure/use ACLs."
    echo "  -o"
    echo "    Using an Open DC/OS cluster: skip Enterprise-only features."
    echo "  -p $ssh_path"
    echo "    Path to cluster SSH key."
    echo "  -i/--interactive"
    echo "    Open a shell prompt in the docker container, without actually running any tests. Equivalent to DOCKER_COMMAND=bash"
    echo "  --headless"
    echo "    Run docker command in headless mode, without attaching to stdin. Sometimes needed in CI."
    echo "  --package-registry"
    echo "    Enables using a package registry to install packages. Requires \$PACKAGE_REGISTRY_STUB_URL."
    echo "  --dcos-files-path DIR"
    echo "    Sets the directory to look for .dcos files. If empty, uses stub universe urls to build .dcos file(s)."
    echo "  --gradle-cache $gradle_cache"
    echo "    Sets the gradle build cache to the specified path. Setting this to \"\" disables the cache."
    echo "  -a/--aws $aws_creds_path"
    echo "    Path to an AWS credentials file. Overrides any AWS_* env credentials."
    echo "  --aws-profile ${AWS_PROFILE:=NAME}"
    echo "    The AWS profile to use. Only required when using an AWS credentials file with multiple profiles."
    echo ""
    echo "Environment:"
    echo "  CLUSTER_URL"
    echo "    URL to cluster. If unset then a cluster will be created using dcos-launch"
    echo "  STUB_UNIVERSE_URL"
    echo "    One or more comma-separated stub-universe URLs. If unset then a build will be performed internally."
    echo "  DCOS_LOGIN_USERNAME/DCOS_LOGIN_PASSWORD"
    echo "    Custom login credentials to use for the cluster."
    echo "  AZURE_[CLIENT_ID,CLIENT_SECRET,TENANT_ID,STORAGE_ACCOUNT,STORAGE_KEY]"
    echo "    Enables Azure tests. The -m default is automatically updated to include any Azure tests."
    echo "  AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY or AWS_DEV_ACCESS_KEY_ID/AWS_DEV_SECRET_ACCESS_KEY"
    echo "    AWS credentials to use if the credentials file is unavailable."
    echo "  S3_BUCKET"
    echo "    S3 bucket to use for testing."
    echo "  DOCKER_COMMAND=$docker_command"
    echo "    Command to be run within the docker image (e.g. 'DOCKER_COMMAND=bash' to just get a prompt)"
    echo "  PYTEST_ARGS"
    echo "    Additional arguments (other than -m or -k) to pass to pytest."
    echo "  TEST_SH_*"
    echo "    Anything starting with TEST_SH_* will be forwarded to the container with that prefix removed."
    echo "    For example, 'TEST_SH_FOO=BAR' is included as 'FOO=BAR'."
}

if [ x"${1//-/}" == x"help" -o x"${1//-/}" == x"h" ]; then
    usage
    exit 1
fi

framework=""

while [[ $# -gt 0 ]]; do
key="$1"
case $key in
    -m)
    pytest_m="$2"
    shift
    ;;
    -k)
    pytest_k="$2"
    shift
    ;;
    -s)
    security="strict"
    ;;
    -o|--open)
    enterprise="false"
    ;;
    -p)
    if [[ ! -f "$2" ]]; then echo "File not found: -p $2"; exit 1; fi
    ssh_path="$2"
    shift
    ;;
    -i|--interactive)
    if [[ x"$headless" == x"true" ]]; then echo "Cannot enable both --headless and --interactive: Disallowing background prompt that runs forever."; exit 1; fi
    interactive="true"
    ;;
    --headless)
    if [[ x"$interactive" == x"true" ]]; then echo "Cannot enable both --headless and --interactive: Disallowing background prompt that runs forever."; exit 1; fi
    headless="true"
    ;;
    --package-registry)
    package_registry="true"
    ;;
    --dcos-files-path)
    if [[ ! -d "$2" ]]; then echo "Directory not found: --dcos-files-path $2"; exit 1; fi
    # Resolve abs path:
    dcos_files_path="$( cd "$( dirname "$2" )" && pwd )/$(basename "$2")"
    shift
    ;;
    --gradle-cache)
    if [[ ! -d "$2" ]]; then echo "Directory not found: --gradle-cache $2"; exit 1; fi
    gradle_cache="$2"
    shift
    ;;
    -a|--aws)
    if [[ ! -f "$2" ]]; then echo "File not found: -a/--aws $2"; exit 1; fi
    aws_creds_path="$2"
    shift
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
    if [[ -n "$framework" ]]; then echo "Multiple frameworks specified, please only specify one at a time: $framework $@"; exit 1; fi
    framework=$key
    ;;
esac
shift # past argument or value
done

if [ -z "$framework" ]; then
    # If FRAMEWORK_LIST only has one option, use that. Otherwise complain.
    if [ $(echo $FRAMEWORK_LIST | wc -w) == 1 ]; then
        framework=$FRAMEWORK_LIST
    else
        echo "Multiple frameworks in $(basename $REPO_ROOT_DIR)/frameworks/, please specify one to test: $FRAMEWORK_LIST"
        exit 1
    fi
elif [ "$framework" = "all" ]; then
    echo "'all' is no longer supported. Please specify one framework to test: $FRAMEWORK_LIST"
    exit 1
fi

volume_args="-v ${REPO_ROOT_DIR}:$WORK_DIR"

# Configure SSH key for getting into the cluster during tests
if [ -f "$ssh_path" ]; then
    volume_args="$volume_args -v $ssh_path:/ssh/key" # pass provided key into docker env
else
    if [ -n "$CLUSTER_URL" ]; then
        # If the user is providing us with a cluster, we require the SSH key for that cluster.
        echo "SSH key not found at $ssh_path. Use -p <path/to/id_rsa> to customize this path."
        echo "An SSH key is required for communication with the provided CLUSTER_URL=$CLUSTER_URL"
        exit 1
    fi
    # Don't need ssh key now: test_runner.sh will extract the key after cluster launch
fi

# Configure the AWS credentials profile
if [ -n "${aws_profile}" ]; then
    echo "Using provided --aws-profile: ${aws_profile}"
elif [ -n "$AWS_PROFILE" ]; then
    echo "Using provided AWS_PROFILE: $AWS_PROFILE"
    aws_profile=$AWS_PROFILE
elif [ -f "${aws_creds_path}" ]; then
    # Check the creds file. If there's exactly one profile, then use that profile.
    available_profiles=$(grep -oE '^\[\S+\]' $aws_creds_path | tr -d '[]') # find line(s) that look like "[profile]", remove "[]"
    available_profile_count=$(echo "$available_profiles" | wc -l)
    if [ "$available_profile_count" == "1" ]; then
        aws_profile=$available_profiles
        echo "Using sole profile in $aws_creds_path: $aws_profile"
    else
        echo "Expected 1 profile in $aws_creds_path, found $available_profile_count: ${available_profiles}"
        echo "Please specify --aws-profile or \$AWS_PROFILE to select a profile"
        exit 1
    fi
else
    echo "No AWS profile specified, using 'default'"
    aws_profile="default"
fi

# Write the AWS credential file (deleted on script exit)
if [ -f "${aws_creds_path}" ]; then
    cat $aws_creds_path > $credsfile
else
    # CI environments may have creds in AWS_DEV_* envvars, map them to AWS_*:
    if [ -n "${AWS_DEV_ACCESS_KEY_ID}" -a -n "${AWS_DEV_SECRET_ACCESS_KEY}}" ]; then
	AWS_ACCESS_KEY_ID=${AWS_DEV_ACCESS_KEY_ID}
        AWS_SECRET_ACCESS_KEY=${AWS_DEV_SECRET_ACCESS_KEY}
    fi
    # Check AWS_* envvars for credentials, create temp creds file using those credentials:
    if [ -n "${AWS_ACCESS_KEY_ID}" -a -n "${AWS_SECRET_ACCESS_KEY}}" ]; then
	echo "Writing AWS env credentials to temporary file: $credsfile"
        cat > $credsfile <<EOF
[${aws_profile}]
aws_access_key_id = ${AWS_ACCESS_KEY_ID}
aws_secret_access_key = ${AWS_SECRET_ACCESS_KEY}
EOF
    else
        echo "Missing AWS credentials file (${aws_creds_path}) and AWS env (AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY)"
        exit 1
    fi
fi
volume_args="$volume_args -v $credsfile:/root/.aws/credentials:ro"

if [ -n "$gradle_cache" ]; then
    echo "Setting Gradle cache to ${gradle_cache}"
    volume_args="$volume_args -v ${gradle_cache}:/root/.gradle"
fi

if [ x"$interactive" == x"true" ]; then
    docker_command="bash"
fi

# Some automation contexts (e.g. Jenkins) will be unhappy if STDIN is not available. The --headless command accomodates such contexts.
if [ x"$headless" != x"true" ]; then
    docker_interactive_arg="-i"
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
    volume_args="$volume_args -v \"${dcos_files_path}\":\"${dcos_files_path}\""
fi

if [ -n "$TEAMCITY_VERSION" ]; then
    # The teamcity python module treats present-but-empty as enabled.
    # We must therefore completely omitted this envvar to disable teamcity handling.
    echo "TEAMCITY_VERSION=\"${TEAMCITY_VERSION}\"" >> $envfile
fi

if [ -n "$azure_enabled" ]; then
    cat >> $envfile <<EOF
AZURE_CLIENT_ID=$AZURE_DEV_CLIENT_ID
AZURE_CLIENT_SECRET=$AZURE_DEV_CLIENT_SECRET
AZURE_TENANT_ID=$AZURE_DEV_TENANT_ID
AZURE_STORAGE_ACCOUNT=$AZURE_DEV_STORAGE_ACCOUNT
AZURE_STORAGE_KEY=$AZURE_DEV_STORAGE_KEY
EOF
fi

cat >> $envfile <<EOF
AWS_PROFILE=$aws_profile
CLUSTER_URL=$CLUSTER_URL
DCOS_ENTERPRISE=$enterprise
DCOS_FILES_PATH=$dcos_files_path
DCOS_LOGIN_PASSWORD=$DCOS_LOGIN_PASSWORD
DCOS_LOGIN_USERNAME=$DCOS_LOGIN_USERNAME
FRAMEWORK=$framework
PACKAGE_REGISTRY_ENABLED=$package_registry
PACKAGE_REGISTRY_STUB_URL=$PACKAGE_REGISTRY_STUB_URL
PYTEST_ARGS=$PYTEST_ARGS
S3_BUCKET=$S3_BUCKET
SECURITY=$security
STUB_UNIVERSE_URL=$STUB_UNIVERSE_URL
EOF

while read line; do
    # Prefix match, then strip prefix in envfile:
    if [[ "${line:0:8}" = "TEST_SH_" ]]; then
        echo ${line#TEST_SH_} >> $envfile
    fi
done < <(env)

CMD="docker run --rm \
-t \
${docker_interactive_arg} \
--env-file $envfile \
${volume_args} \
-w $WORK_DIR \
mesosphere/dcos-commons:latest \
${docker_command}"

echo "==="
echo "Docker command:"
echo "  $CMD"
echo ""
echo "Environment:"
while read line; do
    echo "  $line"
done <$envfile
echo "==="

$CMD
