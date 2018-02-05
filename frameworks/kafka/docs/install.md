---
layout: layout.pug
navigationTitle:
excerpt:
title: Install and Customize
menuWeight: 20

packageName: beta-kafka
serviceName: kafka
---

{% include services/install.md
    techName="Apache Kafka"
    packageName=page.packageName
    serviceName=page.serviceName
    minNodeCount="three"
    defaultInstallDescription="with three brokers"
    serviceAccountInstructionsUrl="https://docs.mesosphere.com/services/kafka/kafka-auth/"
    enterpriseInstallUrl="" %}

## Alternate install configurations

### Minimal Installation

For development purposes, you may wish to install Kafka on a local DC/OS cluster. For this, you can use [dcos-vagrant](https://github.com/mesosphere/dcos-vagrant).

To start a minimal cluster with a single broker, create a JSON options file named `sample-kafka-minimal.json`:

```json
{
  "brokers": {
    "count": 1,
    "mem": 512,
    "disk": 1000
  }
}
```

The command below creates a cluster using `sample-kafka-minimal.json`:

```bash
$ dcos package install {{ page.packageName }} --options=sample-kafka-minimal.json
```

### Custom Installation

Customize the defaults by creating a JSON file. Then, pass it to `dcos package install` using the `--options` parameter.

Sample JSON options file named `sample-kafka-custom.json`:

```json
{
  "service": {
    "name": "sample-kafka-custom",
    "placement_strategy": "NODE"
  },
  "brokers": {
    "count": 10,
    "kill_grace_period": 30
  },
  "kafka": {
    "delete_topic_enable": true,
    "log_retention_hours": 128
  }
}
```

The command below creates a cluster using `sample-kafka.json`:

```bash
$ dcos package install {{ page.packageName }} --options=sample-kafka-custom.json
```

**Recommendation:** Store your custom configuration in source control.

See [Configuration Options](https://docs.mesosphere.com/services/kafka/configure/#configuration-options) for a list of fields that can be customized via an options JSON file when the Kafka cluster is created.

Alternatively, you can perform a custom installation from the DC/OS web interface. Choose `ADVANCED INSTALLATION` at install time.

### Multiple Kafka cluster installation

Installing multiple Kafka clusters is identical to installing Kafka clusters with custom configurations as described above. The only requirement on the operator is that a unique `name` be specified for each installation. For example:

```bash
$ cat kafka1.json
{
  "service": {
  "name": "{{ page.serviceName }}1"
  }
}

$ dcos package install {{ page.packageName }} --options=kafka1.json
```

To query this service using the CLI, you may provide the `--name` parameter:

```bash
$ dcos {{ page.packageName }} --name={{ page.serviceName }}1 ...
```

### Alternate ZooKeeper

By default, the Kafka services uses the ZooKeeper ensemble made available on the Mesos masters of a DC/OS cluster. You can configure an alternate ZooKeeper at install time. This enables you to increase Kafka's capacity and removes the system ZooKeeper's involvment in the service.

To configure it:

1. Create a file named `options.json` with the following contents.

**Note:** If you are using the [DC/OS Apache ZooKeeper service](https://docs.mesosphere.com/services/kafka-zookeeper), use the DNS addresses provided by the `dcos kafka-zookeeper endpoints clientport` command as the value of `kafka_zookeeper_uri`.

```json
{
    "kafka": {
      "kafka_zookeeper_uri": "zookeeper-0-server.kafka-zookeeper.autoip.dcos.thisdcos.directory:1140,zookeeper-1-server.kafka-zookeeper.autoip.dcos.thisdcos.directory:1140,zookeeper-2-server.kafka-zookeeper.autoip.dcos.thisdcos.directory:1140"
    }
}
```

1. Install Kafka with the options file.

```bash
$ dcos package install {{ page.packageName }} --options="options.json"
```

You can also update an already-running Kafka instance from the DC/OS CLI, in case you need to migrate your ZooKeeper data elsewhere.

**Note:** The ZooKeeper ensemble you point to must have the same data as the previous ZooKeeper ensemble.

```bash
$ dcos {{ page.packageName }} --name={{ page.serviceName }} update start --options=options.json
```
