---
post_title: Connecting Clients
menu_order: 50
enterprise: 'no'
---

# Connecting Clients

The Elasticsearch REST APIs are exposed using JSON over HTTP. You simply send HTTP requests to the appropriate Named VIP, which is essentially a load-balanced name-based service address. By default, the Elastic framework creates an Elasticsearch cluster with one [coordinator node](https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-node.html#coordinating-node). Send your requests to this service address as shown in the following examples:  

1. Create an index.

  ```bash
  curl -s -u elastic:changeme -XPUT 'coordinator.elastic.l4lb.thisdcos.directory:9200/customer?pretty'
  ```

1. Store and retrieve data.

  ```bash
  curl -s -u elastic:changeme -XPUT 'coordinator.elastic.l4lb.thisdcos.directory:9200/customer/external/1?pretty' -d '
  {
    "name": "John Doe"
  }'
  curl -s -u elastic:changeme -XGET 'coordinator.elastic.l4lb.thisdcos.directory:9200/customer/external/1?pretty'
  ```

**Note:** If you did not install any coordinator nodes, you should direct all queries to your data nodes instead:

  ```bash
  curl -s -u elastic:changeme 'data.elastic.l4lb.thisdcos.directory:9200/_cat/nodes?v'
  ```

The service address varies based on the name you assigned the framework when you installed it.
```
<node type>.<framework name>.l4lb.thisdcos.directory:9200
```

The default framework name is `elastic`. If you customized the name your framework, you need to adjust the service address accordingly.

## Kibana

[Kibana](https://www.elastic.co/products/kibana) lets you visualize your Elasticsearch data and navigate the Elastic Stack. You can install Kibana like any other DC/OS package via the **Universe** > **Packages** tab of the DC/OS web interface or the DC/OS CLI:

```bash
$ dcos package install kibana
```

This command launches a new Kibana application with the default name `kibana` and the default Elasticsearch URL `http://coordinator.elastic.l4lb.thisdcos.directory:9200`. Two Kibana application instances cannot share the same name, so installing additional Kibana applications beyond the default one requires customizing the `name` at install time for each additional instance.

### Access Kibana

1. Log into your DC/OS cluster so that you can see the Dashboard. You should see your Elastic service and your Kibana service running under Services.

1. Make sure Kibana is ready for use. Depending on your Kibana node’s resources and whether or not you are installing X-Pack, it can take ~10 minutes to launch. If you look in the stdout log for Kibana, you will see this line takes the longest when installing X-Pack:

  ```
  Optimizing and caching browser bundles...
  ```

  Then you’ll see this:

  ```
  {"type":"log","@timestamp":"2016-12-08T22:37:46Z","tags":["listening","info"],"pid":12263,"message":"Server running at http://0.0.0.0:5601"}
  ```

1. If you installed X-Pack, go to
  ```
  http://<dcos_url>/service/kibana/login
  ```
  and log in with `elastic`/`changeme`. [More information on installing X-Pack](https://docs.mesosphere.com/services/elastic/v2.0.0-5.5.1/elastic-x-pack/).
  
  Otherwise go to
  ```
  http://<dcos_url>/service/kibana
  ```

### Configuration Guidelines

- Service name: This needs to be unique for each instance of the service that is running.
- Service user: This must be a non-root user that already exists on each agent. The default user is `nobody`.
- The Kibana X-Pack plugin is not installed by default, but you can enable it. See the [X-Pack documentation](x-pack.md) to learn more about X-Pack in the Elastic package. This setting must match the corresponding setting in the Elastic package (i.e., if you have X-Pack enabled in Kibana, you must also have it enabled in Elastic). 
- Elasticsearch credentials: If you have X-Pack enabled, Kibana will use these credentials for authorization. The default user is  `kibana`.
- Elasticsearch URL: This is a required configuration parameter. The default value `http://coordinator.elastic.l4lb.thisdcos.directory:9200` corresponds to the named VIP that exists when the Elastic package is launched with its own default configuration.  

### Custom Installation

You can customize the Kibana installation in a variety of ways by specifying a JSON options file. For example, here is a sample JSON options file that installs X-Pack and customizes the service name and Elasticsearch URL:

```json
{
    "service": {
        "name": "another-kibana"
    },
    "kibana": {
          "elasticsearch_url": "http://my.elasticsearch.cluster:9200",
          "xpack_enabled": true
    }
}

```

The command below installs Kibana using a `options.json` file:

```bash
$ dcos package install kibana --options=options.json 
```

