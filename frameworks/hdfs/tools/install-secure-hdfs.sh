#!/bin/bash

set -euo pipefail
set -x

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
SPARK_BUILD_DIR="${DIR}/../../"
S3_PATH=""
HDFS_PRIMARY="hdfs"
HTTP_PRIMARY="HTTP"
FRAMEWORK_NAME="hdfs"
DOMAIN="${FRAMEWORK_NAME}.autoip.dcos.thisdcos.directory"
REALM="LOCAL"
LINUX_USER="core"
KEYTAB_FILE="hdfs.keytab"

function add_kdc() {
    echo "Adding kdc marathon app..."
    # dcos marathon app add kdc.json

    echo "Waiting for app to run..."
    while true; do
        local TASKS_RUNNING=$(dcos marathon app list --json | jq ".[0].tasksRunning")
        if [[ ${TASKS_RUNNING} -eq 1 ]]; then
            break
        fi
    done

    sleep 10

    SLAVE_ID=$(dcos task --json | jq -r ".[0].slave_id")
    MASTER_PUBLIC_IP=$(curl --header "Authorization: token=$(dcos config show core.dcos_acs_token)" $(dcos config show core.dcos_url)/metadata | jq -r ".PUBLIC_IPV4")
    SLAVE_HOSTNAME=$(dcos node --json | jq -r ".[] | select(.id==\"${SLAVE_ID}\") | .hostname")

    echo "Getting docker container id..."
    DOCKER_PS_CMD="docker ps | sed -n '2p' | cut -d\" \" -f1"
    DOCKER_CONTAINER_ID=$(dcos node ssh --master-proxy --mesos-id=${SLAVE_ID} --option StrictHostKeyChecking=no "${DOCKER_PS_CMD}")
    echo "DOCKER_CONTAINER_ID=${DOCKER_CONTAINER_ID}"
}

function add_kerberos_principals() {
    echo "Adding Kerberos principals..."
    dcos node ssh --master-proxy --mesos-id=${SLAVE_ID} --option StrictHostKeyChecking=no "docker exec ${DOCKER_CONTAINER_ID} kadmin -l add --use-defaults --random-password ${HDFS_PRIMARY}/name-0-node.${DOMAIN}@${REALM}"
    dcos node ssh --master-proxy --mesos-id=${SLAVE_ID} --option StrictHostKeyChecking=no "docker exec ${DOCKER_CONTAINER_ID} kadmin -l add --use-defaults --random-password ${HTTP_PRIMARY}/name-0-node.${DOMAIN}@${REALM}"
    dcos node ssh --master-proxy --mesos-id=${SLAVE_ID} --option StrictHostKeyChecking=no "docker exec ${DOCKER_CONTAINER_ID} kadmin -l add --use-defaults --random-password ${HDFS_PRIMARY}/name-1-node.${DOMAIN}@${REALM}"
    dcos node ssh --master-proxy --mesos-id=${SLAVE_ID} --option StrictHostKeyChecking=no "docker exec ${DOCKER_CONTAINER_ID} kadmin -l add --use-defaults --random-password ${HTTP_PRIMARY}/name-1-node.${DOMAIN}@${REALM}"
    dcos node ssh --master-proxy --mesos-id=${SLAVE_ID} --option StrictHostKeyChecking=no "docker exec ${DOCKER_CONTAINER_ID} kadmin -l add --use-defaults --random-password ${HDFS_PRIMARY}/journal-0-node.${DOMAIN}@${REALM}"
    dcos node ssh --master-proxy --mesos-id=${SLAVE_ID} --option StrictHostKeyChecking=no "docker exec ${DOCKER_CONTAINER_ID} kadmin -l add --use-defaults --random-password ${HTTP_PRIMARY}/journal-0-node.${DOMAIN}@${REALM}"
    dcos node ssh --master-proxy --mesos-id=${SLAVE_ID} --option StrictHostKeyChecking=no "docker exec ${DOCKER_CONTAINER_ID} kadmin -l add --use-defaults --random-password ${HDFS_PRIMARY}/journal-1-node.${DOMAIN}@${REALM}"
    dcos node ssh --master-proxy --mesos-id=${SLAVE_ID} --option StrictHostKeyChecking=no "docker exec ${DOCKER_CONTAINER_ID} kadmin -l add --use-defaults --random-password ${HTTP_PRIMARY}/journal-1-node.${DOMAIN}@${REALM}"
    dcos node ssh --master-proxy --mesos-id=${SLAVE_ID} --option StrictHostKeyChecking=no "docker exec ${DOCKER_CONTAINER_ID} kadmin -l add --use-defaults --random-password ${HDFS_PRIMARY}/journal-2-node.${DOMAIN}@${REALM}"
    dcos node ssh --master-proxy --mesos-id=${SLAVE_ID} --option StrictHostKeyChecking=no "docker exec ${DOCKER_CONTAINER_ID} kadmin -l add --use-defaults --random-password ${HTTP_PRIMARY}/journal-2-node.${DOMAIN}@${REALM}"
    dcos node ssh --master-proxy --mesos-id=${SLAVE_ID} --option StrictHostKeyChecking=no "docker exec ${DOCKER_CONTAINER_ID} kadmin -l add --use-defaults --random-password ${HDFS_PRIMARY}/data-0-node.${DOMAIN}@${REALM}"
    dcos node ssh --master-proxy --mesos-id=${SLAVE_ID} --option StrictHostKeyChecking=no "docker exec ${DOCKER_CONTAINER_ID} kadmin -l add --use-defaults --random-password ${HTTP_PRIMARY}/data-0-node.${DOMAIN}@${REALM}"
    dcos node ssh --master-proxy --mesos-id=${SLAVE_ID} --option StrictHostKeyChecking=no "docker exec ${DOCKER_CONTAINER_ID} kadmin -l add --use-defaults --random-password ${HDFS_PRIMARY}/data-1-node.${DOMAIN}@${REALM}"
    dcos node ssh --master-proxy --mesos-id=${SLAVE_ID} --option StrictHostKeyChecking=no "docker exec ${DOCKER_CONTAINER_ID} kadmin -l add --use-defaults --random-password ${HTTP_PRIMARY}/data-1-node.${DOMAIN}@${REALM}"
    dcos node ssh --master-proxy --mesos-id=${SLAVE_ID} --option StrictHostKeyChecking=no "docker exec ${DOCKER_CONTAINER_ID} kadmin -l add --use-defaults --random-password ${HDFS_PRIMARY}/data-2-node.${DOMAIN}@${REALM}"
    dcos node ssh --master-proxy --mesos-id=${SLAVE_ID} --option StrictHostKeyChecking=no "docker exec ${DOCKER_CONTAINER_ID} kadmin -l add --use-defaults --random-password ${HTTP_PRIMARY}/data-2-node.${DOMAIN}@${REALM}"
    dcos node ssh --master-proxy --mesos-id=${SLAVE_ID} --option StrictHostKeyChecking=no "docker exec ${DOCKER_CONTAINER_ID} kadmin -l add --use-defaults --random-password ${HDFS_PRIMARY}/zkfc-0-node.${DOMAIN}@${REALM}"
    dcos node ssh --master-proxy --mesos-id=${SLAVE_ID} --option StrictHostKeyChecking=no "docker exec ${DOCKER_CONTAINER_ID} kadmin -l add --use-defaults --random-password ${HDFS_PRIMARY}/zkfc-1-node.${DOMAIN}@${REALM}"
}

function create_keytab_file() {
    echo "Creating ${KEYTAB_FILE}..."
    dcos node ssh --master-proxy --mesos-id=${SLAVE_ID} "docker exec ${DOCKER_CONTAINER_ID} kadmin -l ext -k ${KEYTAB_FILE} ${HDFS_PRIMARY}/name-0-node.${DOMAIN}@${REALM} ${HTTP_PRIMARY}/name-0-node.${DOMAIN}@${REALM} ${HDFS_PRIMARY}/name-1-node.${DOMAIN}@${REALM} ${HTTP_PRIMARY}/name-1-node.${DOMAIN}@${REALM} ${HDFS_PRIMARY}/journal-0-node.${DOMAIN}@${REALM} ${HTTP_PRIMARY}/journal-0-node.${DOMAIN}@${REALM} ${HDFS_PRIMARY}/journal-1-node.${DOMAIN}@${REALM} ${HTTP_PRIMARY}/journal-1-node.${DOMAIN}@${REALM} ${HDFS_PRIMARY}/journal-2-node.${DOMAIN}@${REALM} ${HTTP_PRIMARY}/journal-2-node.${DOMAIN}@${REALM} ${HDFS_PRIMARY}/data-0-node.${DOMAIN}@${REALM} ${HTTP_PRIMARY}/data-0-node.${DOMAIN}@${REALM} ${HDFS_PRIMARY}/data-1-node.${DOMAIN}@${REALM} ${HTTP_PRIMARY}/data-1-node.${DOMAIN}@${REALM} ${HDFS_PRIMARY}/data-2-node.${DOMAIN}@${REALM} ${HTTP_PRIMARY}/data-2-node.${DOMAIN}@${REALM} ${HDFS_PRIMARY}/zkfc-0-node.${DOMAIN}@${REALM} ${HDFS_PRIMARY}/zkfc-1-node.${DOMAIN}@${REALM}"

    echo "Copying ${KEYTAB_FILE} to the current working directory..."
    dcos node ssh --master-proxy --mesos-id=${SLAVE_ID} "docker cp ${DOCKER_CONTAINER_ID}:/${KEYTAB_FILE} /home/${LINUX_USER}/${KEYTAB_FILE}"
    dcos node ssh --master-proxy --leader --option StrictHostKeyChecking=no "scp ${LINUX_USER}@${SLAVE_HOSTNAME}:/home/${LINUX_USER}/${KEYTAB_FILE} /home/${LINUX_USER}/${KEYTAB_FILE}"
    scp ${LINUX_USER}@${MASTER_PUBLIC_IP}:/home/${LINUX_USER}/${KEYTAB_FILE} .
}

function upload_krb5_conf() {
    echo "Uploading krb5.conf to s3://${S3_BUCKET}/${S3_PATH}"
    aws s3 cp ./krb5.conf s3://${S3_BUCKET}/${S3_PATH} --acl public-read
}

function create_tls_artifacts() {
    # server key
    keytool -keystore server.jks -alias localhost -validity 365 -genkey

    # CA key, cert
    openssl req -new -x509 -keyout ca.key -out ca.crt -days 365

    # trust store
    keytool -keystore trust.jks -alias CARoot -import -file ca.crt

    # unsigned server cert
    keytool -keystore server.jks -alias localhost -certreq -file server.crt-unsigned

    # signed server cert
    openssl x509 -req -CA ca.crt -CAkey ca.key -in server.crt-unsigned -out server.crt -days 365 -CAcreateserial -passin pass:changeit

    # add CA cert, server cert to keystore (not sure why)
    keytool -keystore server.jks -alias CARoot -import -file ca.crt
    keytool -keystore server.jks -alias localhost -import -file server.crt
}

function create_secrets() {
    base64 hdfs.keytab > hdfs.keytab.base64
    base64 server.jks > server.jks.base64
    base64 trust.jks > trust.jks.base64

    dcos security secrets create /hdfs-keytab --value-file hdfs.keytab.base64
    dcos security secrets create /truststore --value-file trust.jks.base64
    dcos security secrets create /keystore --value-file server.jks.base64
}

function install_hdfs() {
    cat <<EOF > /tmp/hdfs-kerberos-options.json
{
  "hdfs": {
      "security": {
          "enabled": true,
          "kerberos": {
              "krb5_conf_uri": "https://${S3_BUCKET}.s3.amazonaws.com/${S3_PATH}krb5.conf",
              "keytab_secret_path": "/hdfs-keytab"
          },
          "tls": {
              "trust_store_secret_path": "/truststore",
              "key_store_secret_path": "/keystore"
          }
      },
      "hadoop_root_logger": "DEBUG,console"
  }
}
EOF

    echo "Installing Kerberized HDFS..."
    dcos package install --yes hdfs --options=/tmp/hdfs-kerberos-options.json
}

add_kdc
add_kerberos_principals
create_keytab_file
upload_krb5_conf
create_tls_artifacts
create_secrets
install_hdfs
