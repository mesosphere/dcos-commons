---
layout: layout.pug
navigationTitle:
excerpt:
title: Install and Customize
menuWeight: 20

techName: Apache Kafka
packageName: beta-kafka
serviceName: kafka
zookeeperPackageName: beta-kafka-zookeeper
zookeeperServiceName: kafka-zookeeper
---

{% capture customInstallConfigurations %}
### Minimal installation

For development purposes, you may wish to install {{ page.techName }} on a local DC/OS cluster. For this, you can use [dcos-docker](https://github.com/dcos/dcos-docker) or [dcos-vagrant](https://github.com/dcos/dcos-vagrant).

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

### Example custom installation

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

Alternatively, you can perform a custom installation from the DC/OS web interface. Choose `ADVANCED INSTALLATION` at install time.

### Alternate ZooKeeper

{{ page.techName }} requires a running ZooKeeper ensemble to perform its own internal accounting. By default, the DC/OS {{ page.techName }} Service uses the ZooKeeper ensemble made available on the Mesos masters of a DC/OS cluster at `master.mesos:2181/dcos-service-<servicename>`. At install time, you can configure an alternate ZooKeeper for {{ page.techName }} to use. This enables you to increase {{ page.techName }}'s capacity and removes the DC/OS System ZooKeeper ensemble's involvement in running it.

To configure an alternate Zookeeper instance:

1. Create a file named `options.json` with the following contents.

**Note:** If you are using the [DC/OS Apache ZooKeeper service](https://docs.mesosphere.com/services/{{ page.zookeeperPackageName }}), use the DNS addresses provided by the `dcos {{ page.zookeeperPackageName }} endpoints clientport` command as the value of `kafka_zookeeper_uri`. Here is an example `options.json` which points to a `{{ page.zookeeperPackageName }}` instance named `{{ page.zookeeperServiceName }}`:

```json
{
  "kafka": {
    "kafka_zookeeper_uri": "zookeeper-0-server.{{ page.zookeeperServiceName }}.autoip.dcos.thisdcos.directory:1140,zookeeper-1-server.{{ page.zookeeperServiceName }}.autoip.dcos.thisdcos.directory:1140,zookeeper-2-server.{{ page.zookeeperServiceName }}.autoip.dcos.thisdcos.directory:1140"
  }
}
```

1. Install {{ page.techName }} with the options file you created.

```bash
$ dcos package install {{ page.packageName }} --options="options.json"
```

You can also update an already-running {{ page.techName }} instance from the DC/OS CLI, in case you need to migrate your ZooKeeper data elsewhere.

**Note:** Before performing this configuration change, you must first copy the data from your current ZooKeeper ensemble to the new ZooKeeper ensemble. The new location must have the same data as the previous location during the migration.

```bash
$ dcos {{ page.packageName }} --name={{ page.serviceName }} update start --options=options.json
```
{% endcapture %}

{% include services/install.md
    techName=page.techName
    packageName=page.packageName
    serviceName=page.serviceName
    minNodeCount="three"
    defaultInstallDescription="with three brokers"
    serviceAccountInstructionsUrl="https://docs.mesosphere.com/services/kafka/kafka-auth/"
    customInstallConfigurations=customInstallConfigurations %}
