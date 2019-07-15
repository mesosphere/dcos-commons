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
dcos security org users grant kdc-admin 'dcos:secrets:list:default:/*' full
```

## Building the KDC Container

There are multiple flavors of KDC you can use:

* `Dockerfile.heimdal-alpine` - Heimdal (HL5) Kerberos, in a low-footprint image (~70MB)
* `Dockerfile.heimdal-centos7` - Heimdal (HL5) Kerberos, CentOS 7 image for compatibility with DC/OS (~250MB)
* `Dockerfile.mit-alpine` - MIT Kerberos, in a low-footprint image (~70MB)
* `Dockerfile.mit-centos7` - MIT Kerberos, CentOS 7 image for compatibility with DC/OS (~250MB)

You can build either version using:

```sh
docker build -t mesosphere/kdc:heimdal-centos7 -f Dockerfile.heimdal-centos7 .
```

## API Reference

This API operates in both "plain text" and "json" encoding based on the `Content-Type`header. The simplest use is with the "plain text" API like so:

### `POST /api/principals` - Add KDC Principals 

Plain text API:

```sh
curl -X POST --data-binary @/path/to/principals.txt http://server:8080/api/principals?secret=secret_name
```

JSON API:

```py
import requests

req = requests.post(
    "http://server:8080/api/principals",
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

### `GET /api/principals` - Enumerate KDC Principals 

Plain text API:

```sh
curl http://server:8080/api/principals?filter=*
```

JSON API:

```py
import requests

req = requests.get(
    "http://server:8080/api/principals",
    headers={"content-type":"application/json"},
    json={
        "filter": "*"
    }
)
```

### `DELETE /api/principals` - Remove KDC Principals 

Plain text API:

```sh
curl -X DELETE --data-binary @/path/to/principals.txt http://server:8080/api/principals?secret=secret_name
```

JSON API:

```py
import requests

req = requests.delete(
    "http://server:8080/api/principals",
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
