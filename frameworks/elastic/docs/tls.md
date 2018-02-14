---
layout: layout.pug
navigationTitle:
excerpt:
title: Configuring TLS
menuWeight: 25

---

# Encrypting Network Communication

Elasticsearch requires
[some work](https://www.elastic.co/guide/en/elasticsearch/reference/current/configuring-tls.html)
to get TLS working. The Elastic service takes care of setting everything up when transport encryption is enabled. It will generate and deploy certificates and enable SSL both on the transport and HTTP layers by default.

The following steps describe getting an Elastic service with TLS from scratch. You might already have key pairs, service
accounts and secrets that you could already use. In that case only run the steps you need.

## Install the Enterprise DC/OS CLI

```
dcos package install --yes dcos-enterprise-cli
```

## Create key pair

This generates `elastic.private.pem` and `elastic.public.pem`.

```
dcos security org service-accounts keypair elastic.private.pem elastic.public.pem
```

## Create service account

The service account name that is configured for your service (`service.service_account` in the service JSON
configuration or the `service account` text field in the "Edit Configuration" UI) must match the actual service account
name, which we're calling `elastic` in this case.

```
dcos security org service-accounts create -p elastic.public.pem -d "Elastic service account" elastic
```

## Create service account secret

This stores the private key as a [DC/OS secret](https://docs.mesosphere.com/1.10/security/ent/secrets/) named `elastic-secret`.

```
dcos security secrets create-sa-secret elastic.private.pem elastic elastic-secret
```

## Add service account to `superusers` group

```
dcos security org groups add_user superusers elastic
```

## Install or update Elastic with TLS enabled

Elasticsearch
[requires that X-Pack is installed](https://www.elastic.co/guide/en/elasticsearch/reference/current/configuring-tls.html)
to enable transport encryption. Make sure `elasticsearch.xpack_enabled` is set to `true` or nodes won't be able to
start.

### Install

Make sure to replace `$your_service_name` by your actual service name.

```bash
cat <<EOF > elastic_with_tls.json
{
  "service": {
    "name": "$your_service_name",
    "service_account": "elastic",
    "service_account_secret": "elastic-secret",
    "security": {
      "transport_encryption": {
        "enabled": true
      }
    }
  },
  "elasticsearch": {
    "xpack_enabled": true
  }
}
EOF
```

```
dcos package install --yes --options=elastic_with_tls.json beta-elastic
```

### Or update a running service with TLS turned off

Updating requires a `parallel` strategy due to a know
[Elasticsearch limitation](https://github.com/mesosphere/dcos-commons/blob/e4b2ab5d82a6cda31815fc1224d1eca768513aa9/frameworks/elastic/docs/limitations.md#toggling-tls-requires-doing-a-full-cluster-restart).

```bash
cat <<EOF > elastic_with_tls.json
{
  "service": {
    "name": "$your_service_name",
    "service_account": "elastic",
    "service_account_secret": "elastic-secret",
    "update_strategy": "parallel",
    "security": {
      "transport_encryption": {
        "enabled": true
      }
    }
  },
  "elasticsearch": {
    "xpack_enabled": true
  }
}
EOF
```

```
dcos beta-elastic --name=$your_service_name update start --options=elastic_with_tls.json
```

## Now you can only access your Elasticsearch cluster through HTTPS

```bash
dcos task exec -it coordinator-0-node curl -k -u elastic:changeme https://coordinator.$your_service_name.l4lb.thisdcos.directory:9200 
{
  "name" : "coordinator-0-node",
  "cluster_name" : "beta-elastic",
  "cluster_uuid" : "L99lgy3jQMe2Xo_6m6CPSg",
  "version" : {
    "number" : "5.6.5",
    "build_hash" : "6a37571",
    "build_date" : "2017-12-04T07:50:10.466Z",
    "build_snapshot" : false,
    "lucene_version" : "6.6.1"
  },
  "tagline" : "You Know, for Search"
}
```
