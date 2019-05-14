#!/usr/bin/env bash

set -exo pipefail

export PATH="${PATH}:/usr/sbin"

################################ nginx proxy ###################################

mkdir "${MESOS_SANDBOX}/nginx"

readonly ENV_VARS="\${MESOS_SANDBOX},\${PORT_KIBANA},\${PORT_PROXY},\${FRAMEWORK_NAME},\${MARATHON_APP_LABEL_DCOS_SERVICE_SCHEME}"

envsubst "${ENV_VARS}" < "${MESOS_SANDBOX}/nginx.conf.tmpl" > "${MESOS_SANDBOX}/nginx/nginx.conf"

nginx -c "${MESOS_SANDBOX}/nginx/nginx.conf"

################################# kibana #######################################

readonly KIBANA_PATH="${MESOS_SANDBOX}/kibana-${ELASTIC_VERSION}-linux-x86_64"
readonly KIBANA_YML_PATH="${KIBANA_PATH}/config/kibana.yml"

cat <<-EOF > "${KIBANA_YML_PATH}"
	elasticsearch.url: "${ELASTICSEARCH_URL}"
	elasticsearch.username: "${KIBANA_USER}"
	elasticsearch.password: "${KIBANA_PASSWORD}"

	server.host: 0.0.0.0
	server.port: "${PORT_KIBANA}"
	server.basePath: "/service/${FRAMEWORK_NAME}"
	server.rewriteBasePath: true

	logging.verbose: true

	xpack.security.encryptionKey: "${MESOS_FRAMEWORK_ID}"
	xpack.reporting.encryptionKey: "${MESOS_FRAMEWORK_ID}"
EOF

if [ "${KIBANA_ELASTICSEARCH_TLS}" = "true" ]; then
  cat <<-EOF >> "${KIBANA_YML_PATH}"
		elasticsearch.ssl.certificateAuthorities: ${MESOS_SANDBOX}/.ssl/ca-bundle.crt
	EOF
fi

exec "${MESOS_SANDBOX}/kibana-$ELASTIC_VERSION-linux-x86_64/bin/kibana"
