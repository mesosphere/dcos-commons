---
layout: layout.pug
navigationTitle:
excerpt:
title: Security
menuWeight: 22
---

# DC/OS Apache HDFS Security

The DC/OS Apache HDFS service supports HDFS's native transport encryption, authentication, and authorization mechanisms. The service provides automation and orchestration to simplify the usage of these important features.

A good overview of these features can be found [here](https://hadoop.apache.org/docs/r2.7.2/hadoop-project-dist/hadoop-common/SecureMode.html).

*Note*: These security features are only available on DC/OS Enterprise 1.10 and above.

## Transport Encryption

With transport encryption enabled, DC/OS Apache HDFS will automatically deploy all nodes with the correct configuration to encrypt communication via TLS. The nodes will communicate securely between themselves using TLS. Optionally, plaintext communication can be left open to clients.

The service uses the [DC/OS CA](https://docs.mesosphere.com/latest/security/ent/tls-ssl/) to generate the SSL artifacts that it uses to secure the service. Any client that trusts the DC/OS CA will consider the service's certificates valid.

*Note*: Enabling transport encryption is _required_ to use [SSL authentication](#ssl-authentication) for [authentication](#authentication) (authn), but is optional for [Kerberos authn](#kerberos-authn).

{% include services/security-configure-transport-encryption.md
    techName="Apache HDFS" %}

<!--
TO BE CONFIRMED
*Note*: It is possible to update a running DC/OS Apache HDFS service to enable transport encryption after initial installation, but the service may be unavilable during the transition. Additionally, your HDFS clients will need to be reconfigured unless `service.security.transport_encryption.allow_plaintext` is set to true. -->

#### Verify Transport Encryption Enabled

???
<!-- After service deployment completes, check the list of [HDFS endpoints](api-reference.md#connection-information) for the endpoints `broker-tls`. -->

## Authentication

DC/OS Apache HDFS supports Kerberos authentication.

### Kerberos Authentication

Kerberos authentication relies on a central authority to verify that HDFS clients are who they say they are. DC/OS Apache HDFS integrates with your existing Kerberos infrastructure to verify the identity of clients.

#### Prerequisites
- The hostname and port of a KDC reachable from your DC/OS cluster
- Sufficient access to the KDC to create Kerberos principals
- Sufficient access to the KDC to retrieve a keytab for the generated principals
- [The DC/OS Enterprise CLI](https://docs.mesosphere.com/latest/cli/enterprise-cli/#installing-the-dcos-enterprise-cli)
- DC/OS Superuser permissions

#### Configure Kerberos Authentication

#### Create principals

The DC/OS Apache HDFS service requires Kerberos principals for each node to be deployed. The overall topology of the HDFS service is:
- 3 journal nodes
- 2 name nodes (with ZKFC)
- A configurable number of data nodes

As such the required Kerberos principals will have the form:
```
<service primary>/name-0-node.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>
<service primary>/name-0-zkfc.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>
<service primary>/name-1-node.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>
<service primary>/name-1-zkfc.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>

<service primary>/journal-0-node.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>
<service primary>/journal-1-node.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>
<service primary>/journal-2-node.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>

<service primary>/data-<data-index>-node.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>

```
with:
- `service primary = service.security.kerberos.primary`
- `data index = 0 up to data_node.brokers.count - 1`
- `service subdomain = service.name with all `/`'s removed`
- `service realm = service.security.kerberos.realm`

For example, if installing with these options:
```json
{
    "service": {
        "name": "a/good/example",
        "kerberos": {
            "primary": "example",
            "realm": "EXAMPLE"
        }
    },
    "data_node": {
        "count": 3
    }
}
```
then the principals to create would be:
```
example/name-0-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
example/name-0-zkfc.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
example/name-1-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
example/name-1-zkfc.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE

example/journal-0-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
example/journal-1-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
example/journal-2-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE

example/data-0-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
example/data-1-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
example/data-2-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
```

{% include services/security-configure-transport-encryption.md
    techName="Apache HDFS" %}

#### Install the Service

Install the DC/OS Apache HDFS service with the following options in addition to your own:
```json
{
    "service": {
        "kerberos": {
            "enabled": true,
            "kdc": {
                "hostname": "<kdc host>",
                "port": <kdc port>
            },
            "primary": "<service primary default hdfs>",
            "realm": "<realm>",
            "keytab_secret": "<path to keytab secret>",
            "debug": <true|false default false>
        }
    }
}
```

<!-- TO BE DETERMINED *Note*: It is possible to enable Kerberos after initial installation but the service may be unavailable during the transition. Additionally, your HDFS clients will need to be reconfigured. -->

## Authorization

The DC/OS Apache HDFS service supports HDFS's native authorization primitives. If Keberos is enabled as detailed [above](#kerberos-authentication), then

### Enable Authorization

#### Prerequisites
- Completion of  [Kerberos authentication](#kerberos-authentication) above.
