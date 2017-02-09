---
post_title: Quick Start
menu_order: 0
feature_maturity: experimental
enterprise: 'no'
---

1. Install HDFS from the DC/OS CLI.
    
    If you are using open source DC/OS, install an HDFS cluster with the following command from the DC/OS CLI. If you are using Enterprise DC/OS, you may need to follow additional instructions. See the Install and Customize section for more information.

    **Note:** Your cluster must have at least 5 private nodes.

    ```bash
    $ dcos package install hdfs
    ```

    **Note:** Alternatively, you can [install HDFS from the DC/OS GUI](https://docs.mesosphere.com/1.9/usage/managing-services/install/).

1. [SSH into a DC/OS Node](https://docs.mesosphere.com/1.9/administration/sshcluster/)

    ```bash
    $ dcos node ssh --leader --master-proxy
    ```

1. Run the hadoop client

    ```bash
    $ docker run -it mesosphere/hdfs-client:2.6.4 /bin/bash
    $ HDFS_SERVICE_NAME=hdfs ./configure-hdfs.sh
    $ cd hadoop-2.6.4
    $ ./bin/hdfs dfs -ls /
    ```

1. To configure other clients, return to the DC/OS CLI. Retrieve the `hdfs-site.xml` with the `dcos hdfs connection` command and the `hdfs-site.xml` argument:

```
$ dcos hdfs connection hdfs-site.xml
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
This command returns an XML file that can be used for client nodes of the HDFS cluster.
