#!/bin/bash

SERVICE_ACCOUNT_NAME=service-acct
LINUX_USER=$1
ROLE=$2

if [ -n "$CLUSTER_URL" ]
then
    # CI
    DCOS_URL=$CLUSTER_URL
else
    DCOS_URL=$(dcos config show core.dcos_url)
fi

if [ -n "$CLUSTER_AUTH_TOKEN" ]
then
    # CI
    ACS_TOKEN=$CLUSTER_AUTH_TOKEN
else
    ACS_TOKEN=$(dcos config show core.dcos_acs_token)
fi



function grant_registration() {
    echo "Authorizing ${SERVICE_ACCOUNT_NAME} to register as a Mesos framework with role=${ROLE}"

    curl -k -L -X PUT \
         -H "Authorization: token=${ACS_TOKEN}" \
         "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:framework:role:${ROLE}" \
         -d '{"description":"Register with the Mesos master with role=${ROLE}"}' \
         -H 'Content-Type: application/json'
    curl -k -L -X PUT \
         -H "Authorization: token=${ACS_TOKEN}" \
         "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:framework:role:${ROLE}/users/${SERVICE_ACCOUNT_NAME}/create"
}

function grant_task_execution() {
    echo "Authorizing ${SERVICE_ACCOUNT_NAME} to execute Mesos tasks as user=${LINUX_USER}"
    curl -k -L -X PUT \
         -H "Authorization: token=${ACS_TOKEN}" \
         "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:task:user:${LINUX_USER}" \
         -d '{"description":"Execute Mesos tasks as user=${LINUX_USER}"}' \
         -H 'Content-Type: application/json'
    curl -k -L -X PUT \
         -H "Authorization: token=${ACS_TOKEN}" \
         "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:task:user:${LINUX_USER}/users/${SERVICE_ACCOUNT_NAME}/create"

    # XXX 1.10 curerrently requires this mesos:agent permission as well as
    # mesos:task permission.  unclear if this will be ongoing requirement.
    # See DCOS-15682
    curl -k -L -X PUT \
         -H "Authorization: token=${ACS_TOKEN}" \
         "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:agent:task:user:${LINUX_USER}" \
         -d '{"description":"Execute Mesos tasks as user=${LINUX_USER}"}' \
         -H 'Content-Type: application/json'
    curl -k -L -X PUT \
         -H "Authorization: token=${ACS_TOKEN}" \
         "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:agent:task:user:${LINUX_USER}/users/${SERVICE_ACCOUNT_NAME}/create"

    # In order for the Spark Dispatcher to register with Mesos as
    # root, we must launch the dispatcher task as root.  The other
    # frameworks are launched as nobody, but then register as
    # service.user, which defaults to root
    echo "Authorizing dcos_marathon to execute Mesos tasks as root"
    curl -k -L -X PUT \
         -H "Authorization: token=${ACS_TOKEN}" \
         "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:task:user:root" \
         -d '{"description":"Execute Mesos tasks as user=root"}' \
         -H 'Content-Type: application/json'
    curl -k -L -X PUT \
         -H "Authorization: token=${ACS_TOKEN}" \
         "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:task:user:root/users/dcos_marathon/create"
    # XXX see above
    curl -k -L -X PUT \
         -H "Authorization: token=${ACS_TOKEN}" \
         "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:agent:task:user:root" \
         -d '{"description":"Execute Mesos tasks as user=root"}' \
         -H 'Content-Type: application/json'
    curl -k -L -X PUT \
         -H "Authorization: token=${ACS_TOKEN}" \
         "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:agent:task:user:root/users/dcos_marathon/create"
}

function grant_resources {
    echo "Authorizing ${SERVICE_ACCOUNT_NAME} to reserve Mesos resources with role=${ROLE}"
    curl -k -L -X PUT \
         -H "Authorization: token=${ACS_TOKEN}" \
         "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:reservation:role:${ROLE}" \
         -d '{"description":"Reserve Mesos resources with role=${ROLE}"}' \
         -H 'Content-Type: application/json'
    curl -k -L -X PUT \
         -H "Authorization: token=${ACS_TOKEN}" \
         "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:reservation:role:${ROLE}/users/${SERVICE_ACCOUNT_NAME}/create"

    echo "Authorizing ${SERVICE_ACCOUNT_NAME} to unreserve Mesos resources with principal=${SERVICE_ACCOUNT_NAME}"
    curl -k -L -X PUT \
         -H "Authorization: token=${ACS_TOKEN}" \
         "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:reservation:principal:${SERVICE_ACCOUNT_NAME}" \
         -d '{"description":"Reserve Mesos resources with principal=${SERVICE_ACCOUNT_NAME}"}' \
         -H 'Content-Type: application/json'
    curl -k -L -X PUT \
         -H "Authorization: token=${ACS_TOKEN}" \
         "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:reservation:principal:${SERVICE_ACCOUNT_NAME}/users/${SERVICE_ACCOUNT_NAME}/delete"
}

function grant_volumes {
    echo "Authorizing ${SERVICE_ACCOUNT_NAME} to create Mesos volumes with role=${ROLE}"
    curl -k -L -X PUT \
         -H "Authorization: token=${ACS_TOKEN}" \
         "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:volume:role:${ROLE}" \
         -d '{"description":"Create Mesos volumes with role=${ROLE}"}' \
         -H 'Content-Type: application/json'
    curl -k -L -X PUT \
         -H "Authorization: token=${ACS_TOKEN}" \
         "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:volume:role:${ROLE}/users/${SERVICE_ACCOUNT_NAME}/create"

    echo "Authorizing ${SERVICE_ACCOUNT_NAME} to delete Mesos volumes with principal=${SERVICE_ACCOUNT_NAME}"
    curl -k -L -X PUT \
         -H "Authorization: token=${ACS_TOKEN}" \
         "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:volume:principal:${SERVICE_ACCOUNT_NAME}" \
         -d '{"description":"Create Mesos volumes with principal=${SERVICE_ACCOUNT_NAME}"}' \
         -H 'Content-Type: application/json'
    curl -k -L -X PUT \
         -H "Authorization: token=${ACS_TOKEN}" \
         "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:volume:principal:${SERVICE_ACCOUNT_NAME}/users/${SERVICE_ACCOUNT_NAME}/delete"
}

echo "Granting permissions to ${SERVICE_ACCOUNT_NAME}..."
grant_registration
grant_task_execution
grant_resources
grant_volumes
echo "Permission setup completed for ${SERVICE_ACCOUNT_NAME}"
