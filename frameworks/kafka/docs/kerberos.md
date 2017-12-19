---
post_title: Kerberos
menu_order: 22
enterprise: 'yes'
---

# Setting up Apache Kafka with Kerberos

## Create principals

In order to run Apache Kafka with Kerberos security enabled, a principal needs to be added for every broker in the cluster. For example, a three node cluster with the default service primary (`service.security.kerberos.primary`) of `kafka` will require to following principals:
```
kafka/kafka-0-broker.kafka.autoip.dcos.thisdcos.directory@LOCAL
kafka/kafka-1-broker.kafka.autoip.dcos.thisdcos.directory@LOCAL
kafka/kafka-2-broker.kafka.autoip.dcos.thisdcos.directory@LOCAL
```
(assuming a service name of `kafka`)

## Create the keytab secret

Once the principals have been created, a keytab file must be generated and uploaded to the DC/OS secret store as a base-64-encoded value. Assuming the keytab for **all** the Kafka principals has been created as a file `keytab`, this can be added to the secret store as follows (note that the DC/OS Enterprise CLI needs to be installed to gain access to the `security` command):
```bash
$ base64 -w keytab > keytab.base64
$ dcos security secrets create  __dcos_base64__keytab --value-file keytab.base64
```

The name of the secret created (`__dcos_base64__keytab`) can be changed, as long as the `__dcos__base64__` prefix is maintained.

## Deploy kerberized Kafka

Create the following `kerberos-options.json` file:
```json
{
    "service": {
        "name": "kafka",
        "security": {
            "kerberos": {
                "enabled": true,
                "kdc": {
                    "hostname": "kdc.marathon.autoip.dcos.thisdcos.directory",
                    "port": 2500
                },
                "keytab_secret": "__dcos_base64__keytab"
            }
        }
    }
}
```
Note the specification of the secret name as created in the previous step.

The kerberized Apache Kafka service is then deployed by running:
```bash
$ dcos package install kafka --options=kerberos-options.json
```

## Deploy with kerberized ZooKeeper

If a kerberized `kafka-zookeeper` ensemble is available for use with this Apache Kafka cluster, the authentication between Kafka and ZooKeeper can also be secured using Kerberos.

In order to determine the endpoints for the ZooKeeper ensemble, the following command can be used:
```bash
$ dcos kafka-zookeeper endpoint clientport
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
        "name": "kafka",
        "security": {
            "kerberos": {
                "enabled": true,
                "enabled_for_zookeeper": true,
                "kdc": {
                    "hostname": "kdc.marathon.autoip.dcos.thisdcos.directory",
                    "port": 2500
                },
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
$ dcos package install kafka --options=kerberos-zookeeper-options.json
```
