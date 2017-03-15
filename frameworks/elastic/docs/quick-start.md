---
post_title: Quick Start
menu_order: 0
feature_maturity: experimental
enterprise: 'no'
---

1. Install an Elasticsearch cluster with Kibana and log on to the Mesos master node.

  ```bash
  dcos package install --app elastic
  dcos node ssh --master-proxy --leader
  ```

1. Wait until the cluster is deployed and the nodes are all running. This may take 5-10 minutes. If you try to access the cluster too soon, you may get an empty response or an authentication error like this:

  ```json
  {"error":{"root_cause":[{"type":"security_exception","reason":"failed to authenticate user [elastic]","header":{"WWW-Authenticate":"Basic realm=\"security\" charset=\"UTF-8\""}}],"type":"security_exception","reason":"failed to authenticate user [elastic]","header":{"WWW-Authenticate":"Basic realm=\"security\""}}}
  ```

1. You can check the status of the deployment via the CLI:

  ```bash
  dcos elastic plan show deploy
  ```

1. Explore your cluster.

  ```bash
  curl -s -u elastic:changeme 'coordinator.elastic.l4lb.thisdcos.directory:9200/_cat/health?v'
  curl -s -u elastic:changeme 'coordinator.elastic.l4lb.thisdcos.directory:9200/_cat/nodes?v'
  ```

1. Create and check indices.

  ```bash
  curl -s -u elastic:changeme -XPUT 'coordinator.elastic.l4lb.thisdcos.directory:9200/customer?pretty'
  curl -s -u elastic:changeme 'coordinator.elastic.l4lb.thisdcos.directory:9200/_cat/indices?v'
  ```

1. Store and retrieve data.

  ```bash
  curl -s -u elastic:changeme -XPUT 'coordinator.elastic.l4lb.thisdcos.directory:9200/customer/external/1?pretty' -d '
  {
    "name": "John Doe"
  }'
  curl -s -u elastic:changeme -XGET 'coordinator.elastic.l4lb.thisdcos.directory:9200/customer/external/1?pretty'
  ```

1. Check status.

  ```bash
  curl -s -u elastic:changeme 'coordinator.elastic.l4lb.thisdcos.directory:9200/_cat/health?v'
  curl -s -u elastic:changeme 'coordinator.elastic.l4lb.thisdcos.directory:9200/_cat/indices?v'
  curl -s -u elastic:changeme 'coordinator.elastic.l4lb.thisdcos.directory:9200/_cat/nodes?v'
  ```


**Note:** If you did not install any coordinator nodes, you should direct all queries to your data nodes instead:

```bash
curl -s -u elastic:changeme 'data.elastic.l4lb.thisdcos.directory:9200/_cat/nodes?v'
```

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
  http://$DCOS_URL/service/{{cluster-name}}/kibana/login
  ```
  And log in with `elastic`/`changeme`
