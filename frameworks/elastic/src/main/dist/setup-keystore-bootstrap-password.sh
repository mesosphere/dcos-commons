#!/usr/bin/env bash

# This script sets the Elasticsearch bootstrap password so that requests to the cluster work out of the box without
# requiring users to set passwords, while still recommending them to do so if they haven't already.
# The bootstrap password is ignored if users already set passwords.

readonly SCRIPT_NAME="$(basename "${BASH_SOURCE[0]}")"
readonly ELASTICSEARCH_PASSWORD="changeme" # can't get it from env because it's not exposed as TASKCFG_ALL.
readonly ELASTICSEARCH_PATH="${MESOS_SANDBOX}/elasticsearch-${ELASTIC_VERSION}"
readonly ELASTICSEARCH_KEYSTORE="${ELASTICSEARCH_PATH}/bin/elasticsearch-keystore"
readonly ELASTICSEARCH_BOOTSTRAP_PASSWORD_KEY="bootstrap.password"

function log {
  echo "${SCRIPT_NAME}: ${1-}" 2>&1
}

# No need to worry about passwords if X-Pack (and security, by extension) is disabled.
if [[ "$XPACK_ENABLED" = "false" ]]; then
    exit 0
fi

if [[ -z "$(${ELASTICSEARCH_KEYSTORE} list | grep "${ELASTICSEARCH_BOOTSTRAP_PASSWORD_KEY}")" ]]; then
  log "Setting Elasticsearch keystore bootstrap password"
  echo "${ELASTICSEARCH_PASSWORD}" | ${ELASTICSEARCH_KEYSTORE} add --stdin --silent "${ELASTICSEARCH_BOOTSTRAP_PASSWORD_KEY}"
else
  log "Elasticsearch keystore bootstrap password already set."
fi

log "Make sure to update your password if you haven't already."
