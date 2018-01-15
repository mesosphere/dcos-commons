#!/usr/bin/env bash

# This script sets the Elasticsearch bootstrap password so that requests to the cluster work out of the box without
# requiring users to set passwords, while still recommending them to do so if they haven't already.
# The bootstrap password is ignored if users already set passwords.

set -x

readonly ELASTICSEARCH_PASSWORD="changeme" # can't get it from env because it's not exposed as TASKCFG_ALL.
readonly ELASTICSEARCH_PATH="${MESOS_SANDBOX}/elasticsearch-${ELASTIC_VERSION}"
readonly ELASTICSEARCH_KEYSTORE="${ELASTICSEARCH_PATH}/bin/elasticsearch-keystore"
readonly ELASTICSEARCH_BOOTSTRAP_PASSWORD_KEY="bootstrap.password"

if [[ -z "$(${ELASTICSEARCH_KEYSTORE} list | grep "${ELASTICSEARCH_BOOTSTRAP_PASSWORD_KEY}")" ]]; then
  echo "Setting Elasticsearch keystore bootstrap password"
  echo "${ELASTICSEARCH_PASSWORD}" | ${ELASTICSEARCH_KEYSTORE} add --stdin --silent "${ELASTICSEARCH_BOOTSTRAP_PASSWORD_KEY}"
else
  echo "Elasticsearch keystore bootstrap password already set."
fi

echo "Make sure to update your password if you haven't already."
