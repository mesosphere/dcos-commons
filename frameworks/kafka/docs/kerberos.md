---
layout: layout.pug
navigationTitle:
excerpt:
title: Kerberos
menuWeight: 22

packageName: beta-kafka
serviceName: kafka
---

# Setting up Apache Kafka with Kerberos

## Create principals

In order to run Apache Kafka with Kerberos security enabled, a principal needs to be added for every broker in the cluster. For example, a three node cluster with the default service primary (`service.security.kerberos.primary`) of `{{ page.serviceName }}` will require to following principals:
```
kafka/kafka-0-broker.{{ page.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
kafka/kafka-1-broker.{{ page.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
kafka/kafka-2-broker.{{ page.serviceName }}.autoip.dcos.thisdcos.directory@LOCAL
```
(assuming a service name of `{{ page.serviceName }}` and a `LOCAL` realm)

## Create the keytab secret

Once the principals have been created, a keytab file must be generated and uploaded to the DC/OS secret store as a base-64-encoded value. Assuming the keytab for **all** the Kafka principals has been created as a file `keytab`, this can be added to the secret store as follows (note that the DC/OS Enterprise CLI needs to be installed to gain access to the `security` command):
```bash
$ base64 -w0 keytab > keytab.base64
$ dcos security secrets create  __dcos_base64__keytab --value-file keytab.base64
```

The name of the secret created (`__dcos_base64__keytab`) can be changed, as long as the `__dcos__base64__` prefix is maintained.

## Deploy kerberized Kafka

Create the following `kerberos-options.json` file:
```json
{
    "service": {
        "name": "{{ page.serviceName }}",
        "security": {
            "kerberos": {
                "enabled": true,
                "kdc": {
                    "hostname": "kdc.marathon.autoip.dcos.thisdcos.directory",
                    "port": 2500
                },
                "realm": "LOCAL",
                "keytab_secret": "__dcos_base64__keytab"
            }
        }
    }
}
```
Note the specification of the secret name as created in the previous step.

The kerberized Apache Kafka service is then deployed by running:
```bash
$ dcos package install {{ page.packageName }} --options=kerberos-options.json
```

## Deploy with kerberized ZooKeeper

If a kerberized `kafka-zookeeper` ensemble is available for use with this Apache Kafka cluster, the authentication between Kafka and ZooKeeper can also be secured using Kerberos.

In order to determine the endpoints for the ZooKeeper ensemble, the following command can be used:
```bash
$ dcos kafka-zookeeper --name=kafka-zookeeper endpoint clientport
```
resulting in output resembling:
```json
{
  "address": [
    "10.0.0.49:1140",
    "10.0.2.253:1140",
    "10.0.1.27:1140"
  ],
  "dns": [
    "zookeeper-0-server.kafka-zookeeper.autoip.dcos.thisdcos.directory:1140",
    "zookeeper-1-server.kafka-zookeeper.autoip.dcos.thisdcos.directory:1140",
    "zookeeper-2-server.kafka-zookeeper.autoip.dcos.thisdcos.directory:1140"
  ]
}
```

Create a `kerberos-zookeeper-options.json` file with the following contents:
```json
{
    "service": {
        "name": "{{ page.serviceName }}",
        "security": {
            "kerberos": {
                "enabled": true,
                "enabled_for_zookeeper": true,
                "kdc": {
                    "hostname": "kdc.marathon.autoip.dcos.thisdcos.directory",
                    "port": 2500
                },
                "realm": "LOCAL",
                "keytab_secret": "__dcos_base64__keytab"
            }
        }
    },
    "kafka": {
        "kafka_zookeeper_uri": "zookeeper-0-server.kafka-zookeeper.autoip.dcos.thisdcos.directory:1140,zookeeper-1-server.kafka-zookeeper.autoip.dcos.thisdcos.directory:1140,zookeeper-2-server.kafka-zookeeper.autoip.dcos.thisdcos.directory:1140"
    }
}
```
Note that `service.security.kerberos.enabled_for_zookeeper` is now set to true and that `kafka.kafka_zookeeper_uri` is set to the `"dns"` output of the `dcos kafka-zookeeper endpoint clientport` command.

Kerberized Kafka can then be deployed as follows:
```bash
$ dcos package install {{ page.packageName }} --options=kerberos-zookeeper-options.json
```

## Active Directory

Kerberized Apache Kafka also supports Active Directory as a KDC. Here the generation of principals and the relevant keytab should be adapted for the tools made available by the Active Directory installation.

As an example, the `ktpass` utility can be used to generate the keytab for the Apache Kafka brokers as follows:
```bash
$ ktpass.exe                      /princ kafka/kafka-0-broker.{{ page.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser kafka-0-broker@example.com /ptype KRB5_NT_PRINCIPAL +rndPass /out brokers-0.keytab
$ ktpass.exe /in brokers-0.keytab /princ kafka/kafka-1-broker.{{ page.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser kafka-1-broker@example.com /ptype KRB5_NT_PRINCIPAL +rndPass /out brokers-1.keytab
$ ktpass.exe /in brokers-1.keytab /princ kafka/kafka-2-broker.{{ page.serviceName }}.autoip.dcos.thisdcos.directory@EXAMPLE.COM /mapuser kafka-2-broker@example.com /ptype KRB5_NT_PRINCIPAL +rndPass /out kafka-brokers.keytab
```
Here it is assumed that the domain `example.com` exists and that the domain users `kafka-0-broker`, `kafka-1-broker`, and `kafka-2-broker` have been created (using the `net user` command, for example).

The generated file `kafka-brokers.keytab` can now be base64-encoded and added to the DC/OS secret store as above:
```bash
$ base64 -w0 kafka-brokers.keytab > keytab.base64
$ dcos security secrets create  __dcos_base64__ad_keytab --value-file keytab.base64
```

Kerberized Apache Kafka can then be deployed using the following configuration options:
```json
{
    "service": {
        "name": "{{ page.serviceName }}",
        "security": {
            "kerberos": {
                "enabled": true,
                "kdc": {
                    "hostname": "active-directory-dns.example.com",
                    "port": 88
                },
                "realm": "EXAMPLE.COM",
                "keytab_secret": "__dcos_base64__ad_keytab"
            }
        }
    }
}
```
This assumes that the Active Directory server is reachable from the DC/OS cluster at `active-directory-dns.example.com` and is accepting connections on port `88`. Note also the change in Kerberos realm and the DC/OS secret path used for the keytab.
