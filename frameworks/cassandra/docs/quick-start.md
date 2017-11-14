---
post_title: Quick Start
menu_order: 40
post_excerpt: ""
enterprise: 'no'
---

1. Install DC/OS on your cluster. See [the documentation](https://docs.mesosphere.com/latest/administration/installing/) for instructions.
1. If you are using open source DC/OS, install DC/OS Apache Cassandra with the following command from the DC/OS CLI. If you are using Enterprise DC/OS, you may need to follow additional instructions. See the Install and Customize section for more information.
   
   ```
   dcos package install beta-cassandra
   ```
   
You can also install DC/OS Apache Cassandra from [the DC/OS web interface](https://docs.mesosphere.com/latest/usage/webinterface/).

1. The service will now deploy with a default configuration. You can monitor its deployment via the Services UI in the DC/OS Dashboard.

1. Connect a client to the DC/OS Apache Cassandra service.

   ```
   dcos beta-cassandra endpoints
   [
     "native-client"
   ]

   dcos beta-cassandra endpoints native-client
   {
     "address": [
       "10.0.1.125:9042",
       "10.0.2.152:9042",
       "10.0.1.22:9042"
     ],
     "dns": [
       "node-1-server.cassandra.autoip.dcos.thisdcos.directory:9042",
       "node-0-server.cassandra.autoip.dcos.thisdcos.directory:9042",
       "node-2-server.cassandra.autoip.dcos.thisdcos.directory:9042"
     ]
   }
   ```
   
1. Write some data to your cluster using the `node-0-server` entry provided above.

   **Note:** In production, you should specify multiple node addresses to avoid disruption if a subset of addressed nodes are down:

   ```bash
   $ dcos node ssh --master-proxy --leader
   $ docker run -it cassandra:3.0.14 cqlsh node-0-server.cassandra.autoip.dcos.thisdcos.directory
   node-0-server.cassandra.autoip.dcos.thisdcos.directory:9042
   > CREATE KEYSPACE space1 WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };
   > USE space1;
   > CREATE TABLE testtable1 (key varchar, value varchar, PRIMARY KEY(key));
   > INSERT INTO space1.testtable1(key, value) VALUES('testkey1', 'testvalue1');
   > SELECT * FROM testtable1;
   ```
