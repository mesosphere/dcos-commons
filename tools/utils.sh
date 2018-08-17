#!/usr/bin/env bash
# Common functions and variables to be shared across multiple shell scripts.

aws_creds_path="${HOME}/.aws/credentials"

function info { echo "[INFO]" "[$BASH_LINENO]" "$@"; }

function teamcityEnvVariable {
  local name=$1
  local value=$2
  if [ -n "${TEAMCITY_VERSION}" ]; then
    echo "##teamcity[setParameter name='env.$name' value='$value']"
  fi
}

function parse_aws_credential_file() {
    file_path=${1:-$aws_creds_path}
    # Check the creds file. If there's exactly one profile, then use that profile.
    available_profiles=$(grep -oE '^\[\S+\]' ${file_path} | tr -d '[]') # find line(s) that look like "[profile]", remove "[]"
    available_profile_count=$(echo "$available_profiles" | wc -l)
    if [[ $((available_profile_count)) != 1 ]]; then
        echo "Expected 1 profile in $file_path, found $available_profile_count: ${available_profiles}"
        echo "Please specify \$AWS_PROFILE to select a profile"
        exit 1
    else
        echo ${available_profiles}
    fi
}

