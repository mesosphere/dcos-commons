---
post_title: Install and Customize
menu_order: 0
feature_maturity: preview
enterprise: 'no'
---

# Default Installation

To start a basic cluster with three master nodes, two data nodes, one ingest node, one coordinator node, and one Kibana instance, run the following command on the DC/OS CLI:

```bash
$ dcos package install elastic
```

This command creates a new Elasticsearch cluster with the default name `elastic`. Two clusters cannot share the same name, so installing additional clusters beyond the default cluster requires customizing the `name` at install time for each additional instance.

**Note:** You can also install Elastic from the **Universe** > **Packages** tab of the DC/OS web interface. If you install Elastic from the web interface, you must install the Elastic DC/OS CLI subcommands separately. From the DC/OS CLI, enter:

```bash
dcos package install elastic --cli
```

# Custom Installation

You can customize the Elastic cluster in a variety of ways by specifying a JSON options file. For example, here is a sample JSON options file that customizes the service name, master transport port, and plugins:

```json
{
    "service": {
        "name": "another-cluster"
    },
    "master_nodes": {
          "transport_port": 19300
    },
    "elasticsearch": {
        "plugins": "analysis-icu,analysis-kuromoji"
    }
}

```

The command below creates a cluster using a `options.json` file:

```bash
$ dcos package install elastic --options=options.json 
```

# Multiple Elastic Cluster Installation

Installing multiple Elastic clusters is identical to installing Elastic clusters with custom configurations as described above. The only requirement on the operator is that a unique `name` is specified for each installation.

Sample JSON options file named `another-cluster.json`:
```json
{
    "service": {
        "name": "another-cluster"
    }
}
```

The command below creates a cluster using `another-cluster.json`:

```bash
$ dcos package install elastic --options=another-cluster.json
```

See the Configuring section for a list of fields that can be customized via an options JSON file when the Elastic cluster is created.
