#!/bin/bash

SERVICE_ACCOUNT_NAME=service-acct
SECRET_NAME=secret
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

echo Setting up permissions for user=$LINUX_USER role=$ROLE

echo Role can register a framework...
curl -k -L -X PUT \
     -H "Authorization: token=${ACS_TOKEN}" \
     "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:framework:role:${ROLE}" \
     -d '{"description":"Allows ${ROLE} to register as a framework with the Mesos master"}' \
     -H 'Content-Type: application/json'

echo User can execute tasks...
curl -k -L -X PUT \
     -H "Authorization: token=${ACS_TOKEN}" \
     "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:task:user:${LINUX_USER}" \
     -d '{"description":"Allows ${LINUX_USER} to execute tasks"}' \
     -H 'Content-Type: application/json'

echo Role can reserve resources...
curl -k -L -X PUT \
     -H "Authorization: token=${ACS_TOKEN}" \
     "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:reservation:role:${ROLE}" \
     -d '{"description":"Allows ${ROLE} to reserve resources"}' \
     -H 'Content-Type: application/json'

echo Role can create volumes...
curl -k -L -X PUT \
     -H "Authorization: token=${ACS_TOKEN}" \
     "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:volume:role:${ROLE}" \
     -d '{"description":"Allows ${ROLE} to create volumes"}' \
     -H 'Content-Type: application/json'

echo Assign permissions...
curl -k -L -X PUT \
     -H "Authorization: token=${ACS_TOKEN}" \
     "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:framework:role:${ROLE}/users/${SERVICE_ACCOUNT_NAME}/create"

curl -k -L -X PUT \
     -H "Authorization: token=${ACS_TOKEN}" \
     "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:framework:role:${ROLE}/users/${SERVICE_ACCOUNT_NAME}/delete"

curl -k -L -X PUT \
     -H "Authorization: token=${ACS_TOKEN}" \
     "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:task:user:${LINUX_USER}/users/${SERVICE_ACCOUNT_NAME}/create"

curl -k -L -X PUT \
     -H "Authorization: token=${ACS_TOKEN}" \
     "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:task:user:${LINUX_USER}/users/${SERVICE_ACCOUNT_NAME}/delete"

curl -k -L -X PUT \
     -H "Authorization: token=${ACS_TOKEN}" \
     "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:reservation:role:${ROLE}/users/${SERVICE_ACCOUNT_NAME}/create"

curl -k -L -X PUT \
     -H "Authorization: token=${ACS_TOKEN}" \
     "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:reservation:role:${ROLE}/users/${SERVICE_ACCOUNT_NAME}/delete"

curl -k -L -X PUT \
     -H "Authorization: token=${ACS_TOKEN}" \
     "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:volume:role:${ROLE}/users/${SERVICE_ACCOUNT_NAME}/create"

curl -k -L -X PUT \
     -H "Authorization: token=${ACS_TOKEN}" \
     "${DCOS_URL}/acs/api/v1/acls/dcos:mesos:master:volume:role:${ROLE}/users/${SERVICE_ACCOUNT_NAME}/delete"

echo Permission setup completed for user=$LINUX_USER role=$ROLE
