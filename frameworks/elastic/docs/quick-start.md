---
post_title: Quick Start
menu_order: 0
feature_maturity: experimental
enterprise: 'no'
---

1. Install a Elasticsearch cluster with Kibana using DC/OS CLI:

    **Note:** Your cluster must have at least 3 private nodes.

        $ dcos package install elastic
        
    Wait until the cluster is deployed and the nodes are all running. This may take 5-10 minutes. You can monitor the deploy via the CLI:

        $ dcos elastic plan show deploy

1. Once the cluster is installed, retrieve connection information by running the `endpoints` command:
        
        $ dcos elastic endpoints
        {
            "direct": ["data-1-server.elastic.mesos:1025", "data-0-server.elastic.mesos:1025"],
            "vip": "data.elastic.l4lb.thisdcos.directory:9200"
        }

1. [SSH into a DC/OS node][1]:

        $ dcos node ssh --master-proxy --leader

    Now that you are inside your DC/OS cluster, you can connect to your Elasticsearch cluster directly.

1. Create an indice:

        $ curl -s -u elastic:changeme -XPUT 'coordinator.elastic.l4lb.thisdcos.directory:9200/customer?pretty'


1. Store data.

        $ curl -s -u elastic:changeme -XPUT 'coordinator.elastic.l4lb.thisdcos.directory:9200/customer/external/1?pretty' -d '
        {
            "name": "John Doe"
        }'
        
1. Retrieve data:

        $ curl -s -u elastic:changeme -XGET 'coordinator.elastic.l4lb.thisdcos.directory:9200/customer/external/1?pretty'
        
1. Browse to Kibana: http://$DCOS_URL/service/elastic/kibana/login

  Log in with `elastic`/`changeme`

  
[1]: https://docs.mesosphere.com/1.9/administration/access-node/sshcluster/
