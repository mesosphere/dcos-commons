---
post_title: Connecting Clients
menu_order: 40
feature_maturity: experimental
enterprise: 'no'
---

# Connecting Clients

Applications interface with HDFS like they would any POSIX file system. However, applications that will act as client nodes of the HDFS deployment require an `hdfs-site.xml` and `core-site.xml` file that provides the configuration information necessary to communicate with the cluster.

## Connection Info Using the DC/OS CLI

Executed the following command from the DC/OS CLI to retrieve the `hdfs-site.xml` file that client applications can use to connect to the cluster.

```
$ dcos hdfs --name=<service-name> endpoints hdfs-site.xml
... 
$ dcos hdfs --name=<service-name> endpoints core-site.xml
...
```

## Connection Info Response

The responses are as below.

### hdfs-site.xml
```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
<configuration>
    <property>
        <name>dfs.nameservice.id</name>
        <value>hdfs</value>
    </property>
    <property>
        <name>dfs.nameservices</name>
        <value>hdfs</value>
    </property>
    <property>
        <name>dfs.ha.namenodes.hdfs</name>
        <value>name-0-node,name-1-node</value>
    </property>

    <!-- namenode -->
    <property>
        <name>dfs.namenode.shared.edits.dir</name>
        <value>qjournal://journal-0-node.hdfs.mesos:8485;journal-1-node.hdfs.mesos:8485;journal-2-node.hdfs.mesos:8485/hdfs</value>
    </property>
    <property>
        <name>dfs.namenode.name.dir</name>
        <value>/name-data</value>
    </property>
    <property>
        <name>dfs.namenode.safemode.threshold-pct</name>
        <value>0.9</value>
    </property>
    <property>
        <name>dfs.namenode.heartbeat.recheck-interval</name>
        <value>60000</value>
    </property>
    <property>
        <name>dfs.namenode.handler.count</name>
        <value>20</value>
    </property>
    <property>
        <name>dfs.namenode.invalidate.work.pct.per.iteration</name>
        <value>0.95</value>
    </property>
    <property>
        <name>dfs.namenode.replication.work.multiplier.per.iteration</name>
        <value>4</value>
    </property>
    <property>
        <name>dfs.namenode.datanode.registration.ip-hostname-check</name>
        <value>false</value>
    </property>


    <!-- name-0-node -->
    <property>
        <name>dfs.namenode.rpc-address.hdfs.name-0-node</name>
        <value>name-0-node.hdfs.mesos:9001</value>
    </property>
    <property>
        <name>dfs.namenode.rpc-bind-host.hdfs.name-0-node</name>
        <value>0.0.0.0</value>
    </property>
    <property>
        <name>dfs.namenode.http-address.hdfs.name-0-node</name>
        <value>name-0-node.hdfs.mesos:9002</value>
    </property>
    <property>
        <name>dfs.namenode.http-bind-host.hdfs.name-0-node</name>
        <value>0.0.0.0</value>
    </property>


    <!-- name-1-node -->
    <property>
        <name>dfs.namenode.rpc-address.hdfs.name-1-node</name>
        <value>name-1-node.hdfs.mesos:9001</value>
    </property>
    <property>
        <name>dfs.namenode.rpc-bind-host.hdfs.name-1-node</name>
        <value>0.0.0.0</value>
    </property>
    <property>
        <name>dfs.namenode.http-address.hdfs.name-1-node</name>
        <value>name-1-node.hdfs.mesos:9002</value>
    </property>
    <property>
        <name>dfs.namenode.http-bind-host.hdfs.name-1-node</name>
        <value>0.0.0.0</value>
    </property>

    <!-- journalnode -->
    <property>
        <name>dfs.journalnode.rpc-address</name>
        <value>0.0.0.0:8485</value>
    </property>
    <property>
        <name>dfs.journalnode.http-address</name>
        <value>0.0.0.0:8480</value>
    </property>
    <property>
        <name>dfs.journalnode.edits.dir</name>
        <value>/journal-data</value>
    </property>

    <!-- datanode -->
    <property>
        <name>dfs.datanode.address</name>
        <value>0.0.0.0:9003</value>
    </property>
    <property>
        <name>dfs.datanode.http.address</name>
        <value>0.0.0.0:9004</value>
    </property>
    <property>
        <name>dfs.datanode.ipc.address</name>
        <value>0.0.0.0:9005</value>
    </property>
    <property>
        <name>dfs.datanode.data.dir</name>
        <value>/data-data</value>
    </property>
    <property>
        <name>dfs.datanode.balance.bandwidthPerSec</name>
        <value>41943040</value>
    </property>
    <property>
        <name>dfs.datanode.handler.count</name>
        <value>10</value>
    </property>

    <!-- HA -->
    <property>
        <name>ha.zookeeper.quorum</name>
        <value>master.mesos:2181</value>
    </property>
    <property>
        <name>dfs.ha.fencing.methods</name>
        <value>shell(/bin/true)</value>
    </property>
    <property>
        <name>dfs.ha.automatic-failover.enabled</name>
        <value>true</value>
    </property>


    <property>
        <name>dfs.image.compress</name>
        <value>true</value>
    </property>
    <property>
        <name>dfs.image.compression.codec</name>
        <value>org.apache.hadoop.io.compress.SnappyCodec</value>
    </property>
    <property>
        <name>dfs.client.read.shortcircuit</name>
        <value>true</value>
    </property>
    <property>
        <name>dfs.client.read.shortcircuit.streams.cache.size</name>
        <value>1000</value>
    </property>
    <property>
        <name>dfs.client.read.shortcircuit.streams.cache.size.expiry.ms</name>
        <value>1000</value>
    </property>
    <property>
        <name>dfs.client.failover.proxy.provider.hdfs</name>
        <value>org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider</value>
    </property>
    <property>
        <name>dfs.domain.socket.path</name>
        <value>/var/lib/hadoop-hdfs/dn_socket</value>
    </property>
    <property>
        <name>dfs.permissions.enabled</name>
        <value>false</value>
    </property>

</configuration>
```

### core-site.xml
```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?><configuration>
    <property>
        <name>fs.default.name</name>
        <value>hdfs://hdfs</value>
    </property>
    <property>
        <name>hadoop.proxyuser.hue.hosts</name>
        <value>*</value>
    </property>
    <property>
        <name>hadoop.proxyuser.hue.groups</name>
        <value>*</value>
    </property>
    <property>
        <name>hadoop.proxyuser.root.hosts</name>
        <value>*</value>
    </property>
    <property>
        <name>hadoop.proxyuser.root.groups</name>
        <value>*</value>
    </property>
    <property>
        <name>hadoop.proxyuser.httpfs.groups</name>
        <value>*</value>
    </property>
    <property>
        <name>hadoop.proxyuser.httpfs.hosts</name>
        <value>*</value>
    </property>
    <property>
        <name>ha.zookeeper.parent-znode</name>
        <value>/dcos-service-hdfs/hadoop-ha</value>
    </property>

</configuration>
```

DNS names used in this configuration file will remain accurate even if nodes in the HDFS cluster are moved to different agent nodes.
