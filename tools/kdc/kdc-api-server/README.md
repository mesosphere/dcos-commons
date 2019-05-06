# KDC API Server

This is a lightweight go-lang web-server that enables configuring KDC accounts over a REST endpoint.

## Creating a kdc-admin account

```sh
# Prepare
dcos package install dcos-enterprise-cli --yes
dcos security org service-accounts keypair /tmp/priv.pem /tmp/pub.pem

# Create a service account for kdc admin
dcos security org service-accounts create -p /tmp/pub.pem -d "kdc-admin-sa" kdc-admin
dcos security secrets create-sa-secret --strict /tmp/priv.pem kdc-admin "/kdc-admin"

# Allow the service account to manage secrets
dcos security org users grant kdc-admin dcos:secrets:default:/data-services full
dcos security org users grant kdc-admin dcos:secrets:list:default:/data-services create
```
