---
post_title: Connecting Clients
menu_order: 40
feature_maturity: experimental
enterprise: 'no'
---

# Connecting Clients

Applications interface with HDFS like they would any POSIX file system. However, applications that will act as client nodes of the HDFS deployment require an `hdfs-site.xml` file that provides the configuration information necessary to communicate with the cluster.

## Connection Info Using the DC/OS CLI

Executed the following command from the DC/OS CLI to retrieve the `hdfs-site.xml` file that client applications can use to connect to the cluster.

```
dcos hdfs --name=<service-name> connection hdfs-site.xml
```

## Connection Info Response

The response is as below.

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?><configuration>
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
<value>namenode-0,namenode-1</value>
</property>
<property>
<name>dfs.namenode.http-address.hdfs.namenode-0</name>
<value>namenode-0.hdfs.mesos:9002</value>
</property>
<property>
<name>dfs.namenode.rpc-bind-host.hdfs.namenode-1</name>
<value>0.0.0.0</value>
</property>
<property>
<name>dfs.namenode.http-address.hdfs.namenode-1</name>
<value>namenode-1.hdfs.mesos:9002</value>
</property>
<property>
<name>dfs.namenode.rpc-address.hdfs.namenode-0</name>
<value>namenode-0.hdfs.mesos:9001</value>
</property>
<property>
<name>dfs.namenode.rpc-bind-host.hdfs.namenode-0</name>
<value>0.0.0.0</value>
</property>
<property>
<name>dfs.namenode.http-bind-host.hdfs.namenode-1</name>
<value>0.0.0.0</value>
</property>
<property>
<name>dfs.namenode.http-bind-host.hdfs.namenode-0</name>
<value>0.0.0.0</value>
</property>
<property>
<name>dfs.namenode.rpc-address.hdfs.namenode-1</name>
<value>namenode-1.hdfs.mesos:9001</value>
</property>
<property>
<name>dfs.ha.automatic-failover.enabled</name>
<value>true</value>
</property>
<property>
<name>dfs.client.failover.proxy.provider.hdfs</name>
<value>org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider</value>
</property>
</configuration>
```

DNS names used in this configuration file will remain accurate even if nodes in the HDFS cluster are moved to different agent nodes.
