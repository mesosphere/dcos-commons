---
layout: layout.pug
navigationTitle:
excerpt:
title: Security
menuWeight: 22
---

# DC/OS Apache HDFS Security

The DC/OS Apache HDFS service supports HDFS's native transport encryption, authentication, and authorization mechanisms. The service provides automation and orchestration to simplify the usage of these important features.

A good overview of these features can be found [here](https://hadoop.apache.org/docs/r2.6.0/hadoop-project-dist/hadoop-common/SecureMode.html).

*Note*: These security features are only available on DC/OS Enterprise 1.10 and above.

## Transport Encryption

{% include services/security-transport-encryption-lead-in.md
    techName="Apache HDFS" plaintext="true" %}

*Note*: Enabling transport encryption is not _required_ to use [Kerberos authentication](#kerberos-authentication), but transport encryption _can_ be combined with Kerberos authentication.

{% include services/security-configure-transport-encryption.md
    techName="Apache HDFS" plaintext="true" %}

{% include services/security-transport-encryption-clients.md %}

<!--
TO BE CONFIRMED
*Note*: It is possible to update a running DC/OS Apache HDFS service to enable transport encryption after initial installation, but the service may be unavilable during the transition. Additionally, your HDFS clients will need to be reconfigured unless `service.security.transport_encryption.allow_plaintext` is set to true. -->

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

*Note:* Apache HDFS requires a principal for both the `service primary` and `HTTP`. The latter is used by the HTTP api.

The required Kerberos principals will have the form:
```
<service primary>/name-0-node.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>
HTTP/name-0-node.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>
<service primary>/name-0-zkfc.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>
HTTP/name-0-zkfc.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>
<service primary>/name-1-node.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>
HTTP/name-1-node.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>
<service primary>/name-1-zkfc.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>
HTTP/name-1-zkfc.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>

<service primary>/journal-0-node.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>
HTTP/journal-0-node.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>
<service primary>/journal-1-node.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>
HTTP/journal-1-node.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>
<service primary>/journal-2-node.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>
HTTP/journal-2-node.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>

<service primary>/data-<data-index>-node.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>
HTTP/data-<data-index>-node.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>

```
with:
- `service primary = service.security.kerberos.primary`
- `data index = 0 up to data_node.count - 1`
- `service subdomain = service.name with all `/`'s removed`
- `service realm = service.security.kerberos.realm`

For example, if installing with these options:
```json
{
    "service": {
        "name": "a/good/example",
        "security": {
            "kerberos": {
                "primary": "example",
                "realm": "EXAMPLE"
            }
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
HTTP/name-0-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
example/name-0-zkfc.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
HTTP/name-0-zkfc.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
example/name-1-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
HTTP/name-1-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
example/name-1-zkfc.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
HTTP/name-1-zkfc.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE

example/journal-0-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
HTTP/journal-0-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
example/journal-1-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
HTTP/journal-1-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
example/journal-2-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
HTTP/journal-2-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE

example/data-0-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
HTTP/data-0-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
example/data-1-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
HTTP/data-1-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
example/data-2-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
HTTP/data-2-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
```

{% include services/security-kerberos-ad.md
    principal="example/name-0-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE"
    spn="example/name-0-node.agoodexample.autoip.dcos.thisdcos.directory"
    upn="example/name-0-node.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE" %}

{% include services/security-service-keytab.md
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

The DC/OS Apache HDFS service supports HDFS's native authorization, which behaves similarly to UNIX file permissions. If Keberos is enabled as detailed [above](#kerberos-authentication), then Kerberos principals are mapped to HDFS users against which permissions can be assigned.

### Enable Authorization

#### Prerequisites
- Completion of  [Kerberos authentication](#kerberos-authentication) above.

#### Set Kerberos Principal to User Mapping

A custom mapping can be set to map Kerberos principals to OS user names for the purposes of determining group membership. This is supplied by setting the parameter
```
{
    "hdfs": {
        "security_auth_to_local": "<custom mapping>"
    }
}
```
where `<custom mapping>` is a base64 encoded string. The mapping is base64 encoded to ensure it is sent to the service correctly.

*Note*: There is _no_ default mapping. This is a reasonably secure default, but a mapping must be set if you plan to use groups in assigning permissions.

[This](https://hortonworks.com/blog/fine-tune-your-apache-hadoop-security-settings/) article has a good description of how to build a custom mapping, under the section "Kerberos Principals and UNIX User Names".

*NOTE*: In DC/OS 1.11 and above, the DC/OS UI will automatically encode and decode the mapping to and from base64. If installing from the CLI or from the UI in a version older than DC/OS 1.11, it is necessary to do the encoding manually.
