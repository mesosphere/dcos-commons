---
post_title: Install and Customize
menu_order: 10
feature_maturity: preview
enterprise: 'no'
---

# About installing Kafka on Enterprise DC/OS

 <a name="install-enterprise"></a>
 In Enterprise DC/OS `strict` [security mode](https://docs.mesosphere.com/1.9/administration/installing/custom/configuration-parameters/#security), Kafka requires a service account. In `permissive`, a service account is optional. Only someone with `superuser` permission can create the service account. Refer to [Provisioning Kafka](https://docs.mesosphere.com/1.9/administration/id-and-access-mgt/service-auth/kafka-auth/) for instructions.

# Default Installation

To start a basic test cluster with three brokers, run the following command on the DC/OS CLI. Enterprise DC/OS users must follow additional instructions. [More information about installing Kafka on Enterprise DC/OS](#install-enterprise).

    $ dcos package install kafka
    

This command creates a new Kafka cluster with the default name `kafka`. Two clusters cannot share the same name, so installing additional clusters beyond the default cluster requires [customizing the `name` at install time][4] for each additional instance.

All `dcos kafka` CLI commands have a `--name` argument allowing the user to specify which Kafka instance to query. If you do not specify a service name, the CLI assumes the default value, `kafka`. The default value for `--name` can be customized via the DC/OS CLI configuration:

    $ dcos kafka --name kafka-dev <cmd>
    
**Note:** You can also [install Kafka from the DC/OS GUI](https://docs.mesosphere.com/1.8/usage/managing-services/install/).

# Minimal Installation

For development purposes, you may wish to install Kafka on a local DC/OS cluster. For this, you can use [dcos-vagrant][5].

To start a minimal cluster with a single broker, create a JSON options file named `sample-kafka-minimal.json`:

    {
        "brokers": {
            "count": 1,
            "mem": 512,
            "disk": 1000
        }
    }
    

The command below creates a cluster using `sample-kafka-minimal.json`:

    $ dcos package install --options=sample-kafka-minimal.json kafka
    
<a name="custom-installation"></a>
# Custom Installation

Customize the defaults by creating a JSON file. Then, pass it to `dcos package install` using the `--options` parameter.

Sample JSON options file named `sample-kafka-custom.json`:

    {
        "service": {
            "name": "sample-kafka-custom",
            "placement_strategy": "NODE"
        },
        "brokers": {
            "count": 10
        },
        "kafka": {
            "delete_topic_enable": true,
            "log_retention_hours": 128
        }
    }
    

The command below creates a cluster using `sample-kafka.json`:

    $ dcos package install --options=sample-kafka-custom.json kafka
    

See [Configuration Options][6] for a list of fields that can be customized via an options JSON file when the Kafka cluster is created.

# Multiple Kafka cluster installation

Installing multiple Kafka clusters is identical to installing Kafka clusters with custom configurations as described above. The only requirement on the operator is that a unique `name` is specified for each installation. For example:

    $ cat kafka1.json
    {
        "service": {
            "name": "kafka1"
        }
    }
    
    $ dcos package install kafka --options=kafka1.json

 [4]: #custom-installation
 [5]: https://github.com/mesosphere/dcos-vagrant
 [6]: https://docs.mesosphere.com/1.9/usage/service-guides/kafka/configure#configuration-options
