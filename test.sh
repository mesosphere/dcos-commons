#!/usr/bin/env bash

# Sets the stage to run arbitrary commands (most importantly test_runner.sh) in
# a Docker container using by default the mesosphere/dcos-commons Docker image.

# Will run any commands specified in the DOCKER_COMMAND environment variable.
# e.g.:
# $ DOCKER_COMMAND=ls ./test.sh

# Goals:
# 1. I can pick up a brand new laptop, and as long as I have Docker installed,
#    everything will just work if I run "./test.sh $framework".
#
# 2. I want test.sh to default to running all tests for that framework.
#
# 3. I want to be able to pass -m or -k to pytest.
#
# 4. If I pass "all" instead of a framework name, it will run all frameworks.
#
# 5. This script should validate that AWS credentials exist, and a CLUSTER_URL
#    set, but it need not verify the azure keys / security / etc.

set -eo pipefail

readonly REQUIREMENTS='docker'

for requirement in ${REQUIREMENTS}; do
  if ! [[ -x $(command -v "${requirement}") ]]; then
    echo "You need to install '${requirement}' to run this script"
    exit 1
  fi
done

if [ "${1//-/}" == "help" ] || [ "${1//-/}" == "h" ]; then
  help="true"
fi

timestamp="$(date +%y%m%d-%H%M%S)"
tmp_aws_credentials_file="$(mktemp "/tmp/dcos-commons-aws-credentials-${timestamp}-XXXX.tmp")"
env_file="$(mktemp "/tmp/dcos-commons-env-file-${timestamp}-XXXX.tmp")"

function cleanup {
  rm -f "${tmp_aws_credentials_file}"
  rm -f "${env_file}"
}
trap cleanup EXIT

DCOS_COMMONS_DIRECTORY=${DCOS_COMMONS_DIRECTORY:="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"}
# Where $DCOS_COMMONS_DIRECTORY is mounted in the container.
#
# If this script is run with the '--project' flag, the project's "frameworks"
# directory will be mounted at '$WORK_DIR/frameworks' in the container.
WORK_DIR="/build"

################################################################################
#################### Default values for CLI parameters #########################
################################################################################

if [ -n "${AZURE_DEV_CLIENT_ID}" ] \
     && [ -n "${AZURE_DEV_CLIENT_SECRET}" ] \
     && [ -n "${AZURE_DEV_TENANT_ID}" ] \
     && [ -n "${AZURE_DEV_STORAGE_ACCOUNT}" ] \
     && [ -n "${AZURE_DEV_STORAGE_KEY}" ]; then
  azure_enabled="true"
fi

security="permissive"
if [ -n "${azure_enabled}" ]; then
  pytest_m="sanity"
else
  pytest_m="sanity and not azure"
fi
gradle_cache="${DCOS_COMMONS_DIRECTORY}/.gradle_cache"
ssh_path="${HOME}/.ssh/ccm.pem"
ssh_user="core"
aws_credentials_path="${HOME}/.aws/credentials"
enterprise="true"
headless="false"
interactive="false"
package_registry="false"
docker_options="${DOCKER_OPTIONS:=}"
docker_command="${DOCKER_COMMAND:=bash ${WORK_DIR}/tools/ci/test_runner.sh ${WORK_DIR}}"
docker_image="${DOCKER_IMAGE:-mesosphere/dcos-commons:latest}"
env_passthrough=
env_file_input=

################################################################################
################################ CLI usage #####################################
################################################################################

function usage()
{
  set +x
  echo "Usage: $0 [flags] [framework:${FRAMEWORK_LIST// /,}]"
  echo
  echo "Parameters:"
  echo "  --project ${project}"
  echo "    Full path to a 'dcos-commons'-based project's directory. E.g.: /path/to/dcos-kafka-service, /path/to/dcos-elastic-service"
  echo
  echo "  -m ${pytest_m}"
  echo "    Only run tests matching given mark expression. Example: -m 'sanity and not azure'"
  echo
  echo "  -k <args>"
  echo "    Only run tests which match the given substring expression. Example : -k 'test_tls and not test_tls_soak'"
  echo
  echo "  -s"
  echo "    Using a strict mode cluster: configure/use ACLs."
  echo
  echo "  -o"
  echo "    Using an Open DC/OS cluster: skip Enterprise-only features."
  echo
  echo "  -p ${ssh_path}"
  echo "    Path to cluster SSH key."
  echo
  echo "  -l ${ssh_user}"
  echo "    Username to use for SSH commands into the cluster."
  echo
  echo "  -e ${env_passthrough}"
  echo "    A comma-separated list of environment variables to pass through to the running docker container"
  echo
  echo "  --env_file ${env_file_input}"
  echo "    A path to an env_file to pass to the docker container in addition to those required by the test scripts"
  echo
  echo "  -i/--interactive"
  echo "    Open a shell prompt in the docker container, without actually running any tests. Equivalent to DOCKER_COMMAND=bash"
  echo
  echo "  --headless"
  echo "    Run docker command in headless mode, without attaching to stdin. Sometimes needed in CI."
  echo
  echo "  --package-registry"
  echo "    Enables using a package registry to install packages. Works in 1.12.1 and above only."
  echo
  echo "  --dcos-files-path DIR"
  echo "    Sets the directory to look for .dcos files. If empty, uses stub universe urls to build .dcos file(s)."
  echo
  echo "  --gradle-cache ${gradle_cache}"
  echo "    Sets the gradle build cache to the specified path. Setting this to \"\" disables the cache."
  echo
  echo "  -a/--aws ${aws_credentials_path}"
  echo "    Path to an AWS credentials file. Overrides any AWS_* env credentials."
  echo
  echo "  --aws-profile ${AWS_PROFILE:=NAME}"
  echo "    The AWS profile to use. Only required when using an AWS credentials file with multiple profiles."
  echo
  echo "---"
  echo
  echo "Environment variables:"
  echo "  CLUSTER_URL"
  echo "    URL to cluster. If unset then a cluster will be created using dcos-launch"
  echo "  STUB_UNIVERSE_URL"
  echo "    One or more comma-separated stub-universe URLs. If unset then a build will be performed internally."
  echo "  DCOS_LOGIN_USERNAME/DCOS_LOGIN_PASSWORD"
  echo "    Custom login credentials to use for the cluster."
  echo "  AZURE_{CLIENT_ID,CLIENT_SECRET,TENANT_ID,STORAGE_ACCOUNT,STORAGE_KEY}"
  echo "    Enables Azure tests. The -m default is automatically updated to include any Azure tests."
  echo "  AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY or AWS_DEV_ACCESS_KEY_ID/AWS_DEV_SECRET_ACCESS_KEY"
  echo "    AWS credentials to use if the credentials file is unavailable."
  echo "  S3_BUCKET"
  echo "    S3 bucket to use for testing."
  echo "  DOCKER_COMMAND=${docker_command}"
  echo "    Command to be run within the docker image (e.g. 'DOCKER_COMMAND=bash' to just get a prompt)"
  echo "  DCOS_COMMONS_DIRECTORY=${DCOS_COMMONS_DIRECTORY}"
  echo "    Allows for overriding the location of the repository's root directory. Autodetected by default."
  echo "    Must be an absolute path."
  echo "  PYTEST_ARGS"
  echo "    Additional arguments (other than -m or -k) to pass to pytest."
  echo "  TEST_SH_*"
  echo "    Anything starting with TEST_SH_* will be forwarded to the container with that prefix removed."
  echo "    For example, 'TEST_SH_FOO=BAR' is included as 'FOO=BAR'."
}

if [ "${help}" == "true" ]; then
  usage
  exit 1
fi

################################################################################
############################ Parse CLI parameters ##############################
################################################################################

framework=""
project=""

while [[ ${#} -gt 0 ]]; do
  arg="${1}"

  case "${arg}" in
    --project)
      if [[ ! -d "${2}" ]]; then echo "'${arg}' takes a directory. '${2}' is not a directory"; exit 1; fi
      project="${2}"
      shift
      ;;
    -m)
      pytest_m="${2}"
      shift
      ;;
    -k)
      pytest_k="${2}"
      shift
      ;;
    -s)
      security="strict"
      ;;
    -o|--open)
      enterprise="false"
      ;;
    -p)
      if [[ ! -f "${2}" ]]; then echo "File not found: ${arg} ${2}"; exit 1; fi
      ssh_path="${2}"
      shift
      ;;
    -l)
      ssh_user="${2}"
      shift
      ;;
    -e)
      env_passthrough="${2}"
      shift
      ;;
    --env_file)
      if [[ ! -f "${2}" ]]; then echo "File not found: ${arg} ${2}"; exit 1; fi
      env_file_input="${2}"
      shift
      ;;
    -i|--interactive)
      if [[ "${headless}" == "true" ]]; then echo "Cannot enable both --headless and --interactive: Disallowing background prompt that runs forever."; exit 1; fi
      interactive="true"
      ;;
    --headless)
      if [[ "${interactive}" == "true" ]]; then echo "Cannot enable both --headless and --interactive: Disallowing background prompt that runs forever."; exit 1; fi
      headless="true"
      ;;
    --package-registry)
      package_registry="true"
      ;;
    --dcos-files-path)
      if [[ ! -d "${2}" ]]; then echo "Directory not found: ${arg} ${2}"; exit 1; fi
      # Resolve abs path:
      dcos_files_path="$(cd "$(dirname "${2}")" && pwd)/$(basename "${2}")"
      shift
      ;;
    --gradle-cache)
      if [[ ! -d "${2}" ]]; then echo "Directory not found: ${arg} ${2}"; exit 1; fi
      gradle_cache="${2}"
      shift
      ;;
    -a|--aws)
      if [[ ! -f "${2}" ]]; then echo "File not found: ${arg} ${2}"; exit 1; fi
      aws_credentials_path="${2}"
      shift
      ;;
    --aws-profile)
      aws_profile="${2}"
      shift
      ;;
    -*)
      echo "Unknown option: ${arg}"
      usage
      exit 1
      ;;
    *)
      if [[ -n "${framework}" ]]; then echo "Multiple frameworks specified, please only specify one at a time: ${framework} ${*}"; exit 1; fi
      framework="${arg}"
      ;;
  esac

  shift
done

################################################################################
############################ Parse CLI parameters ##############################
################################################################################

if [ -n "${gradle_cache}" ]; then
  echo "Setting Gradle cache to ${gradle_cache}"
  container_volumes="${container_volumes} -v ${gradle_cache}:/root/.gradle"
fi

if [ "${interactive}" == "true" ]; then
  docker_command="bash"
fi

# Some automation contexts (e.g. Jenkins) will be unhappy if STDIN is not
# available. The --headless command accommodates such contexts.
if [ "${headless}" != "true" ]; then
  docker_interactive_arg="-i"
fi

if [ -n "${dcos_files_path}" ]; then
  container_volumes="${container_volumes} -v ${dcos_files_path}:${dcos_files_path}"
fi

################################################################################
################################### pytest #####################################
################################################################################

if [ -n "${pytest_k}" ]; then
  if [ -n "${PYTEST_ARGS}" ]; then
    PYTEST_ARGS="${PYTEST_ARGS} "
  fi
  PYTEST_ARGS="${PYTEST_ARGS}-k \"${pytest_k}\""
fi
if [ -n "${pytest_m}" ]; then
  if [ -n "${PYTEST_ARGS}" ]; then
    PYTEST_ARGS="${PYTEST_ARGS} "
  fi
  PYTEST_ARGS="${PYTEST_ARGS}-m \"${pytest_m}\""
fi

################################################################################
######### Set up "project", "framework" and "cluster" related variables ########
################################################################################

# Set project root, defaulting to the dcos-commons directory.
PROJECT_ROOT="${project:-${DCOS_COMMONS_DIRECTORY}}"

# Find out which framework(s) are available.
#
# - If there's a "$PROJECT_ROOT/frameworks" directory, get its children.
# - Otherwise just use the name of the repo directory.
#
# If there's multiple options, the user needs to pick one. If there's only one
# option then we'll use that automatically.
if [ -d "${PROJECT_ROOT}/frameworks" ]; then
  FRAMEWORK_LIST=$(find "${PROJECT_ROOT}/frameworks" -maxdepth 1 -mindepth 1 -type d | sort | xargs echo -n)
else
  # spark-build & jenkins.
  FRAMEWORK_LIST=$(basename "${PROJECT_ROOT}")
fi

if [ -z "${framework}" ] \
     && [ "${interactive}" != "true" ] \
     && [ "${DOCKER_COMMAND}" == "" ]; then
  # If FRAMEWORK_LIST only has one option, use that. Otherwise complain.
  if [ "$(echo "${FRAMEWORK_LIST}" | wc -w)" == 1 ]; then
    framework="${FRAMEWORK_LIST}"
  else
    echo "Multiple frameworks in '${PROJECT_ROOT}/frameworks/', please specify one to test: ${FRAMEWORK_LIST}"
    exit 1
  fi
elif [ "${framework}" = "all" ]; then
  echo "'all' is no longer supported. Please specify one framework to test: ${FRAMEWORK_LIST}"
  exit 1
fi

container_volumes="-v ${DCOS_COMMONS_DIRECTORY}:${WORK_DIR}"

if [ -n "${project}" ]; then
  # Mount $PROJECT_ROOT/frameworks into $WORK_DIR/frameworks.
  container_volumes="${container_volumes} -v ${PROJECT_ROOT}/frameworks:${WORK_DIR}/frameworks"

  # In the case of a project using dcos-commons as a submodule, the dcos-commons
  # git directory will be located in '.git/modules/dcos-commons' under the
  # project root instead of being located in the checked out dcos-commons
  # submodule's .git directory, i.e. 'dcos-commons/.git'.
  #
  # Because of this it is needed that the GIT_DIR and GIT_WORK_TREE environment
  # variables are set and point to the actual dcos-commons git directory
  # ('.git/modules/dcos-commons') so that git commands like 'git rev-parse HEAD'
  # work.
  #
  # The commands below cause '.git/modules/dcos-commons' to be mounted as a
  # volume, and set the environment variables.
  container_dcos_commons_git_dir="/dcos-commons-git-dir"
  container_volumes="${container_volumes} -v ${PROJECT_ROOT}/.git/modules/dcos-commons:${container_dcos_commons_git_dir}"
  cat >> "${env_file}" <<-EOF
		GIT_DIR=${container_dcos_commons_git_dir}
		GIT_WORK_TREE=${container_dcos_commons_git_dir}
	EOF
fi

if [ -z "${CLUSTER_URL}" ] && [ "${interactive}" == "true" ]; then
  CLUSTER_URL="$(dcos config show core.dcos_url)"
  echo "CLUSTER_URL not specified. Using attached cluster '${CLUSTER_URL}' in interactive mode"
fi

################################################################################
######################### Configure cluster SSH key ############################
################################################################################

if [ -f "${ssh_path}" ]; then
  container_volumes="${container_volumes} -v ${ssh_path}:/ssh/key"
else
  if [ -n "${CLUSTER_URL}" ]; then
    # If the user is providing us with a cluster, we require the SSH key for that cluster.
    echo "SSH key not found at '${ssh_path}'. Use -p /path/to/id_rsa to customize this path."
    echo "An SSH key is required for communication with the provided CLUSTER_URL=${CLUSTER_URL}"
    exit 1
  fi
  # Don't need ssh key now: test_runner.sh will extract the key after cluster
  # launch.
fi

################################################################################
###################### Configure AWS credentials profile #######################
################################################################################

if [ -n "${aws_profile}" ]; then
  echo "Using provided --aws-profile: ${aws_profile}"
elif [ -n "${AWS_PROFILE}" ]; then
  echo "Using provided AWS_PROFILE: ${AWS_PROFILE}"
  aws_profile="${AWS_PROFILE}"
elif [ -f "${aws_credentials_path}" ]; then
  # If the credentials file has exactly one profile, use that profile.
  available_profiles="$(grep -oE '^\[\S+\]' "${aws_credentials_path}" | tr -d '[]')" # Find line(s) that look like "[profile]", remove "[]".
  available_profile_count="$(echo "${available_profiles}" | wc -l)"
  if [ "${available_profile_count}" == "1" ]; then
    aws_profile="${available_profiles}"
    echo "Using sole profile in ${aws_credentials_path}: ${aws_profile}"
  else
    echo "Expected 1 profile in '${aws_credentials_path}', found ${available_profile_count}: ${available_profiles}"
    echo "Please specify --aws-profile or \$AWS_PROFILE to select a profile"
    exit 1
  fi
else
  echo "No AWS profile specified, using 'default'"
  aws_profile="default"
fi

if [ -f "${aws_credentials_path}" ]; then
  aws_credentials_file_mount_source="${aws_credentials_path}"
else
  # CI environments may have AWS credentials in AWS_DEV_* envvars, map them to
  # AWS_*.
  if [ -n "${AWS_DEV_ACCESS_KEY_ID}" ] \
       && [ -n "${AWS_DEV_SECRET_ACCESS_KEY}" ]; then
    AWS_ACCESS_KEY_ID="${AWS_DEV_ACCESS_KEY_ID}"
    AWS_SECRET_ACCESS_KEY="${AWS_DEV_SECRET_ACCESS_KEY}"
  fi

  # Check AWS_* envvars for credentials, create temp creds file using those
  # credentials.
  if [ -n "${AWS_ACCESS_KEY_ID}" ] && [ -n "${AWS_SECRET_ACCESS_KEY}" ]; then
    echo "Writing AWS credentials to temporary file: ${tmp_aws_credentials_file}"
    cat > "${tmp_aws_credentials_file}" <<-EOF
			[${aws_profile}]
			aws_access_key_id = ${AWS_ACCESS_KEY_ID}
			aws_secret_access_key = ${AWS_SECRET_ACCESS_KEY}
		EOF
  else
    echo "Missing AWS credentials file (${aws_credentials_path}) and AWS env (AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY)"
    exit 1
  fi

  aws_credentials_file_mount_source="${tmp_aws_credentials_file}"
fi

container_volumes="${container_volumes} -v ${aws_credentials_file_mount_source}:/root/.aws/credentials:ro"

################################################################################
############################# Build ENV file ###################################
################################################################################

if [ -n "${TEAMCITY_VERSION}" ]; then
  # The teamcity python module treats present-but-empty as enabled. We must
  # therefore completely omit this envvar to disable teamcity handling.
  echo "TEAMCITY_VERSION=\"${TEAMCITY_VERSION}\"" >> "${env_file}"
fi

if [ -n "${azure_enabled}" ]; then
  cat >> "${env_file}" <<-EOF
		AZURE_CLIENT_ID="${AZURE_DEV_CLIENT_ID}"
		AZURE_CLIENT_SECRET="${AZURE_DEV_CLIENT_SECRET}"
		AZURE_TENANT_ID="${AZURE_DEV_TENANT_ID}"
		AZURE_STORAGE_ACCOUNT="${AZURE_DEV_STORAGE_ACCOUNT}"
		AZURE_STORAGE_KEY="${AZURE_DEV_STORAGE_KEY}"
	EOF
fi

cat >> "${env_file}" <<-EOF
	AWS_PROFILE=${aws_profile}
	CLUSTER_URL=${CLUSTER_URL}
	DCOS_ENTERPRISE=${enterprise}
	DCOS_FILES_PATH=${dcos_files_path}
	DCOS_LOGIN_PASSWORD=${DCOS_LOGIN_PASSWORD}
	DCOS_LOGIN_USERNAME=${DCOS_LOGIN_USERNAME}
	DCOS_SSH_USERNAME=${ssh_user}
	FRAMEWORK=${framework}
	PACKAGE_REGISTRY_ENABLED=$package_registry
	PYTEST_ARGS=${PYTEST_ARGS}
	PYTHONPATH=${WORK_DIR}/testing
	S3_BUCKET=${S3_BUCKET}
	SECURITY=${security}
	STUB_UNIVERSE_URL=${STUB_UNIVERSE_URL}
EOF

while read -r line; do
  # Prefix match, then strip prefix in env_file.
  if [[ "${line:0:8}" = "TEST_SH_" ]]; then
    echo "${line#TEST_SH_}" >> "${env_file}"
  fi
done < <(env)

if [ -n "${env_passthrough}" ]; then
  # If the -e flag is specified, add the ENVVAR lines for the comma-separated
  # list of envvars.
  for envvar_name in ${env_passthrough//,/ }; do
    echo "${envvar_name}" >> "${env_file}"
  done
fi

if [ -n "${env_file_input}" ]; then
  cat "${env_file_input}" >> "${env_file}"
fi

################################################################################
######################### Prepare and run command ##############################
################################################################################

docker pull "${docker_image}"

set +x

CMD="docker run
    --rm
    -t
    ${docker_interactive_arg}
    --env-file ${env_file}
    -w ${WORK_DIR}
    ${container_volumes}
    ${docker_options}
    ${docker_image}
    ${docker_command}"

echo "================================================================================"
echo "Docker command:"
# shellcheck disable=SC2001
echo -e "  $(echo "${CMD}" | sed 's/\([[:alpha:]]\) -v/\1\\n    -v/g')"
echo
echo "Environment in --env-file '${env_file}':"
while read -r line; do
  echo "  ${line}"
done < "${env_file}"
echo "================================================================================"

set -x

# shellcheck disable=SC2086
eval ${CMD}
