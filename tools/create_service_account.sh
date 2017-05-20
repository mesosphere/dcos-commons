set -x
MODE=

if [ "$#" -ge 2 ]; then
    # Set some cluster configs if they are passed in.
    echo At least 2 arguments, must be dcos_url and acs_token
    DCOS_URL=$1
    ACS_TOKEN=$2

    dcos config set core.dcos_url $DCOS_URL
    dcos config set core.dcos_acs_token $ACS_TOKEN
    dcos config set core.ssl_verify false
fi

while [ ! $# -eq 0 ]
do
    case "$1" in
        --strict | -s)
            MODE="--strict"
            ;;
    esac
    shift
done


SERVICE_ACCOUNT_NAME=service-acct
SECRET_NAME=secret

echo Creating service account for account=$SERVICE_ACCOUNT_NAME secret=$SECRET_NAME

echo Install cli necessary for security...
if ! dcos package install dcos-enterprise-cli --package-version=1.0.7; then
    echo "Failed to install dcos-enterprise cli extension" >&2
    exit 1
fi

echo Create keypair...
if ! dcos security org service-accounts keypair private-key.pem public-key.pem; then
    echo "Failed to create keypair for testing service account" >&2
    exit 1
fi

echo Create service account...
dcos security org service-accounts delete "${SERVICE_ACCOUNT_NAME}" &> /dev/null
if ! dcos security org service-accounts create -p public-key.pem -d "My service account" "${SERVICE_ACCOUNT_NAME}"; then
    echo "Failed to create service account '${SERVICE_ACCOUNT_NAME}'" >&2
    exit 1
fi

echo Create secret...
dcos security secrets delete "${SECRET_NAME}" &> /dev/null
if ! dcos security secrets create-sa-secret ${MODE} private-key.pem "${SERVICE_ACCOUNT_NAME}" "${SECRET_NAME}"; then
    echo "Failed to create secret '${SECRET_NAME}' for service account '${SERVICE_ACCOUNT_NAME}'" >&2
    exit 1
fi

echo Service account created for account=$SERVICE_ACCOUNT_NAME secret=$SECRET_NAME
