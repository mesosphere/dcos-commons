---
layout: layout.pug
navigationTitle:
excerpt:
title: Quick Start
menuWeight: 40
---
{% assign data = site.data.services.elastic %}

## Steps

1. Perform a default installation by following the instructions in the Install and Customize section of this topic.
	**Note:** Your DC/OS cluster must have at least 3 private agent nodes.

1. Wait until the cluster is deployed and the nodes are all running. This may take 5-10 minutes. You can monitor the deployment via the CLI:

    ```bash
    $ dcos {{ data.packageName }} --name={{ data.serviceName }} plan show deploy
    ```

1. Retrieve client endpoint information by running the `endpoints` command:

    ```bash
    $ dcos {{ data.packageName }} --name={{ data.serviceName }} endpoints coordinator-http
    {
        "vip": "coordinator.{{ data.serviceName }}.l4lb.thisdcos.directory:9200",
        "address": [
            "10.0.2.88:1026",
            "10.0.2.88:1027"
        ],
        "dns": [
            "coordinator-0-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory:1026",
            "coordinator-0-node.{{ data.serviceName }}.autoip.dcos.thisdcos.directory:1027"
        ],
    }
    ```

1. [SSH into a DC/OS node](https://docs.mesosphere.com/latest/administering-clusters/sshcluster/):

    ```bash
    $ dcos node ssh --master-proxy --leader
    ```

    Now that you are inside your DC/OS cluster, you can connect to your Elasticsearch cluster directly.

1. Create an index:

    ```bash
    $ curl -s -u elastic:changeme -XPUT 'coordinator.{{ data.serviceName }}.l4lb.thisdcos.directory:9200/customer?pretty'
    ```

1. Store data in your index:

    ```bash
    $ curl -s -u elastic:changeme -XPUT \
        'coordinator.{{ data.serviceName }}.l4lb.thisdcos.directory:9200/customer/external/1?pretty' \
        -d '{ "name": "John Doe" }'
    ```

1. Retrieve data from your index:

    ```bash
    $ curl -s -u elastic:changeme -XGET \
        'coordinator.{{ data.serviceName }}.l4lb.thisdcos.directory:9200/customer/external/1?pretty'
    ```
