---
layout: layout.pug
navigationTitle:
excerpt:
title: Security
menuWeight: 22
---

# DC/OS Apache Kafka Security

The DC/OS Apache Kafka service supports Kafka's native transport encryption, authentication, and authorization mechanisms. The service provides automation and orchestration to simplify the usage of these important features.

A good overview of these features can be found [here](https://www.confluent.io/blog/apache-kafka-security-authorization-authentication-encryption/), and Kafka's security documentation can be found [here](http://kafka.apache.org/documentation/#security).

*Note*: These security features are only available on DC/OS Enterprise 1.10 and above.

## Transport Encryption

With transport encryption enabled, DC/OS Apache Kafka will automatically deploy all brokers with the correct configuration to communicate via SSL. The Brokers will communicate securely between themselves using SSL. Optionally, plaintext communication can be left open to clients.

The service uses the [DC/OS CA](https://docs.mesosphere.com/latest/security/ent/tls-ssl/) to generate the SSL artifacts that it uses to secure the service. Any client that trusts the DC/OS CA will consider the service's certificates valid.

*Note*: Enabling transport encryption is _required_ to use [SSL authentication](#ssl-authentication) for [authentication](#authentication) (authn), but is optional for [Kerberos authn](#kerberos-authn).

{% include services/configure-transport-encryption.md
    techName="Apache Kafka" %}

*Note*: It is possible to update a running DC/OS Apache Kafka service to enable transport encryption after initial installation, but the service may be unavilable during the transition. Additionally, your Kafka clients will need to be reconfigured unless `service.security.transport_encryption.allow_plaintext` is set to true.

#### Verify Transport Encryption Enabled

After service deployment completes, check the list of [Kafka endpoints](api-reference.md#connection-information) for the endpoint `broker-tls`. If `service.security.transport_encryption.allow_plaintext` is `true`, then the `broker` endpoint will also be available.

## Authentication

DC/OS Apache Kafka supports two authentication (authn) mechanisms, SSL and Kerberos. The two are supported indpendently but may not be combined. If both are SSL and Kerberos are enabled, the service will use Kerberos authentication.

*Note*: Kerberos can, however, be combined with transport encryption.

### Kerberos Authentication

Kerberos authentication relies on a central authority to verify that Kafka clients (be it broker, consumer, or producer) are who they say they are. DC/OS Apache Kafka integrates with your existing Kerberos infrastructure to verify the identity of clients.

#### Prerequisites
- The hostname and port of a KDC reachable from your DC/OS cluster
- Sufficient access to the KDC to create Kerberos principals
- Sufficient access to the KDC to retrieve a keytab for the generated principals
- [The DC/OS Enterprise CLI](https://docs.mesosphere.com/1.10/cli/enterprise-cli/#installing-the-dcos-enterprise-cli)
- DC/OS Superuser permissions

#### Configure Kerberos Authentication

#### Create principals

The DC/OS Apache Kafka service requires a Kerberos principal for each broker to be deployed. Each principal must be of the form
```
<service primary>/kafka-<broker index>-broker.<service subdomain>.autoip.dcos.thisdcos.directory@<service realm>
```
with:
- `service primary = service.security.kerberos.primary`
- `broker index = 0 up to brokers.count - 1`
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
    "brokers": {
        "count": 3
    }
}
```
then the principals to create would be:
```
example/kafka-0-broker.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
example/kafka-1-broker.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
example/kafka-2-broker.agoodexample.autoip.dcos.thisdcos.directory@EXAMPLE
```

#### Place Service Keytab in DC/OS Secret Store

The DC/OS Apache Kafka service uses a keytab containing all broker principals (service keytab) to simplify orchestration. After creating the principals above, generate the service keytab making sure to include all the broker principals. This will be stored as a secret in the DC/OS Secret Store.

*Note*: DC/OS 1.10 does not support adding binary secrets directly to the secret store, only text files are supported. Instead, first base64 encode the file, and save it to the secret store as `/desired/path/__dcos_base64__secret_name`. The DC/OS security modules will handle decoding the file when it is used by the service. More details [here](https://docs.mesosphere.com/services/ops-guide/overview/#binary-secrets).

The service keytab should be stored at `service/path/service.keytab` (as noted above for 1.10, it would be `__dcos_base64__service.keytab`), where `service/path` matches the path of the service. For example, if installing with the options
```json
{
    "service": {
        "name": "a/good/example"
    }
}
```
then the service keytab should be stored at `a/good/service.keytab`.

Documentation for adding a file to the secret store can be found [here](https://docs.mesosphere.com/latest/security/ent/secrets/create-secrets/#creating-secrets-from-a-file-via-the-dcos-enterprise-cli).

*Note*: Secrets access is controlled by [DC/OS Spaces](https://docs.mesosphere.com/latest/security/ent/#spaces-for-secrets), which function like namespaces. Technically, any secret path in the same space as that of the service will be accessible by the service. Matching the two paths is, however, the most secure option. Additionally the secret name `service.keytab` is a convention and not a requirement.


#### Install the Service

Install the DC/OS Apache Kafka service with the following options in addition to your own:
```json
{
    "service": {
        "kerberos": {
            "enabled": true,
            "enabled_for_zookeeper": <true|false default false>,
            "kdc": {
                "hostname": "<kdc host>",
                "port": <kdc port>
            },
            "primary": "<service primary default kafka>",
            "realm": "<realm>",
            "keytab_secret": "<path to keytab secret>",
            "debug": <true|false default false>
        }
    }
}
```

*Note*: If `service.kerberos.enabled_for_zookeeper` is set to true, then the additional setting `kafka.kafka_zookeeper_uri` must be configured to point at a kerberized Apache ZooKeeper as follows:
```json
{
    "kafka": {
        "kafka_zookeeper_uri": <list of zookeeper hosts>
    }
}
```
The DC/OS Apache ZooKeeper service is intended for this purpose and supports Kerberos.

*Note*: It is possible to enable Kerberos after initial installation but the service may be unavailable during the transition. Additionally, your Kafka clients will need to be reconfigured.


### SSL Authentication

SSL authentication requires that all clients be they brokers, producers, or consumers present a valid certificate from which their identity can be derived. DC/OS Apache Kafka uses the `CN` of the SSL certificate as the principal for a given client. For example, the certificate `CN=bob@example.com,OU=,O=Example,L=London,ST=London,C=GB` will be considered as the principal `bob@example.com`.

#### Prerequisites
- Completion of the section [Transport Encryption](#transport-encryption) above

#### Install the Service

Install the DC/OS Apache Kafka service with the following options in addition to your own:
```json
{
    "service": {
        "service_account": "<service-account>",
        "service_account_secret": "<secret path>",
        "security": {
            "transport_encryption": {
                "enabled": true
            },
            "ssl_authentication": {
                "enabled": true
            }
        }
    }
}
```

*Note*: It is possible to enable SSL authentication after initial installation, but the service may be unavailable during the transition. Additionally, your Kafka clients will need to be reconfigured.

#### Authenticating a Client

To authenticate a client against DC/OS Apache Kafka, you will need to configure it to use a certificate signed by the DC/OS CA. After generating a certificate signing request, you can issue it to the DC/OS CA by calling the API `<dcos-cluster>/ca/api/v2/sign`. Using the `curl` command the request would look like:
```bash
$ curl -X POST \
    -H "Authorization: token=$(dcos config show core.dcos_acs_token)" \
    <dcos-cluster>/ca/api/v2/sign \
    -d '{"certificate_request": "<json-encoded-value-of-request.csr>"}'
```

The response will contain a signed public certificate. Full details on the DC/OS CA API can be found [here](https://docs.mesosphere.com/latest/security/ent/tls-ssl/ca-api/).

## Authorization

The DC/OS Apache Kafka Service supports Kafka's ACL-based authorization (authz) system. To use Kafka's authz, either SSL Auth or Kerberos must be enabled as detailed above.

### Enable Authorization

#### Prerequisites
- Completion of either [SSL Authentication](#ssl-authentication) or [Kerberos](#kerberos-authn) above.

#### Install the Service

Install the DC/OS Apache Kafka service with the following options in addition to your own (remember, either SSL authentication or Kerberos _must_ be enabled):
```json
{
    "service": {
        "security": {
            "authorization": {
                "enabled": true,
                "super_users": "<list of super users>",
                "allow_everyone_if_no_acl_found": <true|false default false>
            }
        }
    }
}
```

`service.security.authorization.super_users` should be set to a semi-colon delimited list of principals to treat as super users (all permissions). The format of the list is `User:<user1>;User:<user2>;...`. Using Kerberos authentication, the "user" value is the Kerberos primary, and for SSL authentication the "user" value is the `CN` of the certificate. The Kafka brokers themselves are automatically designated as super users.
