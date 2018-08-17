#!/usr/bin/env bash
# Assumes terraform and jq are already installed.

set -o errexit -o pipefail -o xtrace

source "$(dirname "${BASH_SOURCE[0]}")/../../utils.sh"

CURRENT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
TERRAFORM_CONFIG_DIR="${CURRENT_DIR}/config_terraform"
TERRAFORM_CONFIG_TEMPLATE="${TERRAFORM_CONFIG_DIR}.json"
TERRAFORM_CLUSTER_PROFILE="${TERRAFORM_CONFIG_DIR}/desired_cluster_profile.tfvars"

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
    else
        # Set AWS_PROFILE to empty as aws key id and secret are found.
        AWS_PROFILE=""
    fi

    if [[ x"$DCOS_ENTERPRISE" == x"true" ]]; then
        : ${DCOS_SECURITY?"Set DCOS_SECURITY to one of (permissive, strict, disabled) for EE clusters"}
        : ${DCOS_LICENSE?"Set DCOS_LICENSE with a valid license for DCOS_VERSION ${DCOS_VERSION} EE cluster"}
    else
        info "Skipping enterprise config as DCOS_ENTERPRISE is set to [$DCOS_ENTERPRISE]"
        DCOS_SECURITY=""
        DCOS_LICENSE=""
    fi

    sed \
        -e "s/{FRAMEWORK}/${FRAMEWORK?"Set FRAMEWORK to configure cluster size"}/" \
        -e "s/{DCOS_VERSION}/${DCOS_VERSION?"Set DCOS_VERSION to create a cluster"}/" \
        -e "s/{AWS_SSH_KEY_NAME}/${AWS_SSH_KEY_NAME?"Set AWS_SSH_KEY_NAME to create cluster (Use \"default\" for ccm)"}/" \
        -e "s/{AWS_ACCESS_KEY_ID}/${AWS_ACCESS_KEY_ID}/" \
        -e "s/{AWS_SECRET_ACCESS_KEY}/${AWS_SECRET_ACCESS_KEY}/" \
        -e "s/{AWS_PROFILE}/${AWS_PROFILE}/" \
        -e "s/{DCOS_SECURITY}/${DCOS_SECURITY}/" \
        -e "s/{DCOS_LICENSE}/${DCOS_LICENSE}/" \
        ${TERRAFORM_CONFIG_TEMPLATE} \
        > "${TERRAFORM_CLUSTER_PROFILE}.json"
    cd ${CWD}
}

function create_cluster {
    CWD=$(pwd)
    cd ${TERRAFORM_CONFIG_DIR}
    terraform init -from-module git@github.com:mesosphere/enterprise-terraform-dcos//aws
    #terraform init -from-module git@github.com:dcos/terraform-dcos//aws

    # Create a tfvars file from the json file.
    json_file="${TERRAFORM_CLUSTER_PROFILE}.json"
    info "Terraform config being used : $(cat ${json_file})"
    jq -r '.defaults * .frameworks[.frameworks.framework] | to_entries[] | (.key) + "=\"" + (.value|strings) +"\""' ${json_file} >> ${TERRAFORM_CLUSTER_PROFILE}
    # TODO flavor based selector for open vs ee.
    jq -r '.defaults.enterprise | to_entries[] | (.key) + "=\"" + (.value|strings) +"\""' ${json_file} >> ${TERRAFORM_CLUSTER_PROFILE}
    terraform apply -var-file "${TERRAFORM_CLUSTER_PROFILE}" -auto-approve
    master_ip=terraform output --json | jq -r '."Master ELB Address".value'
    teamcityEnvVariable "CLUSTER_URL" ${master_ip}
    cd ${CWD}
}

function destroy_cluster {
    CWD=$(pwd)
    cd ${TERRAFORM_CONFIG_DIR}
    terraform destroy -var-file ${TERRAFORM_CLUSTER_PROFILE} -auto-approve
    cd ${CWD}
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
    populate_terraform_config
    create_cluster
    #emit_success_metric $(($SECONDS - $START_TIME))
}

######################### Delegates to subcommands or runs main, as appropriate
if [[ ${1:-} ]] && declare -F | cut -d' ' -f3 | fgrep -qx -- "${1:-}"
then "$@"
else init
fi
