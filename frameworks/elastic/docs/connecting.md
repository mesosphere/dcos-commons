---
post_title: Connecting Clients
menu_order: 32
feature_maturity: experimental
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