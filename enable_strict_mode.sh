#!/bin/sh
if ! [ -z $DEBUG ]; then
    set -x
    DEBUG_CURL_OPTS=-s
fi

framework_name="$1"
framework_user="nobody"

usage () {
    cat <<EOF
Purpose:
Install $framework_name into a strict DC/OS cluster, ensuring all prerequisites are met
prior to executing "dcos package install ≤framework_name≥".

Environment:
FRAMEWORK_NAME     $framework_name
FRAMEWORK_USER     $framework_user
SECURITY           strict
MESOS_API_VERSION  api version for mesos, defaults to 'V0' (libmesos) due to a
                   known limitation as of 201705 whereby 'V1' (http) does not
                   correctly authenitcate.

Prerequisite(s):
1. An authenticated dcos "session", meaning 'dcos auth login' has been performed.

Usage:
./enable_strict_mode <framework_name>

Supported DC/OS Version(s):
1.10 
EOF
}

if [ $# -ne 1 ] || [ $1 == "usage" ] || [ ! -d "frameworks/$framework_name" ] ; then
  usage
  exit
fi

dcos_username=bootstrapuser
dcos_password=deleteme
local_staging_path="$(pwd)/frameworks/$framework_name/staging"
options_file_path="$local_staging_path/$framework_name.json"

dcos_acs_token=$(dcos config show core.dcos_acs_token)
dcos_url=$(dcos config show core.dcos_url)
dcos_ca_crt=${local_staging_path}/dcos-ca.crt

if [ -z $MESOS_API_VERSION ]; then export MESOS_API_VERSION='V0'; fi

create_staging_path() {
  echo "Creating framework-local staging path"
  mkdir -p $local_staging_path
}

dcos_create_key_pair() {
  dcos security org service-accounts keypair ${framework_name}-private-key.pem ${framework_name}-public-key.pem
  res=$(ls -l | grep "${framework_name}-private-key.pem\|${framework_name}-public-key.pem" | wc -l)
  if [ $res -ne 2 ]; then
    echo "Failed during creationg of key pair"
    exit 1
  fi
  mv ${framework_name}-private-key.pem $local_staging_path/${framework_name}-private-key.pem
  mv ${framework_name}-public-key.pem $local_staging_path/${framework_name}-public-key.pem
}

dcos_create_service_account () {
    echo "Creating service account"
    framework_name=$1
    res=$(dcos security org service-accounts create -p $local_staging_path/${framework_name}-public-key.pem -d "${framework_name} service account" ${framework_name}-principal 2>&1)
    if [ $? -ne 0 ]; then
      if [[ $res == *"already exists"* ]]; then
        echo "User with id \`${framework_name}-principal\` already exists. Proceeding..."
      else
        echo $res
        exit $?
      fi
    fi
}

dcos_store_pvt_key () {
    echo "Storing private key"
    framework_name=$1
    # can't use error code to branch on since warnings and errors are reported as errors.
    # strip response to only 'Error: ' line(s) b/c usage is nice, but not helpful here.
    res=$(dcos security secrets create-sa-secret --strict \
       $local_staging_path/${framework_name}-private-key.pem ${framework_name}-principal ${framework_name}/${framework_name}-secret 2>&1 \
       |awk '/Error:.*/{print;}')
    ec=$?
    case ${res} in
        *HTTP\ 409:*)
            2&> echo "WARN: ${framework_name} private key is already stored."
            ;;
        *)
            if [ $ec != 0 ]; then
                2&> echo "${res}"
                exit 1
            fi
    esac
    rm -rf $local_staging_path/${framework_name}-private-key.pem
}

dcos_obtain_ca_crt () {
    echo "Obtaining CA cert"
    curl -k -v ${dcos_url}/ca/dcos-ca.crt -o ${dcos_ca_crt} $DEBUG_CURL_OPTS >/dev/null 2>&1
}

http_exit_on_http_error () {
    uri="$1"
    res="$2"
    http_status=$(printf "${res}" |head -n 1|awk -F' ' '{print $2;}')
    is_error=0
    case ${http_status} in
        409) ;;
        4**) is_error=1;;
        5**) is_error=1;;
        *) ;;
    esac
    if [ $is_error != 0 ]; then
        echo "${res}"
        exit ${http_status}
    fi
}

http_url_concat () {
    url_base="$1"
    url_branch="$2"

    # if the base and branch end and start w/ a slash, avoid the double slash
    # by trimming the leading slash from the branch.
    case ${url_branch} in
        /*)
            case ${url_base} in
                */)
                    # trim leading / from branch
                    url_branch=$(echo "${url_branch}" |sed 's/^\///')
            esac
    esac
    # ensure that there is a slash between the base and branch.
    case ${url_base} in
        */) ;;
        *)
            case ${url_branch} in
                /*) ;;
                *)
                    url_branch="/${url_branch}"
            esac
            ;;
    esac

    echo "${url_base}${url_branch}"
}

dcos_secure_put () {
    url_branch="$1"
    request_body="$2"

    uri=$(http_url_concat ${dcos_url} ${url_branch})
    res=$(curl -i -X PUT --cacert ${dcos_ca_crt} \
        -H "Authorization: token=${dcos_acs_token}" \
        ${uri} \
        -d "${request_body}" \
        -H 'Content-Type: application/json' \
        $DEBUG_CURL_OPTS 2>/dev/null)
    ec=$?
    if [ $ec != 0 ]; then exit $ec; fi
    http_exit_on_http_error ${uri} "$res"
}

dcos_acl_grant () {
    echo "Granting $1"
    acl=$1
    dcos_secure_put \
        "acs/api/v1/acls/${acl}" \
        '{"description":"Allows the thing to do its thing."}'
}


dcos_racl_grant () {
    acl=$1
    dcos_secure_put \
        "acs/api/v1/acls/${acl}/create" \
        '{}'
}

dcos_racl_revoke () {
    acl=$1
    dcos_secure_put \
        "acs/api/v1/acls/${acl}/delete" \
        '{}'
}

dcos_grant_service_account_perms () {
    echo "Granting service account permissions"
    framework_name=$1
    framework_user=$2

    acls=$(cat <<EOF
dcos:mesos:agent:task:appid
dcos:mesos:master:task:user:${framework_user}
dcos:mesos:agent:task:user:${framework_user}
dcos:mesos:master:framework:role:${framework_name}-role
dcos:mesos:master:reservation:role:${framework_name}-role
dcos:mesos:master:volume:role:${framework_name}-role
dcos:mesos:master:reservation:principal:${framework_name}-principal
dcos:mesos:master:volume:principal:${framework_name}-principal

dcos:mesos:master:framework:role:${framework_name}-role/users/${framework_name}-principal/create
dcos:mesos:master:reservation:role:${framework_name}-role/users/${framework_name}-principal/create
dcos:mesos:master:volume:role:${framework_name}-role/users/${framework_name}-principal/create
dcos:mesos:master:task:user:${framework_user}/users/${framework_name}-principal/create
dcos:mesos:master:reservation:principal:${framework_name}-principal/users/${framework_name}-principal/delete
dcos:mesos:master:volume:principal:${framework_name}-principal/users/${framework_name}-principal/delete
EOF
)

    for acl in ${acls}; do
        dcos_acl_grant ${acl}
    done
}


dcos_create_options_file () {
    echo "Creating options file"
    framework_name=$1
    framework_user=$2

    cat >${options_file_path} <<EOF
{
  "service": {
    "principal": "${framework_name}-principal",
    "secret_name": "${framework_name}/${framework_name}-secret",
    "user": "$framework_user"
  }
}
EOF
}

rm_staging_area() {
  framework_name=$1
  rm -rf $local_staging_area
}

echo "strictor is off to setup security bits for $framework_name using MESOS_API_VERSION: ${MESOS_API_VERSION}"
create_staging_path
dcos_create_key_pair
dcos_obtain_ca_crt
dcos_create_service_account $framework_name
dcos_store_pvt_key $framework_name
dcos_grant_service_account_perms $framework_name $framework_user
dcos_create_options_file $framework_name $framework_user
rm_staging_area $framework_name
cat <<EOF
Security accounts and permissions are now created for [${framework_name}].
To install an instance of the framework, execute the following:
dcos package install --yes ${framework_name} --options=$options_file_path
EOF

