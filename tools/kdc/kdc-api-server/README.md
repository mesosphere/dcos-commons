# KDC API Server

This is a lightweight go-lang web-server that enables configuring KDC accounts over a REST endpoint.

## Creating a kdc-admin account

In order for the KDC API Server to work, it requires a DC/OS service account, with minimum required permissions the access the secrets store.

```sh
# Prepare
dcos package install dcos-enterprise-cli --yes
dcos security org service-accounts keypair /tmp/priv.pem /tmp/pub.pem

# Create a service account for kdc admin
dcos security org service-accounts create -p /tmp/pub.pem -d "kdc-admin-sa" kdc-admin
dcos security secrets create-sa-secret --strict /tmp/priv.pem kdc-admin "/kdc-admin"

# Allow the service account to manage secrets
dcos security org users grant kdc-admin 'dcos:secrets:default:/*' full
dcos security org users grant kdc-admin 'dcos:secrets:list:default:/*' read
```

## API Reference

### `POST /api/add` - Add KDC Principals 

This API operates in both "plain text" and "json" encoding based on the `Content-Type` header. The simplest use is with the "plain text" API like so:

```sh
curl --data-binary @/path/to/principals.txt http://server:8080/api/add?secret=secret_name
```

Alternatively, using JSON encoding:

```py
import requests

req = requests.POST(
    "http://server:8080/api/add",
    headers={"content-type":"application/json"},
    json={
        "secret": "secret_name",
        "principals": [
            "principal-1",
            "principal-2",
            "principal-3"
        ]
    }
)
```

### `POST /api/list` - Enumerate KDC Principals 

This API operates in both "plain text" and "json" encoding based on the `Content-Type`header. The simplest use is with the "plain text" API like so:

```sh
curl http://server:8080/api/list?filter=*
```

Alternatively using JSON encoding:

```py
import requests

req = requests.POST(
    "http://server:8080/api/list",
    headers={"content-type":"application/json"},
    json={
        "filter": "*"
    }
)
```

