#!/usr/bin/env bash
# Assumes terraform and jq are already installed.

set -o errexit -o pipefail

source "$(dirname "${BASH_SOURCE[0]}")/../../utils.sh"

CURRENT_DIR=$(pwd -P)
TERRAFORM_CONFIG_DIR="${CURRENT_DIR}/config_terraform"
TERRAFORM_CONFIG_TEMPLATE="${TERRAFORM_CONFIG_DIR}.json"
TERRAFORM_CLUSTER_PROFILE="desired_cluster_profile.tfvars"

: ${FRAMEWORK?"Need to set FRAMEWORK name"}

function clean {
    rm -rf ${TERRAFORM_CONFIG_DIR}
    mkdir -p ${TERRAFORM_CONFIG_DIR}
}

function populate_terraform_config() {
    CWD=$(pwd)
    cd ${TERRAFORM_CONFIG_DIR}
    # Look for Dev or Default AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY in the environment.
    AWS_ACCESS_KEY_ID=${AWS_DEV_ACCESS_KEY_ID:-${AWS_ACCESS_KEY_ID}}
    AWS_SECRET_ACCESS_KEY=${AWS_DEV_SECRET_ACCESS_KEY:-${AWS_SECRET_ACCESS_KEY}}
    if [[ (-z ${AWS_ACCESS_KEY_ID} || -z ${AWS_SECRET_ACCESS_KEY}) ]]; then
        if [[ -z ${AWS_PROFILE} ]]; then
            AWS_PROFILE=$(parse_aws_credential_file)
            info "Using AWS_PROFILE : $AWS_PROFILE"
        fi
    fi
    info "trouble : $AWS_PROFILE"
    #awk -F= '!a[$1]++' "${FRAMEWORK}.properties" ${TERRAFORM_CLUSTER_PROFILE} > "${TERRAFORM_CONFIG_DIR}/${TERRAFORM_CLUSTER_PROFILE}"
    cd ${CWD}
}

function create_cluster {
    CWD=$(pwd)
    cd ${TERRAFORM_CONFIG_DIR}
    terraform init -from-module git@github.com:mesosphere/enterprise-terraform-dcos//aws
    populate_terraform_config
    info "terraform vars after merging :"
    cat ${TERRAFORM_CLUSTER_PROFILE}
    terraform apply -var-file "${TERRAFORM_CLUSTER_PROFILE}" -auto-approve
    terraform output --json
    cd ${CWD}
}

function destroy_cluster {
    terraform destroy -var-file ${TERRAFORM_CONFIG_DIR}/desired_cluster_profile.tfvars -auto-approve
}

#function emit_success_metric {
#    channel="%DCOS_LAUNCH_CONFIG_CHANNEL%"
#    deploy_seconds=$1
#    success_post_body='
#    {
#        "series" :[
#            {
#                "metric":"$provider.attempt",
#                "points":[[$currenttime, $success]],
#                "type":"gauge",
#                "tags":["provider:$provider","channel:$channel","security:$SECURITY","enterprise:$DCOS_ENTERPRISE","launch_wait_seconds:$deploy_seconds"]
#            },
#            {
#                "metric":"$provider.success",
#                "points":[[$currenttime, 1]],
#                "type":"gauge",
#                "tags":["provider:$provider","channel:$channel","security:$SECURITY","enterprise:$DCOS_ENTERPRISE","launch_wait_seconds:$deploy_seconds"]
#            },
#            {
#                "metric":"$provider.wait_time",
#                "points":[[$currenttime, $deploy_seconds]],
#                "type":"gauge",
#                "tags":["provider:$provider","channel:$channel","security:$SECURITY","enterprise:$DCOS_ENTERPRISE","success:$success"]
#            }
#        ]
#    }'
#    echo ${success_post_body} | curl -X POST -H "Content-Type: application/json" -d @-
#    rm
#}
#
#
#function emit_failure_metric {
#
#
#}

function init {
    clean
    #START_TIME=$SECONDS
    create_cluster
    #emit_success_metric $(($SECONDS - $START_TIME))
}

######################### Delegates to subcommands or runs main, as appropriate
if [[ ${1:-} ]] && declare -F | cut -d' ' -f3 | fgrep -qx -- "${1:-}"
then "$@"
else init
fi
