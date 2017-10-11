---
post_title: Quick Start
menu_order: 40
enterprise: 'no'
---

1. Perform a default installation by following the instructions in the Install and Customize section of this topic.
	**Note:** Your DC/OS cluster must have at least 3 private agent nodes.

1. Wait until the cluster is deployed and the nodes are all running. This may take 5-10 minutes. You can monitor the deployment via the CLI:

	```bash
	$ dcos beta-elastic --name="elastic" plan show deploy
	```

1. SSH into the master node.

    ```bash
    dcos node ssh --master-proxy --leader
    ```

1. Retrieve client endpoint information by running the `endpoints` command:

        $ dcos beta-elastic --name="elastic" endpoints coordinator-http
        {
            "vip": "coordinator.elastic.l4lb.thisdcos.directory:9200",
            "address": [
                "10.0.2.88:1026",
                "10.0.2.88:1027"
            ],
            "dns": [
                "coordinator-0-node.elastic.autoip.dcos.thisdcos.directory:1026",
                "coordinator-0-node.elastic.autoip.dcos.thisdcos.directory:1027"
            ],
        }

1. [SSH into a DC/OS node][1]:

        $ dcos node ssh --master-proxy --leader

    Now that you are inside your DC/OS cluster, you can connect to your Elasticsearch cluster directly.

1. Create an index:

        $ curl -s -u elastic:changeme -XPUT 'coordinator.elastic.l4lb.thisdcos.directory:9200/customer?pretty'


1. Store data in your index:

        $ curl -s -u elastic:changeme -XPUT 'coordinator.elastic.l4lb.thisdcos.directory:9200/customer/external/1?pretty' -d '
        {
            "name": "John Doe"
        }'

1. Retrieve data from your index:

        $ curl -s -u elastic:changeme -XGET 'coordinator.elastic.l4lb.thisdcos.directory:9200/customer/external/1?pretty'


[1]: https://docs.mesosphere.com/1.9/administering-clusters/sshcluster/
