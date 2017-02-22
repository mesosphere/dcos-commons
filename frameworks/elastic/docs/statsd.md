---
post_title: Statsd Reporting
menu_order: 40
feature_maturity: experimental
enterprise: 'no'
---

For EE clusters, the Elastic framework automatically installs the [statsd plugin](https://github.com/Automattic/elasticsearch-statsd-plugin) on all Elasticsearch nodes to report metrics to the DC/OS Metrics Collector. You access Elasticsearch’s metrics as well as the default DC/OS metrics by querying each agent node individually:

1. Use the dcos CLI to get the auth token: `dcos config show core.dcos_acs_token`.
1. Ssh into an agent node.
1. Index a few documents, send some queries.
1. Within a minute, you should be able to query the endpoint and get results:

`curl -s -H "Authorization: token=your_auth_token" http://localhost:61001/system/v1/metrics/v0/containers  | jq`

Pick a container ID.

`curl -s -H "Authorization: token=your_auth_token" http://localhost:61001/system/v1/metrics/v0/containers/{container-id}/app  | jq`

The response will contain elasticsearch-specific metrics like this:
```
    {
      "name": "elasticsearch.elastic.node.data-0-server.thread_pool.warmer.largest",
      "value": 2,
      "unit": "",
      "timestamp": "2017-01-31T23:24:44Z"
    },
    {
      "name": "elasticsearch.elastic.node.data-0-server.thread_pool.warmer.completed",
      "value": 2221,
      "unit": "",
      "timestamp": "2017-01-31T23:24:44Z"
    },
```

Metric names are formed based on the [formats described here](https://github.com/Automattic/elasticsearch-statsd-plugin#stats-key-formats). Scroll up to [configuration](https://github.com/Automattic/elasticsearch-statsd-plugin#configuration) to see how PREFIX and NODE_NAME get determined. In the case of a master node failover, the counts start from 0 again.
