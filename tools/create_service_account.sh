set -x
MODE=

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

echo (Re)creating service account for account=$SERVICE_ACCOUNT_NAME secret=$SECRET_NAME

echo Install cli necessary for security...
dcos package install dcos-enterprise-cli

echo Create keypair...
dcos security org service-accounts keypair private-key.pem public-key.pem

echo Create service account...
dcos security org service-accounts delete "${SERVICE_ACCOUNT_NAME}" &> /dev/null
dcos security org service-accounts create -p public-key.pem -d "My service account" "${SERVICE_ACCOUNT_NAME}"

echo Create secret...
dcos security secrets delete "${SECRET_NAME}" &> /dev/null
dcos security secrets create-sa-secret ${MODE} private-key.pem "${SERVICE_ACCOUNT_NAME}" "${SECRET_NAME}"

echo Service account (re)created for account=$SERVICE_ACCOUNT_NAME secret=$SECRET_NAME
