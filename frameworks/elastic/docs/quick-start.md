---
post_title: Kick the Tires
menu_order: 0
feature_maturity: preview
enterprise: 'no'
---

1. Perform a default installation by following the instructions in the Install and Customize section of this topic.
	**Note:** Your DC/OS cluster must have at least 3 private agent nodes.

1. Wait until the cluster is deployed and the nodes are all running. This may take 5-10 minutes. You can monitor the deployment via the CLI:
	
	```bash
	$ dcos elastic plan show deploy
	```

1. SSH into the master node.

    ```bash
    dcos node ssh --master-proxy --leader
    ```
        
1. Retrieve client endpoint information by running the `endpoints` command:
        
        $ dcos elastic endpoints coordinator
        {
            "direct": ["coordinator-1-server.elastic.mesos:1025", "coordinato-0-server.elastic.mesos:1025"],
            "vip": "coordinator.elastic.l4lb.thisdcos.directory:9200"
        }

1. [SSH into a DC/OS node][1]:

        $ dcos node ssh --master-proxy --leader

    Now that you are inside your DC/OS cluster, you can connect to your Elasticsearch cluster directly.

1. Create an indice:

        $ curl -s -u elastic:changeme -XPUT 'coordinator.elastic.l4lb.thisdcos.directory:9200/customer?pretty'


1. Store data in your indice:

        $ curl -s -u elastic:changeme -XPUT 'coordinator.elastic.l4lb.thisdcos.directory:9200/customer/external/1?pretty' -d '
        {
            "name": "John Doe"
        }'
        
1. Retrieve data from your indice:

        $ curl -s -u elastic:changeme -XGET 'coordinator.elastic.l4lb.thisdcos.directory:9200/customer/external/1?pretty'

# Access Kibana

1. Log into your DC/OS cluster so that you can see the Dashboard. You should see your Elastic service running under Services.

1. Make sure Kibana is ready for use. The `kibana-deploy` phase should be marked as `COMPLETE` when you check the plan status:

  ```bash
  dcos elastic plan show deploy
  ```

  Depending on your Kibana node’s resources, it can take ~10 minutes to launch. If you look in the stdout log for the Kibana task, you will see this line takes the longest:

  ```
  Optimizing and caching browser bundles...
  ```

  Then you’ll see this:

  ```
  {"type":"log","@timestamp":"2016-12-08T22:37:46Z","tags":["listening","info"],"pid":12263,"message":"Server running at http://0.0.0.0:5601"}
  ```

1. Then, go to this URL:
  ```
  http://<dcos_url>/service/elastic/kibana/login
  ```
  And log in with `elastic`/`changeme`

[1]: https://docs.mesosphere.com/1.9/administering-clusters/sshcluster/
