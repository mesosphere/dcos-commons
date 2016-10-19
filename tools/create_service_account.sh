SERVICE_ACCOUNT_NAME=service-acct
SECRET_NAME=secret

echo Install cli necessary for security
dcos package install dcos-enterprise-cli

echo Create keypair
dcos security org service-accounts keypair private-key.pem public-key.pem

echo Create service account
dcos security org service-accounts delete "${SERVICE_ACCOUNT_NAME}"
dcos security org service-accounts create -p public-key.pem -d "My service account" "${SERVICE_ACCOUNT_NAME}"

echo Create secret
dcos security secrets delete "${SECRET_NAME}"
dcos security secrets create-sa-secret private-key.pem "${SERVICE_ACCOUNT_NAME}" "${SECRET_NAME}"

