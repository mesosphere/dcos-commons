---
post_title: API Reference
menu_order: 60
feature_maturity: experimental
enterprise: 'no'
---

The DC/OS HDFS Service implements a REST API that may be accessed from outside the cluster. The <dcos_url> parameter referenced below indicates the base URL of the DC/OS cluster on which the HDFS Service is deployed.

<a name="#rest-auth"></a>
# REST API Authentication
REST API requests must be authenticated. This authentication is only applicable for interacting with the HDFS REST API directly. You do not need the token to access the HDFS nodes themselves.
 
If you are using Enterprise DC/OS, follow these instructions to [create a service account and an authentication token](https://docs.mesosphere.com/1.9/administration/id-and-access-mgt/service-auth/custom-service-auth/). You can then configure your service to automatically refresh the authentication token when it expires. To get started more quickly, you can also [get the authentication token without a service account](https://docs.mesosphere.com/1.9/administration/id-and-access-mgt/iam-api/), but you will need to manually refresh the token.

If you are using open source DC/OS, follow these instructions to [pass your HTTP API token to the DC/OS endpoint](https://dcos.io/docs/1.9/administration/id-and-access-mgt/iam-api/). 

Once you have the authentication token, you can store it in an environment variable and reference it in your REST API calls:

```
$ export auth_token=uSeR_t0k3n
```

The `curl` examples in this document assume that an auth token has been stored in an environment variable named `auth_token`.

If you are using Enterprise DC/OS, the security mode of your installation may also require the `--ca-cert` flag when making REST calls. Refer to [Obtaining and passing the DC/OS certificate in cURL requests](https://docs.mesosphere.com/1.9/administration/tls-ssl/#get-dcos-cert) for information on how to use the `--cacert` flag. [If your security mode is `disabled`](https://docs.mesosphere.com/1.9/administration/tls-ssl/), do not use the `--ca-cert` flag.

# Plan API
The Plan API provides endpoints for monitoring and controlling service installation and configuration updates.

```bash
$ curl -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/plans/deploy
```
## Pause Installation

The installation will pause after completing installation of the current node and wait for user input.

```bash
$ curl -X POST -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/plans/deploy/interrupt
```

## Resume Installation

The REST API request below will resume installation at the next pending node.

```bash
$ curl -X PUT <dcos_surl>/service/hdfs/v1/plans/deploy/continue
```

# Connection API

```bash
$ curl -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/endpoints/hdfs-site.xml
```

```bash
$ curl -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/endpoints/core-site.xml
```

You will see a response similar to the following:

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
The contents of the responses represent valid hdfs-site.xml and core-site.xml that can be used by clients to connect to the service.

# Nodes API

The pods API provides endpoints for retrieving information about nodes, restarting them, and replacing them.

## List Nodes

A list of available node ids can be retrieved by sending a GET request to `/v1/pods`:

CLI Example
```
$ dcos hdfs pods list 
```

HTTP Example
```
$ curl  -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/pods
[
    "data-0",
    "data-1",
    "data-2",
    "journal-0",
    "journal-1",
    "journal-2",
    "name-0",
    "name-1",
    "zkfc-0",
    "zkfc-1"
]
```

## Node Info

You can retrieve node information by sending a GET request to `/v1/pods/<node-id>/info`:

```
$ curl  -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/pods/<node-id>/info
```

CLI Example
```
$ dcos hdfs pods info journalnode-0
```

HTTP Example
```
$ curl  -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/pods/journalnode-0/info
[{
	info: {
		name: "journal-0-node",
		taskId: {
			value: "journal-0-node__b31a70f4-73c5-4065-990c-76c0c704b8e4"
		},
		slaveId: {
			value: "0060634a-aa2b-4fcc-afa6-5569716b533a-S5"
		},
		resources: [{
			name: "cpus",
			type: "SCALAR",
			scalar: {
				value: 0.3
			},
			ranges: null,
			set: null,
			role: "hdfs-role",
			reservation: {
				principal: "hdfs-principal",
				labels: {
					labels: [{
						key: "resource_id",
						value: "4208f1ea-586f-4157-81fd-dfa0877e7472"
					}]
				}
			},
			disk: null,
			revocable: null,
			shared: null
		}, {
			name: "mem",
			type: "SCALAR",
			scalar: {
				value: 512
			},
			ranges: null,
			set: null,
			role: "hdfs-role",
			reservation: {
				principal: "hdfs-principal",
				labels: {
					labels: [{
						key: "resource_id",
						value: "a0be3c2c-3c7c-47ad-baa9-be81fb5d5f2e"
					}]
				}
			},
			disk: null,
			revocable: null,
			shared: null
		}, {
			name: "ports",
			type: "RANGES",
			scalar: null,
			ranges: {
				range: [{
					begin: 8480,
					end: 8480
				}, {
					begin: 8485,
					end: 8485
				}]
			},
			set: null,
			role: "hdfs-role",
			reservation: {
				principal: "hdfs-principal",
				labels: {
					labels: [{
						key: "resource_id",
						value: "d50b3deb-97c7-4960-89e5-ac4e508e4564"
					}]
				}
			},
			disk: null,
			revocable: null,
			shared: null
		}, {
			name: "disk",
			type: "SCALAR",
			scalar: {
				value: 5000
			},
			ranges: null,
			set: null,
			role: "hdfs-role",
			reservation: {
				principal: "hdfs-principal",
				labels: {
					labels: [{
						key: "resource_id",
						value: "3e624468-11fb-4fcf-9e67-ddb883b1718e"
					}]
				}
			},
			disk: {
				persistence: {
					id: "6bf7fcf1-ccdf-41a3-87ba-459162da1f03",
					principal: "hdfs-principal"
				},
				volume: {
					mode: "RW",
					containerPath: "journal-data",
					hostPath: null,
					image: null,
					source: null
				},
				source: null
			},
			revocable: null,
			shared: null
		}],
		executor: {
			type: null,
			executorId: {
				value: "journal__e42893b5-9d96-4dfb-8e85-8360d483a122"
			},
			frameworkId: null,
			command: {
				uris: [{
					value: "https://downloads.mesosphere.com/hdfs/assets/1.0.0-2.6.0/executor.zip",
					executable: null,
					extract: null,
					cache: null,
					outputFile: null
				}, {
					value: "https://downloads.mesosphere.com/libmesos-bundle/libmesos-bundle-1.9-argus-1.1.x-2.tar.gz",
					executable: null,
					extract: null,
					cache: null,
					outputFile: null
				}, {
					value: "https://downloads.mesosphere.com/java/jre-8u112-linux-x64-jce-unlimited.tar.gz",
					executable: null,
					extract: null,
					cache: null,
					outputFile: null
				}, {
					value: "https://downloads.mesosphere.com/hdfs/assets/hadoop-2.6.0-cdh5.9.1-dcos.tar.gz",
					executable: null,
					extract: null,
					cache: null,
					outputFile: null
				}, {
					value: "https://downloads.mesosphere.com/hdfs/assets/1.0.0-2.6.0/bootstrap.zip",
					executable: null,
					extract: null,
					cache: null,
					outputFile: null
				}, {
					value: "http://api.hdfs.marathon.l4lb.thisdcos.directory/v1/artifacts/template/25f791d8-4d42-458f-84fb-9d82842ffb3e/journal/node/core-site",
					executable: null,
					extract: false,
					cache: null,
					outputFile: "config-templates/core-site"
				}, {
					value: "http://api.hdfs.marathon.l4lb.thisdcos.directory/v1/artifacts/template/25f791d8-4d42-458f-84fb-9d82842ffb3e/journal/node/hdfs-site",
					executable: null,
					extract: false,
					cache: null,
					outputFile: "config-templates/hdfs-site"
				}, {
					value: "http://api.hdfs.marathon.l4lb.thisdcos.directory/v1/artifacts/template/25f791d8-4d42-458f-84fb-9d82842ffb3e/journal/node/hadoop-metrics2",
					executable: null,
					extract: false,
					cache: null,
					outputFile: "config-templates/hadoop-metrics2"
				}],
				environment: null,
				shell: null,
				value: "export LD_LIBRARY_PATH=$MESOS_SANDBOX/libmesos-bundle/lib:$LD_LIBRARY_PATH && export MESOS_NATIVE_JAVA_LIBRARY=$(ls $MESOS_SANDBOX/libmesos-bundle/lib/libmesos-*.so) && export JAVA_HOME=$(ls -d $MESOS_SANDBOX/jre*/) && ./executor/bin/executor",
				arguments: [],
				user: null
			},
			container: null,
			resources: [],
			name: "journal",
			source: null,
			data: null,
			discovery: null,
			shutdownGracePeriod: null,
			labels: null
		},
		command: {
			uris: [],
			environment: {
				variables: [{
					name: "PERMISSIONS_ENABLED",
					value: "false"
				}, {
					name: "DATA_NODE_BALANCE_BANDWIDTH_PER_SEC",
					value: "41943040"
				}, {
					name: "NAME_NODE_HANDLER_COUNT",
					value: "20"
				}, {
					name: "CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE",
					value: "1000"
				}, {
					name: "HADOOP_ROOT_LOGGER",
					value: "INFO,console"
				}, {
					name: "HA_FENCING_METHODS",
					value: "shell(/bin/true)"
				}, {
					name: "SERVICE_ZK_ROOT",
					value: "dcos-service-hdfs"
				}, {
					name: "HADOOP_PROXYUSER_HUE_GROUPS",
					value: "*"
				}, {
					name: "NAME_NODE_HEARTBEAT_RECHECK_INTERVAL",
					value: "60000"
				}, {
					name: "HADOOP_PROXYUSER_HUE_HOSTS",
					value: "*"
				}, {
					name: "CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS",
					value: "1000"
				}, {
					name: "JOURNAL_NODE_RPC_PORT",
					value: "8485"
				}, {
					name: "CLIENT_FAILOVER_PROXY_PROVIDER_HDFS",
					value: "org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider"
				}, {
					name: "DATA_NODE_HANDLER_COUNT",
					value: "10"
				}, {
					name: "HA_AUTOMATIC_FAILURE",
					value: "true"
				}, {
					name: "JOURNALNODE",
					value: "true"
				}, {
					name: "NAME_NODE_REPLICATION_WORK_MULTIPLIER_PER_ITERATION",
					value: "4"
				}, {
					name: "HADOOP_PROXYUSER_HTTPFS_HOSTS",
					value: "*"
				}, {
					name: "POD_INSTANCE_INDEX",
					value: "0"
				}, {
					name: "DATA_NODE_IPC_PORT",
					value: "9005"
				}, {
					name: "JOURNAL_NODE_HTTP_PORT",
					value: "8480"
				}, {
					name: "NAME_NODE_DATA_NODE_REGISTRATION_IP_HOSTNAME_CHECK",
					value: "false"
				}, {
					name: "TASK_USER",
					value: "root"
				}, {
					name: "journal-0-node",
					value: "true"
				}, {
					name: "HADOOP_PROXYUSER_ROOT_GROUPS",
					value: "*"
				}, {
					name: "TASK_NAME",
					value: "journal-0-node"
				}, {
					name: "HADOOP_PROXYUSER_ROOT_HOSTS",
					value: "*"
				}, {
					name: "IMAGE_COMPRESS",
					value: "true"
				}, {
					name: "CLIENT_READ_SHORTCIRCUIT",
					value: "true"
				}, {
					name: "FRAMEWORK_NAME",
					value: "hdfs"
				}, {
					name: "IMAGE_COMPRESSION_CODEC",
					value: "org.apache.hadoop.io.compress.SnappyCodec"
				}, {
					name: "NAME_NODE_SAFEMODE_THRESHOLD_PCT",
					value: "0.9"
				}, {
					name: "NAME_NODE_INVALIDATE_WORK_PCT_PER_ITERATION",
					value: "0.95"
				}, {
					name: "HADOOP_PROXYUSER_HTTPFS_GROUPS",
					value: "*"
				}, {
					name: "CLIENT_READ_SHORTCIRCUIT_PATH",
					value: "/var/lib/hadoop-hdfs/dn_socket"
				}, {
					name: "DATA_NODE_HTTP_PORT",
					value: "9004"
				}, {
					name: "DATA_NODE_RPC_PORT",
					value: "9003"
				}, {
					name: "NAME_NODE_HTTP_PORT",
					value: "9002"
				}, {
					name: "NAME_NODE_RPC_PORT",
					value: "9001"
				}, {
					name: "CONFIG_TEMPLATE_CORE_SITE",
					value: "config-templates/core-site,hadoop-2.6.0-cdh5.9.1/etc/hadoop/core-site.xml"
				}, {
					name: "CONFIG_TEMPLATE_HDFS_SITE",
					value: "config-templates/hdfs-site,hadoop-2.6.0-cdh5.9.1/etc/hadoop/hdfs-site.xml"
				}, {
					name: "CONFIG_TEMPLATE_HADOOP_METRICS2",
					value: "config-templates/hadoop-metrics2,hadoop-2.6.0-cdh5.9.1/etc/hadoop/hadoop-metrics2.properties"
				}, {
					name: "PORT_JOURNAL_RPC",
					value: "8485"
				}, {
					name: "PORT_JOURNAL_HTTP",
					value: "8480"
				}]
			},
			shell: null,
			value: "./bootstrap && ./hadoop-2.6.0-cdh5.9.1/bin/hdfs journalnode",
			arguments: [],
			user: null
		},
		container: null,
		healthCheck: null,
		killPolicy: null,
		data: null,
		labels: {
			labels: [{
				key: "goal_state",
				value: "RUNNING"
			}, {
				key: "offer_attributes",
				value: ""
			}, {
				key: "task_type",
				value: "journal"
			}, {
				key: "index",
				value: "0"
			}, {
				key: "offer_hostname",
				value: "10.0.1.23"
			}, {
				key: "target_configuration",
				value: "4bdb3f97-96b0-4e78-8d47-f39edc33f6e3"
			}]
		},
		discovery: null
	},
	status: {
		taskId: {
			value: "journal-0-node__b31a70f4-73c5-4065-990c-76c0c704b8e4"
		},
		state: "TASK_RUNNING",
		message: "Reconciliation: Latest task state",
		source: "SOURCE_MASTER",
		reason: "REASON_RECONCILIATION",
		data: null,
		slaveId: {
			value: "0060634a-aa2b-4fcc-afa6-5569716b533a-S5"
		},
		executorId: null,
		timestamp: 1486694618.923135,
		uuid: null,
		healthy: null,
		labels: null,
		containerStatus: {
			containerId: {
				value: "a4c8433f-2648-4ba7-a8b8-5fe5df20e8af",
				parent: null
			},
			networkInfos: [{
				ipAddresses: [{
					protocol: null,
					ipAddress: "10.0.1.23"
				}],
				name: null,
				groups: [],
				labels: null,
				portMappings: []
			}],
			cgroupInfo: null,
			executorPid: 5594
		},
		unreachableTime: null
	}
}]
```

## Replace a Node

The replace endpoint can be used to replace a node with an instance running on another agent node.

CLI Example
```
$ dcos hdfs pods replace <node-id>
```

HTTP Example
```
$ curl -X POST -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/pods/<node-id>/replace
```

If the operation succeeds, a `200 OK` is returned.

## Restart a Node

The restart endpoint can be used to restart a node in place on the same agent node.

CLI Example
```
$ dcos hdfs pods restart <node-id>
```

HTTP Example
```bash
$ curl -X POST -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/pods/<node-id>/restart
```

If the operation succeeds a `200 OK` is returned.

# Configuration API

The configuration API provides an endpoint to view current and previous configurations of the cluster.

## View Target Config

You can view the current target configuration by sending a GET request to `/v1/configurations/target`.

CLI Example
```
$ dcos hdfs config target
```

HTTP Example
```
$ curl -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/configurations/target
{
	name: "hdfs",
	role: "hdfs-role",
	principal: "hdfs-principal",
	api - port: 10002,
	web - url: null,
	zookeeper: "master.mesos:2181",
	pod - specs: [{
		type: "journal",
		user: null,
		count: 3,
		container: null,
		uris: [
			"https://downloads.mesosphere.com/hdfs/assets/hadoop-2.6.0-cdh5.9.1-dcos.tar.gz",
			"https://downloads.mesosphere.com/hdfs/assets/1.0.0-2.6.0/bootstrap.zip"
		],
		task - specs: [{
			name: "node",
			goal: "RUNNING",
			resource - set: {
				id: "node-resource-set",
				resource - specifications: [{
					@type: "DefaultResourceSpec",
					name: "cpus",
					value: {
						type: "SCALAR",
						scalar: {
							value: 0.3
						},
						ranges: null,
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					envKey: null
				}, {
					@type: "DefaultResourceSpec",
					name: "mem",
					value: {
						type: "SCALAR",
						scalar: {
							value: 512
						},
						ranges: null,
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					envKey: null
				}, {
					@type: "PortsSpec",
					name: "ports",
					value: {
						type: "RANGES",
						scalar: null,
						ranges: {
							range: [{
								begin: 8485,
								end: 8485
							}, {
								begin: 8480,
								end: 8480
							}]
						},
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					port - specs: [{
						@type: "PortSpec",
						name: "ports",
						value: {
							type: "RANGES",
							scalar: null,
							ranges: {
								range: [{
									begin: 8485,
									end: 8485
								}]
							},
							set: null,
							text: null
						},
						role: "hdfs-role",
						principal: "hdfs-principal",
						port - name: "journal-rpc",
						envKey: null
					}, {
						@type: "PortSpec",
						name: "ports",
						value: {
							type: "RANGES",
							scalar: null,
							ranges: {
								range: [{
									begin: 8480,
									end: 8480
								}]
							},
							set: null,
							text: null
						},
						role: "hdfs-role",
						principal: "hdfs-principal",
						port - name: "journal-http",
						envKey: null
					}],
					envKey: null
				}],
				volume - specifications: [{
					@type: "DefaultVolumeSpec",
					type: "ROOT",
					container - path: "journal-data",
					name: "disk",
					value: {
						type: "SCALAR",
						scalar: {
							value: 5000
						},
						ranges: null,
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					envKey: "DISK_SIZE"
				}],
				role: "hdfs-role",
				principal: "hdfs-principal"
			},
			command - spec: {
				value: "./bootstrap && ./hadoop-2.6.0-cdh5.9.1/bin/hdfs journalnode",
				environment: {
					CLIENT_FAILOVER_PROXY_PROVIDER_HDFS: "org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider",
					CLIENT_READ_SHORTCIRCUIT: "true",
					CLIENT_READ_SHORTCIRCUIT_PATH: "/var/lib/hadoop-hdfs/dn_socket",
					CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE: "1000",
					CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS: "1000",
					DATA_NODE_BALANCE_BANDWIDTH_PER_SEC: "41943040",
					DATA_NODE_HANDLER_COUNT: "10",
					DATA_NODE_HTTP_PORT: "9004",
					DATA_NODE_IPC_PORT: "9005",
					DATA_NODE_RPC_PORT: "9003",
					HADOOP_PROXYUSER_HTTPFS_GROUPS: "*",
					HADOOP_PROXYUSER_HTTPFS_HOSTS: "*",
					HADOOP_PROXYUSER_HUE_GROUPS: "*",
					HADOOP_PROXYUSER_HUE_HOSTS: "*",
					HADOOP_PROXYUSER_ROOT_GROUPS: "*",
					HADOOP_PROXYUSER_ROOT_HOSTS: "*",
					HADOOP_ROOT_LOGGER: "INFO,console",
					HA_AUTOMATIC_FAILURE: "true",
					HA_FENCING_METHODS: "shell(/bin/true)",
					IMAGE_COMPRESS: "true",
					IMAGE_COMPRESSION_CODEC: "org.apache.hadoop.io.compress.SnappyCodec",
					JOURNALNODE: "true",
					JOURNAL_NODE_HTTP_PORT: "8480",
					JOURNAL_NODE_RPC_PORT: "8485",
					NAME_NODE_DATA_NODE_REGISTRATION_IP_HOSTNAME_CHECK: "false",
					NAME_NODE_HANDLER_COUNT: "20",
					NAME_NODE_HEARTBEAT_RECHECK_INTERVAL: "60000",
					NAME_NODE_HTTP_PORT: "9002",
					NAME_NODE_INVALIDATE_WORK_PCT_PER_ITERATION: "0.95",
					NAME_NODE_REPLICATION_WORK_MULTIPLIER_PER_ITERATION: "4",
					NAME_NODE_RPC_PORT: "9001",
					NAME_NODE_SAFEMODE_THRESHOLD_PCT: "0.9",
					PERMISSIONS_ENABLED: "false",
					SERVICE_ZK_ROOT: "dcos-service-hdfs",
					TASK_USER: "root"
				}
			},
			health - check - spec: null,
			readiness - check - spec: null,
			config - files: [{
				name: "core-site",
				relative - path: "hadoop-2.6.0-cdh5.9.1/etc/hadoop/core-site.xml",
				template - content: "<?xml version="
				1.0 " encoding="
				UTF - 8 " standalone="
				no "?> <?xml-stylesheet type="
				text / xsl " href="
				configuration.xsl "?><configuration> <property> <name>fs.default.name</name> <value>hdfs://hdfs</value> </property> <property> <name>hadoop.proxyuser.hue.hosts</name> <value>{{HADOOP_PROXYUSER_HUE_HOSTS}}</value> </property> <property> <name>hadoop.proxyuser.hue.groups</name> <value>{{HADOOP_PROXYUSER_HUE_GROUPS}}</value> </property> <property> <name>hadoop.proxyuser.root.hosts</name> <value>{{HADOOP_PROXYUSER_ROOT_HOSTS}}</value> </property> <property> <name>hadoop.proxyuser.root.groups</name> <value>{{HADOOP_PROXYUSER_ROOT_GROUPS}}</value> </property> <property> <name>hadoop.proxyuser.httpfs.groups</name> <value>{{HADOOP_PROXYUSER_HTTPFS_GROUPS}}</value> </property> <property> <name>hadoop.proxyuser.httpfs.hosts</name> <value>{{HADOOP_PROXYUSER_HTTPFS_HOSTS}}</value> </property> <property> <name>ha.zookeeper.parent-znode</name> <value>/{{SERVICE_ZK_ROOT}}/hadoop-ha</value> </property> {{#SECURE_MODE}} <property> <!-- The ZKFC nodes use this property to verify they are connecting to the namenode with the expected principal. --> <name>hadoop.security.service.user.name.key.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>hadoop.security.authentication</name> <value>kerberos</value> </property> <property> <name>hadoop.security.authorization</name> <value>true</value> </property> {{/SECURE_MODE}} </configuration> "
			}, {
				name: "hdfs-site",
				relative - path: "hadoop-2.6.0-cdh5.9.1/etc/hadoop/hdfs-site.xml",
				template - content: "<?xml version="
				1.0 " encoding="
				UTF - 8 " standalone="
				no "?> <?xml-stylesheet type="
				text / xsl " href="
				configuration.xsl "?> <configuration> <property> <name>dfs.nameservice.id</name> <value>hdfs</value> </property> <property> <name>dfs.nameservices</name> <value>hdfs</value> </property> <property> <name>dfs.ha.namenodes.hdfs</name> <value>name-0-node,name-1-node</value> </property> <!-- namenode --> <property> <name>dfs.namenode.shared.edits.dir</name> <value>qjournal://journal-0-node.{{FRAMEWORK_NAME}}.mesos:{{JOURNAL_NODE_RPC_PORT}};journal-1-node.{{FRAMEWORK_NAME}}.mesos:{{JOURNAL_NODE_RPC_PORT}};journal-2-node.{{FRAMEWORK_NAME}}.mesos:{{JOURNAL_NODE_RPC_PORT}}/hdfs</value> </property> <property> <name>dfs.namenode.name.dir</name> <value>{{MESOS_SANDBOX}}/name-data</value> </property> <property> <name>dfs.namenode.safemode.threshold-pct</name> <value>{{NAME_NODE_SAFEMODE_THRESHOLD_PCT}}</value> </property> <property> <name>dfs.namenode.heartbeat.recheck-interval</name> <value>{{NAME_NODE_HEARTBEAT_RECHECK_INTERVAL}}</value> </property> <property> <name>dfs.namenode.handler.count</name> <value>{{NAME_NODE_HANDLER_COUNT}}</value> </property> <property> <name>dfs.namenode.invalidate.work.pct.per.iteration</name> <value>{{NAME_NODE_INVALIDATE_WORK_PCT_PER_ITERATION}}</value> </property> <property> <name>dfs.namenode.replication.work.multiplier.per.iteration</name> <value>{{NAME_NODE_REPLICATION_WORK_MULTIPLIER_PER_ITERATION}}</value> </property> <property> <name>dfs.namenode.datanode.registration.ip-hostname-check</name> <value>{{NAME_NODE_DATA_NODE_REGISTRATION_IP_HOSTNAME_CHECK}}</value> </property> <!-- name-0-node --> <property> <name>dfs.namenode.rpc-address.hdfs.name-0-node</name> <value>name-0-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.namenode.rpc-bind-host.hdfs.name-0-node</name> <value>0.0.0.0</value> </property> <property> <name>dfs.namenode.http-address.hdfs.name-0-node</name> <value>name-0-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.namenode.http-bind-host.hdfs.name-0-node</name> <value>0.0.0.0</value> </property> <!-- name-1-node --> <property> <name>dfs.namenode.rpc-address.hdfs.name-1-node</name> <value>name-1-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.namenode.rpc-bind-host.hdfs.name-1-node</name> <value>0.0.0.0</value> </property> <property> <name>dfs.namenode.http-address.hdfs.name-1-node</name> <value>name-1-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.namenode.http-bind-host.hdfs.name-1-node</name> <value>0.0.0.0</value> </property> <!-- journalnode --> <property> <name>dfs.journalnode.rpc-address</name> <value>0.0.0.0:{{JOURNAL_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.journalnode.http-address</name> <value>0.0.0.0:{{JOURNAL_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.journalnode.edits.dir</name> <value>{{MESOS_SANDBOX}}/journal-data</value> </property> <!-- datanode --> <property> <name>dfs.datanode.address</name> <value>0.0.0.0:{{DATA_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.datanode.http.address</name> <value>0.0.0.0:{{DATA_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.datanode.ipc.address</name> <value>0.0.0.0:{{DATA_NODE_IPC_PORT}}</value> </property> <property> <name>dfs.datanode.data.dir</name> <value>{{MESOS_SANDBOX}}/data-data</value> </property> <property> <name>dfs.datanode.balance.bandwidthPerSec</name> <value>41943040</value> </property> <property> <name>dfs.datanode.handler.count</name> <value>{{DATA_NODE_HANDLER_COUNT}}</value> </property> <!-- HA --> <property> <name>ha.zookeeper.quorum</name> <value>master.mesos:2181</value> </property> <property> <name>dfs.ha.fencing.methods</name> <value>{{HA_FENCING_METHODS}}</value> </property> <property> <name>dfs.ha.automatic-failover.enabled</name> <value>{{HA_AUTOMATIC_FAILURE}}</value> </property> {{#NAMENODE}} <property> <name>dfs.ha.namenode.id</name> <value>name-{{POD_INSTANCE_INDEX}}-node</value> </property> {{/NAMENODE}} <property> <name>dfs.image.compress</name> <value>{{IMAGE_COMPRESS}}</value> </property> <property> <name>dfs.image.compression.codec</name> <value>{{IMAGE_COMPRESSION_CODEC}}</value> </property> <property> <name>dfs.client.read.shortcircuit</name> <value>{{CLIENT_READ_SHORTCIRCUIT}}</value> </property> <property> <name>dfs.client.read.shortcircuit.streams.cache.size</name> <value>{{CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE}}</value> </property> <property> <name>dfs.client.read.shortcircuit.streams.cache.size.expiry.ms</name> <value>{{CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS}}</value> </property> <property> <name>dfs.client.failover.proxy.provider.hdfs</name> <value>{{CLIENT_FAILOVER_PROXY_PROVIDER_HDFS}}</value> </property> <property> <name>dfs.domain.socket.path</name> <value>{{CLIENT_READ_SHORTCIRCUIT_PATH}}</value> </property> <property> <name>dfs.permissions.enabled</name> <value>{{PERMISSIONS_ENABLED}}</value> </property> {{#SECURE_MODE}} <property> <name>ignore.secure.ports.for.testing</name> <value>true</value> </property> <!-- Security Configuration --> <property> <name>hadoop.security.auth_to_local</name> <value> RULE:[2:$1@$0](.*)s/.*/{{TASK_USER}}/ RULE:[1:$1@$0](.*)s/.*/{{TASK_USER}}/ </value> </property> <property> <name>dfs.block.access.token.enable</name> <value>true</value> </property> <property> <name>dfs.namenode.kerberos.principal.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.datanode.kerberos.principal.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.journalnode.kerberos.principal.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.cluster.administrators</name> <value>core,root,hdfs,nobody</value> </property> <property> <name>dfs.web.authentication.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/{{TASK_NAME}}.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.web.authentication.kerberos.keytab</name> <value>keytabs/{{KERBEROS_PRIMARY}}.{{TASK_NAME}}.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> {{#DATANODE}} <!-- DataNode Security Configuration --> <property> <name>dfs.datanode.keytab.file</name> <value>keytabs/{{KERBEROS_PRIMARY}}.data-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.datanode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/data-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.datanode.data.dir.perm</name> <value>700</value> </property> {{/DATANODE}} {{#NAMENODE}} <!-- NameNode Security Configuration --> <property> <name>dfs.namenode.keytab.file</name> <value>keytabs/{{KERBEROS_PRIMARY}}.name-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.namenode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/name-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.namenode.kerberos.internal.spnego.principal</name> <value>{{KERBEROS_PRIMARY_HTTP}}/name-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> {{/NAMENODE}} {{#ZKFC}} <!-- NameNode Security Configuration --> <property> <name>dfs.namenode.keytab.file</name> <value>keytabs/{{KERBEROS_PRIMARY}}.zkfc-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.namenode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/zkfc-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.namenode.kerberos.internal.spnego.principal</name> <value>{{KERBEROS_PRIMARY_HTTP}}/zkfc-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> {{/ZKFC}} {{#JOURNALNODE}} <!-- JournalNode Security Configuration --> <property> <name>dfs.journalnode.keytab.file</name> <value>keytabs/hdfs.journal-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.journalnode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/journal-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.journalnode.kerberos.internal.spnego.principal</name> <value>{{KERBEROS_PRIMARY_HTTP}}/journal-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> {{/JOURNALNODE}} {{/SECURE_MODE}} </configuration> "
			}, {
				name: "hadoop-metrics2",
				relative - path: "hadoop-2.6.0-cdh5.9.1/etc/hadoop/hadoop-metrics2.properties",
				template - content: "# Autogenerated by the Mesos Framework, DO NOT EDIT *.sink.statsd.class=org.apache.hadoop.metrics2.sink.StatsDSink journalnode.sink.statsd.period=10 journalnode.sink.statsd.server.host={{STATSD_UDP_HOST}} journalnode.sink.statsd.server.port={{STATSD_UDP_PORT}} journalnode.sink.statsd.skip.hostname=false"
			}]
		}],
		placement - rule: {
			@type: "AndRule",
			rules: [{
				@type: "TaskTypeRule",
				type: "journal",
				converter: {
					@type: "TaskTypeLabelConverter"
				},
				behavior: "AVOID"
			}, {
				@type: "TaskTypeRule",
				type: "name",
				converter: {
					@type: "TaskTypeLabelConverter"
				},
				behavior: "AVOID"
			}]
		}
	}, {
		type: "name",
		user: null,
		count: 2,
		container: null,
		uris: [
			"https://downloads.mesosphere.com/hdfs/assets/hadoop-2.6.0-cdh5.9.1-dcos.tar.gz",
			"https://downloads.mesosphere.com/hdfs/assets/1.0.0-2.6.0/bootstrap.zip"
		],
		task - specs: [{
			name: "node",
			goal: "RUNNING",
			resource - set: {
				id: "name-resources",
				resource - specifications: [{
					@type: "DefaultResourceSpec",
					name: "cpus",
					value: {
						type: "SCALAR",
						scalar: {
							value: 0.3
						},
						ranges: null,
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					envKey: null
				}, {
					@type: "DefaultResourceSpec",
					name: "mem",
					value: {
						type: "SCALAR",
						scalar: {
							value: 512
						},
						ranges: null,
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					envKey: null
				}, {
					@type: "PortsSpec",
					name: "ports",
					value: {
						type: "RANGES",
						scalar: null,
						ranges: {
							range: [{
								begin: 9001,
								end: 9001
							}, {
								begin: 9002,
								end: 9002
							}]
						},
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					port - specs: [{
						@type: "PortSpec",
						name: "ports",
						value: {
							type: "RANGES",
							scalar: null,
							ranges: {
								range: [{
									begin: 9001,
									end: 9001
								}]
							},
							set: null,
							text: null
						},
						role: "hdfs-role",
						principal: "hdfs-principal",
						port - name: "name-rpc",
						envKey: null
					}, {
						@type: "PortSpec",
						name: "ports",
						value: {
							type: "RANGES",
							scalar: null,
							ranges: {
								range: [{
									begin: 9002,
									end: 9002
								}]
							},
							set: null,
							text: null
						},
						role: "hdfs-role",
						principal: "hdfs-principal",
						port - name: "name-http",
						envKey: null
					}],
					envKey: null
				}],
				volume - specifications: [{
					@type: "DefaultVolumeSpec",
					type: "ROOT",
					container - path: "name-data",
					name: "disk",
					value: {
						type: "SCALAR",
						scalar: {
							value: 5000
						},
						ranges: null,
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					envKey: "DISK_SIZE"
				}],
				role: "hdfs-role",
				principal: "hdfs-principal"
			},
			command - spec: {
				value: "./bootstrap && ./hadoop-2.6.0-cdh5.9.1/bin/hdfs namenode",
				environment: {
					CLIENT_FAILOVER_PROXY_PROVIDER_HDFS: "org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider",
					CLIENT_READ_SHORTCIRCUIT: "true",
					CLIENT_READ_SHORTCIRCUIT_PATH: "/var/lib/hadoop-hdfs/dn_socket",
					CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE: "1000",
					CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS: "1000",
					DATA_NODE_BALANCE_BANDWIDTH_PER_SEC: "41943040",
					DATA_NODE_HANDLER_COUNT: "10",
					DATA_NODE_HTTP_PORT: "9004",
					DATA_NODE_IPC_PORT: "9005",
					DATA_NODE_RPC_PORT: "9003",
					FRAMEWORK_NAME: "",
					HADOOP_PROXYUSER_HTTPFS_GROUPS: "*",
					HADOOP_PROXYUSER_HTTPFS_HOSTS: "*",
					HADOOP_PROXYUSER_HUE_GROUPS: "*",
					HADOOP_PROXYUSER_HUE_HOSTS: "*",
					HADOOP_PROXYUSER_ROOT_GROUPS: "*",
					HADOOP_PROXYUSER_ROOT_HOSTS: "*",
					HADOOP_ROOT_LOGGER: "INFO,console",
					HA_AUTOMATIC_FAILURE: "true",
					HA_FENCING_METHODS: "shell(/bin/true)",
					IMAGE_COMPRESS: "true",
					IMAGE_COMPRESSION_CODEC: "org.apache.hadoop.io.compress.SnappyCodec",
					JOURNAL_NODE_HTTP_PORT: "8480",
					JOURNAL_NODE_RPC_PORT: "8485",
					NAMENODE: "true",
					NAME_NODE_DATA_NODE_REGISTRATION_IP_HOSTNAME_CHECK: "false",
					NAME_NODE_HANDLER_COUNT: "20",
					NAME_NODE_HEARTBEAT_RECHECK_INTERVAL: "60000",
					NAME_NODE_HTTP_PORT: "9002",
					NAME_NODE_INVALIDATE_WORK_PCT_PER_ITERATION: "0.95",
					NAME_NODE_REPLICATION_WORK_MULTIPLIER_PER_ITERATION: "4",
					NAME_NODE_RPC_PORT: "9001",
					NAME_NODE_SAFEMODE_THRESHOLD_PCT: "0.9",
					PERMISSIONS_ENABLED: "false",
					SERVICE_ZK_ROOT: "dcos-service-hdfs",
					TASK_USER: "root"
				}
			},
			health - check - spec: null,
			readiness - check - spec: {
				command: "./hadoop-2.6.0-cdh5.9.1/bin/hdfs haadmin -getServiceState name-$POD_INSTANCE_INDEX-node",
				delay: 0,
				interval: 5,
				timeout: 60
			},
			config - files: [{
				name: "core-site",
				relative - path: "hadoop-2.6.0-cdh5.9.1/etc/hadoop/core-site.xml",
				template - content: "<?xml version="
				1.0 " encoding="
				UTF - 8 " standalone="
				no "?> <?xml-stylesheet type="
				text / xsl " href="
				configuration.xsl "?><configuration> <property> <name>fs.default.name</name> <value>hdfs://hdfs</value> </property> <property> <name>hadoop.proxyuser.hue.hosts</name> <value>{{HADOOP_PROXYUSER_HUE_HOSTS}}</value> </property> <property> <name>hadoop.proxyuser.hue.groups</name> <value>{{HADOOP_PROXYUSER_HUE_GROUPS}}</value> </property> <property> <name>hadoop.proxyuser.root.hosts</name> <value>{{HADOOP_PROXYUSER_ROOT_HOSTS}}</value> </property> <property> <name>hadoop.proxyuser.root.groups</name> <value>{{HADOOP_PROXYUSER_ROOT_GROUPS}}</value> </property> <property> <name>hadoop.proxyuser.httpfs.groups</name> <value>{{HADOOP_PROXYUSER_HTTPFS_GROUPS}}</value> </property> <property> <name>hadoop.proxyuser.httpfs.hosts</name> <value>{{HADOOP_PROXYUSER_HTTPFS_HOSTS}}</value> </property> <property> <name>ha.zookeeper.parent-znode</name> <value>/{{SERVICE_ZK_ROOT}}/hadoop-ha</value> </property> {{#SECURE_MODE}} <property> <!-- The ZKFC nodes use this property to verify they are connecting to the namenode with the expected principal. --> <name>hadoop.security.service.user.name.key.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>hadoop.security.authentication</name> <value>kerberos</value> </property> <property> <name>hadoop.security.authorization</name> <value>true</value> </property> {{/SECURE_MODE}} </configuration> "
			}, {
				name: "hdfs-site",
				relative - path: "hadoop-2.6.0-cdh5.9.1/etc/hadoop/hdfs-site.xml",
				template - content: "<?xml version="
				1.0 " encoding="
				UTF - 8 " standalone="
				no "?> <?xml-stylesheet type="
				text / xsl " href="
				configuration.xsl "?> <configuration> <property> <name>dfs.nameservice.id</name> <value>hdfs</value> </property> <property> <name>dfs.nameservices</name> <value>hdfs</value> </property> <property> <name>dfs.ha.namenodes.hdfs</name> <value>name-0-node,name-1-node</value> </property> <!-- namenode --> <property> <name>dfs.namenode.shared.edits.dir</name> <value>qjournal://journal-0-node.{{FRAMEWORK_NAME}}.mesos:{{JOURNAL_NODE_RPC_PORT}};journal-1-node.{{FRAMEWORK_NAME}}.mesos:{{JOURNAL_NODE_RPC_PORT}};journal-2-node.{{FRAMEWORK_NAME}}.mesos:{{JOURNAL_NODE_RPC_PORT}}/hdfs</value> </property> <property> <name>dfs.namenode.name.dir</name> <value>{{MESOS_SANDBOX}}/name-data</value> </property> <property> <name>dfs.namenode.safemode.threshold-pct</name> <value>{{NAME_NODE_SAFEMODE_THRESHOLD_PCT}}</value> </property> <property> <name>dfs.namenode.heartbeat.recheck-interval</name> <value>{{NAME_NODE_HEARTBEAT_RECHECK_INTERVAL}}</value> </property> <property> <name>dfs.namenode.handler.count</name> <value>{{NAME_NODE_HANDLER_COUNT}}</value> </property> <property> <name>dfs.namenode.invalidate.work.pct.per.iteration</name> <value>{{NAME_NODE_INVALIDATE_WORK_PCT_PER_ITERATION}}</value> </property> <property> <name>dfs.namenode.replication.work.multiplier.per.iteration</name> <value>{{NAME_NODE_REPLICATION_WORK_MULTIPLIER_PER_ITERATION}}</value> </property> <property> <name>dfs.namenode.datanode.registration.ip-hostname-check</name> <value>{{NAME_NODE_DATA_NODE_REGISTRATION_IP_HOSTNAME_CHECK}}</value> </property> <!-- name-0-node --> <property> <name>dfs.namenode.rpc-address.hdfs.name-0-node</name> <value>name-0-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.namenode.rpc-bind-host.hdfs.name-0-node</name> <value>0.0.0.0</value> </property> <property> <name>dfs.namenode.http-address.hdfs.name-0-node</name> <value>name-0-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.namenode.http-bind-host.hdfs.name-0-node</name> <value>0.0.0.0</value> </property> <!-- name-1-node --> <property> <name>dfs.namenode.rpc-address.hdfs.name-1-node</name> <value>name-1-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.namenode.rpc-bind-host.hdfs.name-1-node</name> <value>0.0.0.0</value> </property> <property> <name>dfs.namenode.http-address.hdfs.name-1-node</name> <value>name-1-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.namenode.http-bind-host.hdfs.name-1-node</name> <value>0.0.0.0</value> </property> <!-- journalnode --> <property> <name>dfs.journalnode.rpc-address</name> <value>0.0.0.0:{{JOURNAL_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.journalnode.http-address</name> <value>0.0.0.0:{{JOURNAL_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.journalnode.edits.dir</name> <value>{{MESOS_SANDBOX}}/journal-data</value> </property> <!-- datanode --> <property> <name>dfs.datanode.address</name> <value>0.0.0.0:{{DATA_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.datanode.http.address</name> <value>0.0.0.0:{{DATA_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.datanode.ipc.address</name> <value>0.0.0.0:{{DATA_NODE_IPC_PORT}}</value> </property> <property> <name>dfs.datanode.data.dir</name> <value>{{MESOS_SANDBOX}}/data-data</value> </property> <property> <name>dfs.datanode.balance.bandwidthPerSec</name> <value>41943040</value> </property> <property> <name>dfs.datanode.handler.count</name> <value>{{DATA_NODE_HANDLER_COUNT}}</value> </property> <!-- HA --> <property> <name>ha.zookeeper.quorum</name> <value>master.mesos:2181</value> </property> <property> <name>dfs.ha.fencing.methods</name> <value>{{HA_FENCING_METHODS}}</value> </property> <property> <name>dfs.ha.automatic-failover.enabled</name> <value>{{HA_AUTOMATIC_FAILURE}}</value> </property> {{#NAMENODE}} <property> <name>dfs.ha.namenode.id</name> <value>name-{{POD_INSTANCE_INDEX}}-node</value> </property> {{/NAMENODE}} <property> <name>dfs.image.compress</name> <value>{{IMAGE_COMPRESS}}</value> </property> <property> <name>dfs.image.compression.codec</name> <value>{{IMAGE_COMPRESSION_CODEC}}</value> </property> <property> <name>dfs.client.read.shortcircuit</name> <value>{{CLIENT_READ_SHORTCIRCUIT}}</value> </property> <property> <name>dfs.client.read.shortcircuit.streams.cache.size</name> <value>{{CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE}}</value> </property> <property> <name>dfs.client.read.shortcircuit.streams.cache.size.expiry.ms</name> <value>{{CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS}}</value> </property> <property> <name>dfs.client.failover.proxy.provider.hdfs</name> <value>{{CLIENT_FAILOVER_PROXY_PROVIDER_HDFS}}</value> </property> <property> <name>dfs.domain.socket.path</name> <value>{{CLIENT_READ_SHORTCIRCUIT_PATH}}</value> </property> <property> <name>dfs.permissions.enabled</name> <value>{{PERMISSIONS_ENABLED}}</value> </property> {{#SECURE_MODE}} <property> <name>ignore.secure.ports.for.testing</name> <value>true</value> </property> <!-- Security Configuration --> <property> <name>hadoop.security.auth_to_local</name> <value> RULE:[2:$1@$0](.*)s/.*/{{TASK_USER}}/ RULE:[1:$1@$0](.*)s/.*/{{TASK_USER}}/ </value> </property> <property> <name>dfs.block.access.token.enable</name> <value>true</value> </property> <property> <name>dfs.namenode.kerberos.principal.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.datanode.kerberos.principal.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.journalnode.kerberos.principal.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.cluster.administrators</name> <value>core,root,hdfs,nobody</value> </property> <property> <name>dfs.web.authentication.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/{{TASK_NAME}}.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.web.authentication.kerberos.keytab</name> <value>keytabs/{{KERBEROS_PRIMARY}}.{{TASK_NAME}}.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> {{#DATANODE}} <!-- DataNode Security Configuration --> <property> <name>dfs.datanode.keytab.file</name> <value>keytabs/{{KERBEROS_PRIMARY}}.data-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.datanode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/data-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.datanode.data.dir.perm</name> <value>700</value> </property> {{/DATANODE}} {{#NAMENODE}} <!-- NameNode Security Configuration --> <property> <name>dfs.namenode.keytab.file</name> <value>keytabs/{{KERBEROS_PRIMARY}}.name-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.namenode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/name-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.namenode.kerberos.internal.spnego.principal</name> <value>{{KERBEROS_PRIMARY_HTTP}}/name-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> {{/NAMENODE}} {{#ZKFC}} <!-- NameNode Security Configuration --> <property> <name>dfs.namenode.keytab.file</name> <value>keytabs/{{KERBEROS_PRIMARY}}.zkfc-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.namenode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/zkfc-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.namenode.kerberos.internal.spnego.principal</name> <value>{{KERBEROS_PRIMARY_HTTP}}/zkfc-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> {{/ZKFC}} {{#JOURNALNODE}} <!-- JournalNode Security Configuration --> <property> <name>dfs.journalnode.keytab.file</name> <value>keytabs/hdfs.journal-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.journalnode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/journal-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.journalnode.kerberos.internal.spnego.principal</name> <value>{{KERBEROS_PRIMARY_HTTP}}/journal-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> {{/JOURNALNODE}} {{/SECURE_MODE}} </configuration> "
			}, {
				name: "hadoop-metrics2",
				relative - path: "hadoop-2.6.0-cdh5.9.1/etc/hadoop/hadoop-metrics2.properties",
				template - content: "# Autogenerated by the Mesos Framework, DO NOT EDIT *.sink.statsd.class=org.apache.hadoop.metrics2.sink.StatsDSink namenode.sink.statsd.period=10 namenode.sink.statsd.server.host={{STATSD_UDP_HOST}} namenode.sink.statsd.server.port={{STATSD_UDP_PORT}} namenode.sink.statsd.skip.hostname=false"
			}]
		}, {
			name: "format",
			goal: "FINISHED",
			resource - set: {
				id: "name-resources",
				resource - specifications: [{
					@type: "DefaultResourceSpec",
					name: "cpus",
					value: {
						type: "SCALAR",
						scalar: {
							value: 0.3
						},
						ranges: null,
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					envKey: null
				}, {
					@type: "DefaultResourceSpec",
					name: "mem",
					value: {
						type: "SCALAR",
						scalar: {
							value: 512
						},
						ranges: null,
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					envKey: null
				}, {
					@type: "PortsSpec",
					name: "ports",
					value: {
						type: "RANGES",
						scalar: null,
						ranges: {
							range: [{
								begin: 9001,
								end: 9001
							}, {
								begin: 9002,
								end: 9002
							}]
						},
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					port - specs: [{
						@type: "PortSpec",
						name: "ports",
						value: {
							type: "RANGES",
							scalar: null,
							ranges: {
								range: [{
									begin: 9001,
									end: 9001
								}]
							},
							set: null,
							text: null
						},
						role: "hdfs-role",
						principal: "hdfs-principal",
						port - name: "name-rpc",
						envKey: null
					}, {
						@type: "PortSpec",
						name: "ports",
						value: {
							type: "RANGES",
							scalar: null,
							ranges: {
								range: [{
									begin: 9002,
									end: 9002
								}]
							},
							set: null,
							text: null
						},
						role: "hdfs-role",
						principal: "hdfs-principal",
						port - name: "name-http",
						envKey: null
					}],
					envKey: null
				}],
				volume - specifications: [{
					@type: "DefaultVolumeSpec",
					type: "ROOT",
					container - path: "name-data",
					name: "disk",
					value: {
						type: "SCALAR",
						scalar: {
							value: 5000
						},
						ranges: null,
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					envKey: "DISK_SIZE"
				}],
				role: "hdfs-role",
				principal: "hdfs-principal"
			},
			command - spec: {
				value: "./bootstrap && ./hadoop-2.6.0-cdh5.9.1/bin/hdfs namenode -format",
				environment: {
					CLIENT_FAILOVER_PROXY_PROVIDER_HDFS: "org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider",
					CLIENT_READ_SHORTCIRCUIT: "true",
					CLIENT_READ_SHORTCIRCUIT_PATH: "/var/lib/hadoop-hdfs/dn_socket",
					CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE: "1000",
					CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS: "1000",
					DATA_NODE_BALANCE_BANDWIDTH_PER_SEC: "41943040",
					DATA_NODE_HANDLER_COUNT: "10",
					DATA_NODE_HTTP_PORT: "9004",
					DATA_NODE_IPC_PORT: "9005",
					DATA_NODE_RPC_PORT: "9003",
					FRAMEWORK_NAME: "",
					HADOOP_PROXYUSER_HTTPFS_GROUPS: "*",
					HADOOP_PROXYUSER_HTTPFS_HOSTS: "*",
					HADOOP_PROXYUSER_HUE_GROUPS: "*",
					HADOOP_PROXYUSER_HUE_HOSTS: "*",
					HADOOP_PROXYUSER_ROOT_GROUPS: "*",
					HADOOP_PROXYUSER_ROOT_HOSTS: "*",
					HADOOP_ROOT_LOGGER: "INFO,console",
					HA_AUTOMATIC_FAILURE: "true",
					HA_FENCING_METHODS: "shell(/bin/true)",
					IMAGE_COMPRESS: "true",
					IMAGE_COMPRESSION_CODEC: "org.apache.hadoop.io.compress.SnappyCodec",
					JOURNAL_NODE_HTTP_PORT: "8480",
					JOURNAL_NODE_RPC_PORT: "8485",
					NAMENODE: "true",
					NAME_NODE_DATA_NODE_REGISTRATION_IP_HOSTNAME_CHECK: "false",
					NAME_NODE_HANDLER_COUNT: "20",
					NAME_NODE_HEARTBEAT_RECHECK_INTERVAL: "60000",
					NAME_NODE_HTTP_PORT: "9002",
					NAME_NODE_INVALIDATE_WORK_PCT_PER_ITERATION: "0.95",
					NAME_NODE_REPLICATION_WORK_MULTIPLIER_PER_ITERATION: "4",
					NAME_NODE_RPC_PORT: "9001",
					NAME_NODE_SAFEMODE_THRESHOLD_PCT: "0.9",
					PERMISSIONS_ENABLED: "false",
					SERVICE_ZK_ROOT: "dcos-service-hdfs",
					TASK_USER: "root"
				}
			},
			health - check - spec: null,
			readiness - check - spec: null,
			config - files: [{
				name: "core-site",
				relative - path: "hadoop-2.6.0-cdh5.9.1/etc/hadoop/core-site.xml",
				template - content: "<?xml version="
				1.0 " encoding="
				UTF - 8 " standalone="
				no "?> <?xml-stylesheet type="
				text / xsl " href="
				configuration.xsl "?><configuration> <property> <name>fs.default.name</name> <value>hdfs://hdfs</value> </property> <property> <name>hadoop.proxyuser.hue.hosts</name> <value>{{HADOOP_PROXYUSER_HUE_HOSTS}}</value> </property> <property> <name>hadoop.proxyuser.hue.groups</name> <value>{{HADOOP_PROXYUSER_HUE_GROUPS}}</value> </property> <property> <name>hadoop.proxyuser.root.hosts</name> <value>{{HADOOP_PROXYUSER_ROOT_HOSTS}}</value> </property> <property> <name>hadoop.proxyuser.root.groups</name> <value>{{HADOOP_PROXYUSER_ROOT_GROUPS}}</value> </property> <property> <name>hadoop.proxyuser.httpfs.groups</name> <value>{{HADOOP_PROXYUSER_HTTPFS_GROUPS}}</value> </property> <property> <name>hadoop.proxyuser.httpfs.hosts</name> <value>{{HADOOP_PROXYUSER_HTTPFS_HOSTS}}</value> </property> <property> <name>ha.zookeeper.parent-znode</name> <value>/{{SERVICE_ZK_ROOT}}/hadoop-ha</value> </property> {{#SECURE_MODE}} <property> <!-- The ZKFC nodes use this property to verify they are connecting to the namenode with the expected principal. --> <name>hadoop.security.service.user.name.key.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>hadoop.security.authentication</name> <value>kerberos</value> </property> <property> <name>hadoop.security.authorization</name> <value>true</value> </property> {{/SECURE_MODE}} </configuration> "
			}, {
				name: "hdfs-site",
				relative - path: "hadoop-2.6.0-cdh5.9.1/etc/hadoop/hdfs-site.xml",
				template - content: "<?xml version="
				1.0 " encoding="
				UTF - 8 " standalone="
				no "?> <?xml-stylesheet type="
				text / xsl " href="
				configuration.xsl "?> <configuration> <property> <name>dfs.nameservice.id</name> <value>hdfs</value> </property> <property> <name>dfs.nameservices</name> <value>hdfs</value> </property> <property> <name>dfs.ha.namenodes.hdfs</name> <value>name-0-node,name-1-node</value> </property> <!-- namenode --> <property> <name>dfs.namenode.shared.edits.dir</name> <value>qjournal://journal-0-node.{{FRAMEWORK_NAME}}.mesos:{{JOURNAL_NODE_RPC_PORT}};journal-1-node.{{FRAMEWORK_NAME}}.mesos:{{JOURNAL_NODE_RPC_PORT}};journal-2-node.{{FRAMEWORK_NAME}}.mesos:{{JOURNAL_NODE_RPC_PORT}}/hdfs</value> </property> <property> <name>dfs.namenode.name.dir</name> <value>{{MESOS_SANDBOX}}/name-data</value> </property> <property> <name>dfs.namenode.safemode.threshold-pct</name> <value>{{NAME_NODE_SAFEMODE_THRESHOLD_PCT}}</value> </property> <property> <name>dfs.namenode.heartbeat.recheck-interval</name> <value>{{NAME_NODE_HEARTBEAT_RECHECK_INTERVAL}}</value> </property> <property> <name>dfs.namenode.handler.count</name> <value>{{NAME_NODE_HANDLER_COUNT}}</value> </property> <property> <name>dfs.namenode.invalidate.work.pct.per.iteration</name> <value>{{NAME_NODE_INVALIDATE_WORK_PCT_PER_ITERATION}}</value> </property> <property> <name>dfs.namenode.replication.work.multiplier.per.iteration</name> <value>{{NAME_NODE_REPLICATION_WORK_MULTIPLIER_PER_ITERATION}}</value> </property> <property> <name>dfs.namenode.datanode.registration.ip-hostname-check</name> <value>{{NAME_NODE_DATA_NODE_REGISTRATION_IP_HOSTNAME_CHECK}}</value> </property> <!-- name-0-node --> <property> <name>dfs.namenode.rpc-address.hdfs.name-0-node</name> <value>name-0-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.namenode.rpc-bind-host.hdfs.name-0-node</name> <value>0.0.0.0</value> </property> <property> <name>dfs.namenode.http-address.hdfs.name-0-node</name> <value>name-0-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.namenode.http-bind-host.hdfs.name-0-node</name> <value>0.0.0.0</value> </property> <!-- name-1-node --> <property> <name>dfs.namenode.rpc-address.hdfs.name-1-node</name> <value>name-1-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.namenode.rpc-bind-host.hdfs.name-1-node</name> <value>0.0.0.0</value> </property> <property> <name>dfs.namenode.http-address.hdfs.name-1-node</name> <value>name-1-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.namenode.http-bind-host.hdfs.name-1-node</name> <value>0.0.0.0</value> </property> <!-- journalnode --> <property> <name>dfs.journalnode.rpc-address</name> <value>0.0.0.0:{{JOURNAL_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.journalnode.http-address</name> <value>0.0.0.0:{{JOURNAL_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.journalnode.edits.dir</name> <value>{{MESOS_SANDBOX}}/journal-data</value> </property> <!-- datanode --> <property> <name>dfs.datanode.address</name> <value>0.0.0.0:{{DATA_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.datanode.http.address</name> <value>0.0.0.0:{{DATA_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.datanode.ipc.address</name> <value>0.0.0.0:{{DATA_NODE_IPC_PORT}}</value> </property> <property> <name>dfs.datanode.data.dir</name> <value>{{MESOS_SANDBOX}}/data-data</value> </property> <property> <name>dfs.datanode.balance.bandwidthPerSec</name> <value>41943040</value> </property> <property> <name>dfs.datanode.handler.count</name> <value>{{DATA_NODE_HANDLER_COUNT}}</value> </property> <!-- HA --> <property> <name>ha.zookeeper.quorum</name> <value>master.mesos:2181</value> </property> <property> <name>dfs.ha.fencing.methods</name> <value>{{HA_FENCING_METHODS}}</value> </property> <property> <name>dfs.ha.automatic-failover.enabled</name> <value>{{HA_AUTOMATIC_FAILURE}}</value> </property> {{#NAMENODE}} <property> <name>dfs.ha.namenode.id</name> <value>name-{{POD_INSTANCE_INDEX}}-node</value> </property> {{/NAMENODE}} <property> <name>dfs.image.compress</name> <value>{{IMAGE_COMPRESS}}</value> </property> <property> <name>dfs.image.compression.codec</name> <value>{{IMAGE_COMPRESSION_CODEC}}</value> </property> <property> <name>dfs.client.read.shortcircuit</name> <value>{{CLIENT_READ_SHORTCIRCUIT}}</value> </property> <property> <name>dfs.client.read.shortcircuit.streams.cache.size</name> <value>{{CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE}}</value> </property> <property> <name>dfs.client.read.shortcircuit.streams.cache.size.expiry.ms</name> <value>{{CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS}}</value> </property> <property> <name>dfs.client.failover.proxy.provider.hdfs</name> <value>{{CLIENT_FAILOVER_PROXY_PROVIDER_HDFS}}</value> </property> <property> <name>dfs.domain.socket.path</name> <value>{{CLIENT_READ_SHORTCIRCUIT_PATH}}</value> </property> <property> <name>dfs.permissions.enabled</name> <value>{{PERMISSIONS_ENABLED}}</value> </property> {{#SECURE_MODE}} <property> <name>ignore.secure.ports.for.testing</name> <value>true</value> </property> <!-- Security Configuration --> <property> <name>hadoop.security.auth_to_local</name> <value> RULE:[2:$1@$0](.*)s/.*/{{TASK_USER}}/ RULE:[1:$1@$0](.*)s/.*/{{TASK_USER}}/ </value> </property> <property> <name>dfs.block.access.token.enable</name> <value>true</value> </property> <property> <name>dfs.namenode.kerberos.principal.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.datanode.kerberos.principal.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.journalnode.kerberos.principal.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.cluster.administrators</name> <value>core,root,hdfs,nobody</value> </property> <property> <name>dfs.web.authentication.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/{{TASK_NAME}}.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.web.authentication.kerberos.keytab</name> <value>keytabs/{{KERBEROS_PRIMARY}}.{{TASK_NAME}}.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> {{#DATANODE}} <!-- DataNode Security Configuration --> <property> <name>dfs.datanode.keytab.file</name> <value>keytabs/{{KERBEROS_PRIMARY}}.data-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.datanode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/data-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.datanode.data.dir.perm</name> <value>700</value> </property> {{/DATANODE}} {{#NAMENODE}} <!-- NameNode Security Configuration --> <property> <name>dfs.namenode.keytab.file</name> <value>keytabs/{{KERBEROS_PRIMARY}}.name-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.namenode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/name-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.namenode.kerberos.internal.spnego.principal</name> <value>{{KERBEROS_PRIMARY_HTTP}}/name-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> {{/NAMENODE}} {{#ZKFC}} <!-- NameNode Security Configuration --> <property> <name>dfs.namenode.keytab.file</name> <value>keytabs/{{KERBEROS_PRIMARY}}.zkfc-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.namenode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/zkfc-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.namenode.kerberos.internal.spnego.principal</name> <value>{{KERBEROS_PRIMARY_HTTP}}/zkfc-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> {{/ZKFC}} {{#JOURNALNODE}} <!-- JournalNode Security Configuration --> <property> <name>dfs.journalnode.keytab.file</name> <value>keytabs/hdfs.journal-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.journalnode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/journal-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.journalnode.kerberos.internal.spnego.principal</name> <value>{{KERBEROS_PRIMARY_HTTP}}/journal-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> {{/JOURNALNODE}} {{/SECURE_MODE}} </configuration> "
			}]
		}, {
			name: "bootstrap",
			goal: "FINISHED",
			resource - set: {
				id: "name-resources",
				resource - specifications: [{
					@type: "DefaultResourceSpec",
					name: "cpus",
					value: {
						type: "SCALAR",
						scalar: {
							value: 0.3
						},
						ranges: null,
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					envKey: null
				}, {
					@type: "DefaultResourceSpec",
					name: "mem",
					value: {
						type: "SCALAR",
						scalar: {
							value: 512
						},
						ranges: null,
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					envKey: null
				}, {
					@type: "PortsSpec",
					name: "ports",
					value: {
						type: "RANGES",
						scalar: null,
						ranges: {
							range: [{
								begin: 9001,
								end: 9001
							}, {
								begin: 9002,
								end: 9002
							}]
						},
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					port - specs: [{
						@type: "PortSpec",
						name: "ports",
						value: {
							type: "RANGES",
							scalar: null,
							ranges: {
								range: [{
									begin: 9001,
									end: 9001
								}]
							},
							set: null,
							text: null
						},
						role: "hdfs-role",
						principal: "hdfs-principal",
						port - name: "name-rpc",
						envKey: null
					}, {
						@type: "PortSpec",
						name: "ports",
						value: {
							type: "RANGES",
							scalar: null,
							ranges: {
								range: [{
									begin: 9002,
									end: 9002
								}]
							},
							set: null,
							text: null
						},
						role: "hdfs-role",
						principal: "hdfs-principal",
						port - name: "name-http",
						envKey: null
					}],
					envKey: null
				}],
				volume - specifications: [{
					@type: "DefaultVolumeSpec",
					type: "ROOT",
					container - path: "name-data",
					name: "disk",
					value: {
						type: "SCALAR",
						scalar: {
							value: 5000
						},
						ranges: null,
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					envKey: "DISK_SIZE"
				}],
				role: "hdfs-role",
				principal: "hdfs-principal"
			},
			command - spec: {
				value: "./bootstrap && ./hadoop-2.6.0-cdh5.9.1/bin/hdfs namenode -bootstrapStandby",
				environment: {
					CLIENT_FAILOVER_PROXY_PROVIDER_HDFS: "org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider",
					CLIENT_READ_SHORTCIRCUIT: "true",
					CLIENT_READ_SHORTCIRCUIT_PATH: "/var/lib/hadoop-hdfs/dn_socket",
					CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE: "1000",
					CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS: "1000",
					DATA_NODE_BALANCE_BANDWIDTH_PER_SEC: "41943040",
					DATA_NODE_HANDLER_COUNT: "10",
					DATA_NODE_HTTP_PORT: "9004",
					DATA_NODE_IPC_PORT: "9005",
					DATA_NODE_RPC_PORT: "9003",
					FRAMEWORK_NAME: "",
					HADOOP_PROXYUSER_HTTPFS_GROUPS: "*",
					HADOOP_PROXYUSER_HTTPFS_HOSTS: "*",
					HADOOP_PROXYUSER_HUE_GROUPS: "*",
					HADOOP_PROXYUSER_HUE_HOSTS: "*",
					HADOOP_PROXYUSER_ROOT_GROUPS: "*",
					HADOOP_PROXYUSER_ROOT_HOSTS: "*",
					HADOOP_ROOT_LOGGER: "INFO,console",
					HA_AUTOMATIC_FAILURE: "true",
					HA_FENCING_METHODS: "shell(/bin/true)",
					IMAGE_COMPRESS: "true",
					IMAGE_COMPRESSION_CODEC: "org.apache.hadoop.io.compress.SnappyCodec",
					JOURNAL_NODE_HTTP_PORT: "8480",
					JOURNAL_NODE_RPC_PORT: "8485",
					NAMENODE: "true",
					NAME_NODE_DATA_NODE_REGISTRATION_IP_HOSTNAME_CHECK: "false",
					NAME_NODE_HANDLER_COUNT: "20",
					NAME_NODE_HEARTBEAT_RECHECK_INTERVAL: "60000",
					NAME_NODE_HTTP_PORT: "9002",
					NAME_NODE_INVALIDATE_WORK_PCT_PER_ITERATION: "0.95",
					NAME_NODE_REPLICATION_WORK_MULTIPLIER_PER_ITERATION: "4",
					NAME_NODE_RPC_PORT: "9001",
					NAME_NODE_SAFEMODE_THRESHOLD_PCT: "0.9",
					PERMISSIONS_ENABLED: "false",
					SERVICE_ZK_ROOT: "dcos-service-hdfs",
					TASK_USER: "root"
				}
			},
			health - check - spec: null,
			readiness - check - spec: null,
			config - files: [{
				name: "core-site",
				relative - path: "hadoop-2.6.0-cdh5.9.1/etc/hadoop/core-site.xml",
				template - content: "<?xml version="
				1.0 " encoding="
				UTF - 8 " standalone="
				no "?> <?xml-stylesheet type="
				text / xsl " href="
				configuration.xsl "?><configuration> <property> <name>fs.default.name</name> <value>hdfs://hdfs</value> </property> <property> <name>hadoop.proxyuser.hue.hosts</name> <value>{{HADOOP_PROXYUSER_HUE_HOSTS}}</value> </property> <property> <name>hadoop.proxyuser.hue.groups</name> <value>{{HADOOP_PROXYUSER_HUE_GROUPS}}</value> </property> <property> <name>hadoop.proxyuser.root.hosts</name> <value>{{HADOOP_PROXYUSER_ROOT_HOSTS}}</value> </property> <property> <name>hadoop.proxyuser.root.groups</name> <value>{{HADOOP_PROXYUSER_ROOT_GROUPS}}</value> </property> <property> <name>hadoop.proxyuser.httpfs.groups</name> <value>{{HADOOP_PROXYUSER_HTTPFS_GROUPS}}</value> </property> <property> <name>hadoop.proxyuser.httpfs.hosts</name> <value>{{HADOOP_PROXYUSER_HTTPFS_HOSTS}}</value> </property> <property> <name>ha.zookeeper.parent-znode</name> <value>/{{SERVICE_ZK_ROOT}}/hadoop-ha</value> </property> {{#SECURE_MODE}} <property> <!-- The ZKFC nodes use this property to verify they are connecting to the namenode with the expected principal. --> <name>hadoop.security.service.user.name.key.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>hadoop.security.authentication</name> <value>kerberos</value> </property> <property> <name>hadoop.security.authorization</name> <value>true</value> </property> {{/SECURE_MODE}} </configuration> "
			}, {
				name: "hdfs-bootstrap-site",
				relative - path: "hadoop-2.6.0-cdh5.9.1/etc/hadoop/hdfs-site.xml",
				template - content: "<?xml version="
				1.0 " encoding="
				UTF - 8 " standalone="
				no "?> <?xml-stylesheet type="
				text / xsl " href="
				configuration.xsl "?> <configuration> <property> <name>dfs.nameservice.id</name> <value>hdfs</value> </property> <property> <name>dfs.nameservices</name> <value>hdfs</value> </property> <property> <name>dfs.ha.namenodes.hdfs</name> <value>name-0-node,name-1-node</value> </property> <!-- namenode --> <property> <name>dfs.namenode.shared.edits.dir</name> <value>qjournal://journal-0-node.{{FRAMEWORK_NAME}}.mesos:{{JOURNAL_NODE_RPC_PORT}};journal-1-node.{{FRAMEWORK_NAME}}.mesos:{{JOURNAL_NODE_RPC_PORT}};journal-2-node.{{FRAMEWORK_NAME}}.mesos:{{JOURNAL_NODE_RPC_PORT}}/hdfs</value> </property> <property> <name>dfs.namenode.name.dir</name> <value>{{MESOS_SANDBOX}}/name-data</value> </property> <property> <name>dfs.namenode.safemode.threshold-pct</name> <value>{{NAME_NODE_SAFEMODE_THRESHOLD_PCT}}</value> </property> <property> <name>dfs.namenode.heartbeat.recheck-interval</name> <value>{{NAME_NODE_HEARTBEAT_RECHECK_INTERVAL}}</value> </property> <property> <name>dfs.namenode.handler.count</name> <value>{{NAME_NODE_HANDLER_COUNT}}</value> </property> <property> <name>dfs.namenode.invalidate.work.pct.per.iteration</name> <value>{{NAME_NODE_INVALIDATE_WORK_PCT_PER_ITERATION}}</value> </property> <property> <name>dfs.namenode.replication.work.multiplier.per.iteration</name> <value>{{NAME_NODE_REPLICATION_WORK_MULTIPLIER_PER_ITERATION}}</value> </property> <property> <name>dfs.namenode.datanode.registration.ip-hostname-check</name> <value>{{NAME_NODE_DATA_NODE_REGISTRATION_IP_HOSTNAME_CHECK}}</value> </property> <!-- name-0-node --> <property> <name>dfs.namenode.rpc-address.hdfs.name-0-node</name> <value>name-0-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.namenode.rpc-bind-host.hdfs.name-0-node</name> <value>0.0.0.0</value> </property> <property> <name>dfs.namenode.http-address.hdfs.name-0-node</name> <value>name-0-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.namenode.http-bind-host.hdfs.name-0-node</name> <value>0.0.0.0</value> </property> <!-- name-1-node --> <property> <name>dfs.namenode.rpc-address.hdfs.name-1-node</name> <value>name-1-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.namenode.rpc-bind-host.hdfs.name-1-node</name> <value>0.0.0.0</value> </property> <property> <name>dfs.namenode.http-address.hdfs.name-1-node</name> <value>name-1-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.namenode.http-bind-host.hdfs.name-1-node</name> <value>0.0.0.0</value> </property> <!-- journalnode --> <property> <name>dfs.journalnode.rpc-address</name> <value>0.0.0.0:{{JOURNAL_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.journalnode.http-address</name> <value>0.0.0.0:{{JOURNAL_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.journalnode.edits.dir</name> <value>{{MESOS_SANDBOX}}/journal-data</value> </property> <!-- datanode --> <property> <name>dfs.datanode.address</name> <value>0.0.0.0:{{DATA_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.datanode.http.address</name> <value>0.0.0.0:{{DATA_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.datanode.ipc.address</name> <value>0.0.0.0:{{DATA_NODE_IPC_PORT}}</value> </property> <property> <name>dfs.datanode.data.dir</name> <value>{{MESOS_SANDBOX}}/data-data</value> </property> <property> <name>dfs.datanode.balance.bandwidthPerSec</name> <value>41943040</value> </property> <property> <name>dfs.datanode.handler.count</name> <value>{{DATA_NODE_HANDLER_COUNT}}</value> </property> <!-- HA --> <property> <name>ha.zookeeper.quorum</name> <value>master.mesos:2181</value> </property> <property> <name>dfs.ha.fencing.methods</name> <value>{{HA_FENCING_METHODS}}</value> </property> <property> <name>dfs.ha.automatic-failover.enabled</name> <value>{{HA_AUTOMATIC_FAILURE}}</value> </property> {{#NAMENODE}} <property> <name>dfs.ha.namenode.id</name> <value>name-{{POD_INSTANCE_INDEX}}-node</value> </property> {{/NAMENODE}} <property> <name>dfs.image.compress</name> <value>{{IMAGE_COMPRESS}}</value> </property> <property> <name>dfs.image.compression.codec</name> <value>{{IMAGE_COMPRESSION_CODEC}}</value> </property> <property> <name>dfs.client.read.shortcircuit</name> <value>{{CLIENT_READ_SHORTCIRCUIT}}</value> </property> <property> <name>dfs.client.read.shortcircuit.streams.cache.size</name> <value>{{CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE}}</value> </property> <property> <name>dfs.client.read.shortcircuit.streams.cache.size.expiry.ms</name> <value>{{CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS}}</value> </property> <property> <name>dfs.client.failover.proxy.provider.hdfs</name> <value>{{CLIENT_FAILOVER_PROXY_PROVIDER_HDFS}}</value> </property> <property> <name>dfs.domain.socket.path</name> <value>{{CLIENT_READ_SHORTCIRCUIT_PATH}}</value> </property> <property> <name>dfs.permissions.enabled</name> <value>{{PERMISSIONS_ENABLED}}</value> </property> {{#SECURE_MODE}} <property> <name>ignore.secure.ports.for.testing</name> <value>true</value> </property> <!-- Security Configuration --> <property> <name>hadoop.security.auth_to_local</name> <value> RULE:[2:$1@$0](.*)s/.*/{{TASK_USER}}/ RULE:[1:$1@$0](.*)s/.*/{{TASK_USER}}/ </value> </property> <property> <name>dfs.block.access.token.enable</name> <value>true</value> </property> <property> <name>dfs.namenode.kerberos.principal.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.datanode.kerberos.principal.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.journalnode.kerberos.principal.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.cluster.administrators</name> <value>core,root,hdfs,nobody</value> </property> <property> <name>dfs.web.authentication.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/{{TASK_NAME}}.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.web.authentication.kerberos.keytab</name> <value>keytabs/{{KERBEROS_PRIMARY}}.{{TASK_NAME}}.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> {{#DATANODE}} <!-- DataNode Security Configuration --> <property> <name>dfs.datanode.keytab.file</name> <value>keytabs/{{KERBEROS_PRIMARY}}.data-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.datanode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/data-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.datanode.data.dir.perm</name> <value>700</value> </property> {{/DATANODE}} {{#NAMENODE}} <!-- NameNode Security Configuration --> <property> <name>dfs.namenode.keytab.file</name> <value>keytabs/{{KERBEROS_PRIMARY}}.name-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.namenode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/name-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.namenode.kerberos.internal.spnego.principal</name> <value>{{KERBEROS_PRIMARY_HTTP}}/name-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> {{/NAMENODE}} {{#ZKFC}} <!-- NameNode Security Configuration --> <property> <name>dfs.namenode.keytab.file</name> <value>keytabs/{{KERBEROS_PRIMARY}}.zkfc-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.namenode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/zkfc-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.namenode.kerberos.internal.spnego.principal</name> <value>{{KERBEROS_PRIMARY_HTTP}}/zkfc-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> {{/ZKFC}} {{#JOURNALNODE}} <!-- JournalNode Security Configuration --> <property> <name>dfs.journalnode.keytab.file</name> <value>keytabs/hdfs.journal-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.journalnode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/journal-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.journalnode.kerberos.internal.spnego.principal</name> <value>{{KERBEROS_PRIMARY_HTTP}}/journal-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> {{/JOURNALNODE}} {{/SECURE_MODE}} </configuration> "
			}]
		}],
		placement - rule: {
			@type: "AndRule",
			rules: [{
				@type: "TaskTypeRule",
				type: "name",
				converter: {
					@type: "TaskTypeLabelConverter"
				},
				behavior: "AVOID"
			}, {
				@type: "TaskTypeRule",
				type: "journal",
				converter: {
					@type: "TaskTypeLabelConverter"
				},
				behavior: "AVOID"
			}]
		}
	}, {
		type: "zkfc",
		user: null,
		count: 2,
		container: null,
		uris: [
			"https://downloads.mesosphere.com/hdfs/assets/hadoop-2.6.0-cdh5.9.1-dcos.tar.gz",
			"https://downloads.mesosphere.com/hdfs/assets/1.0.0-2.6.0/bootstrap.zip"
		],
		task - specs: [{
			name: "node",
			goal: "RUNNING",
			resource - set: {
				id: "zkfc-resources",
				resource - specifications: [{
					@type: "DefaultResourceSpec",
					name: "cpus",
					value: {
						type: "SCALAR",
						scalar: {
							value: 0.3
						},
						ranges: null,
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					envKey: null
				}, {
					@type: "DefaultResourceSpec",
					name: "mem",
					value: {
						type: "SCALAR",
						scalar: {
							value: 512
						},
						ranges: null,
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					envKey: null
				}],
				volume - specifications: [],
				role: "hdfs-role",
				principal: "hdfs-principal"
			},
			command - spec: {
				value: "./bootstrap && ./hadoop-2.6.0-cdh5.9.1/bin/hdfs zkfc",
				environment: {
					CLIENT_FAILOVER_PROXY_PROVIDER_HDFS: "org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider",
					CLIENT_READ_SHORTCIRCUIT: "true",
					CLIENT_READ_SHORTCIRCUIT_PATH: "/var/lib/hadoop-hdfs/dn_socket",
					CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE: "1000",
					CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS: "1000",
					DATA_NODE_BALANCE_BANDWIDTH_PER_SEC: "41943040",
					DATA_NODE_HANDLER_COUNT: "10",
					DATA_NODE_HTTP_PORT: "9004",
					DATA_NODE_IPC_PORT: "9005",
					DATA_NODE_RPC_PORT: "9003",
					HADOOP_PROXYUSER_HTTPFS_GROUPS: "*",
					HADOOP_PROXYUSER_HTTPFS_HOSTS: "*",
					HADOOP_PROXYUSER_HUE_GROUPS: "*",
					HADOOP_PROXYUSER_HUE_HOSTS: "*",
					HADOOP_PROXYUSER_ROOT_GROUPS: "*",
					HADOOP_PROXYUSER_ROOT_HOSTS: "*",
					HADOOP_ROOT_LOGGER: "INFO,console",
					HA_AUTOMATIC_FAILURE: "true",
					HA_FENCING_METHODS: "shell(/bin/true)",
					IMAGE_COMPRESS: "true",
					IMAGE_COMPRESSION_CODEC: "org.apache.hadoop.io.compress.SnappyCodec",
					JOURNAL_NODE_HTTP_PORT: "8480",
					JOURNAL_NODE_RPC_PORT: "8485",
					NAME_NODE_DATA_NODE_REGISTRATION_IP_HOSTNAME_CHECK: "false",
					NAME_NODE_HANDLER_COUNT: "20",
					NAME_NODE_HEARTBEAT_RECHECK_INTERVAL: "60000",
					NAME_NODE_HTTP_PORT: "9002",
					NAME_NODE_INVALIDATE_WORK_PCT_PER_ITERATION: "0.95",
					NAME_NODE_REPLICATION_WORK_MULTIPLIER_PER_ITERATION: "4",
					NAME_NODE_RPC_PORT: "9001",
					NAME_NODE_SAFEMODE_THRESHOLD_PCT: "0.9",
					PERMISSIONS_ENABLED: "false",
					SERVICE_ZK_ROOT: "dcos-service-hdfs",
					TASK_USER: "root",
					ZKFC: "true"
				}
			},
			health - check - spec: null,
			readiness - check - spec: null,
			config - files: [{
				name: "core-site",
				relative - path: "hadoop-2.6.0-cdh5.9.1/etc/hadoop/core-site.xml",
				template - content: "<?xml version="
				1.0 " encoding="
				UTF - 8 " standalone="
				no "?> <?xml-stylesheet type="
				text / xsl " href="
				configuration.xsl "?><configuration> <property> <name>fs.default.name</name> <value>hdfs://hdfs</value> </property> <property> <name>hadoop.proxyuser.hue.hosts</name> <value>{{HADOOP_PROXYUSER_HUE_HOSTS}}</value> </property> <property> <name>hadoop.proxyuser.hue.groups</name> <value>{{HADOOP_PROXYUSER_HUE_GROUPS}}</value> </property> <property> <name>hadoop.proxyuser.root.hosts</name> <value>{{HADOOP_PROXYUSER_ROOT_HOSTS}}</value> </property> <property> <name>hadoop.proxyuser.root.groups</name> <value>{{HADOOP_PROXYUSER_ROOT_GROUPS}}</value> </property> <property> <name>hadoop.proxyuser.httpfs.groups</name> <value>{{HADOOP_PROXYUSER_HTTPFS_GROUPS}}</value> </property> <property> <name>hadoop.proxyuser.httpfs.hosts</name> <value>{{HADOOP_PROXYUSER_HTTPFS_HOSTS}}</value> </property> <property> <name>ha.zookeeper.parent-znode</name> <value>/{{SERVICE_ZK_ROOT}}/hadoop-ha</value> </property> {{#SECURE_MODE}} <property> <!-- The ZKFC nodes use this property to verify they are connecting to the namenode with the expected principal. --> <name>hadoop.security.service.user.name.key.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>hadoop.security.authentication</name> <value>kerberos</value> </property> <property> <name>hadoop.security.authorization</name> <value>true</value> </property> {{/SECURE_MODE}} </configuration> "
			}, {
				name: "hdfs-site",
				relative - path: "hadoop-2.6.0-cdh5.9.1/etc/hadoop/hdfs-site.xml",
				template - content: "<?xml version="
				1.0 " encoding="
				UTF - 8 " standalone="
				no "?> <?xml-stylesheet type="
				text / xsl " href="
				configuration.xsl "?> <configuration> <property> <name>dfs.nameservice.id</name> <value>hdfs</value> </property> <property> <name>dfs.nameservices</name> <value>hdfs</value> </property> <property> <name>dfs.ha.namenodes.hdfs</name> <value>name-0-node,name-1-node</value> </property> <!-- namenode --> <property> <name>dfs.namenode.shared.edits.dir</name> <value>qjournal://journal-0-node.{{FRAMEWORK_NAME}}.mesos:{{JOURNAL_NODE_RPC_PORT}};journal-1-node.{{FRAMEWORK_NAME}}.mesos:{{JOURNAL_NODE_RPC_PORT}};journal-2-node.{{FRAMEWORK_NAME}}.mesos:{{JOURNAL_NODE_RPC_PORT}}/hdfs</value> </property> <property> <name>dfs.namenode.name.dir</name> <value>{{MESOS_SANDBOX}}/name-data</value> </property> <property> <name>dfs.namenode.safemode.threshold-pct</name> <value>{{NAME_NODE_SAFEMODE_THRESHOLD_PCT}}</value> </property> <property> <name>dfs.namenode.heartbeat.recheck-interval</name> <value>{{NAME_NODE_HEARTBEAT_RECHECK_INTERVAL}}</value> </property> <property> <name>dfs.namenode.handler.count</name> <value>{{NAME_NODE_HANDLER_COUNT}}</value> </property> <property> <name>dfs.namenode.invalidate.work.pct.per.iteration</name> <value>{{NAME_NODE_INVALIDATE_WORK_PCT_PER_ITERATION}}</value> </property> <property> <name>dfs.namenode.replication.work.multiplier.per.iteration</name> <value>{{NAME_NODE_REPLICATION_WORK_MULTIPLIER_PER_ITERATION}}</value> </property> <property> <name>dfs.namenode.datanode.registration.ip-hostname-check</name> <value>{{NAME_NODE_DATA_NODE_REGISTRATION_IP_HOSTNAME_CHECK}}</value> </property> <!-- name-0-node --> <property> <name>dfs.namenode.rpc-address.hdfs.name-0-node</name> <value>name-0-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.namenode.rpc-bind-host.hdfs.name-0-node</name> <value>0.0.0.0</value> </property> <property> <name>dfs.namenode.http-address.hdfs.name-0-node</name> <value>name-0-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.namenode.http-bind-host.hdfs.name-0-node</name> <value>0.0.0.0</value> </property> <!-- name-1-node --> <property> <name>dfs.namenode.rpc-address.hdfs.name-1-node</name> <value>name-1-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.namenode.rpc-bind-host.hdfs.name-1-node</name> <value>0.0.0.0</value> </property> <property> <name>dfs.namenode.http-address.hdfs.name-1-node</name> <value>name-1-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.namenode.http-bind-host.hdfs.name-1-node</name> <value>0.0.0.0</value> </property> <!-- journalnode --> <property> <name>dfs.journalnode.rpc-address</name> <value>0.0.0.0:{{JOURNAL_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.journalnode.http-address</name> <value>0.0.0.0:{{JOURNAL_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.journalnode.edits.dir</name> <value>{{MESOS_SANDBOX}}/journal-data</value> </property> <!-- datanode --> <property> <name>dfs.datanode.address</name> <value>0.0.0.0:{{DATA_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.datanode.http.address</name> <value>0.0.0.0:{{DATA_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.datanode.ipc.address</name> <value>0.0.0.0:{{DATA_NODE_IPC_PORT}}</value> </property> <property> <name>dfs.datanode.data.dir</name> <value>{{MESOS_SANDBOX}}/data-data</value> </property> <property> <name>dfs.datanode.balance.bandwidthPerSec</name> <value>41943040</value> </property> <property> <name>dfs.datanode.handler.count</name> <value>{{DATA_NODE_HANDLER_COUNT}}</value> </property> <!-- HA --> <property> <name>ha.zookeeper.quorum</name> <value>master.mesos:2181</value> </property> <property> <name>dfs.ha.fencing.methods</name> <value>{{HA_FENCING_METHODS}}</value> </property> <property> <name>dfs.ha.automatic-failover.enabled</name> <value>{{HA_AUTOMATIC_FAILURE}}</value> </property> {{#NAMENODE}} <property> <name>dfs.ha.namenode.id</name> <value>name-{{POD_INSTANCE_INDEX}}-node</value> </property> {{/NAMENODE}} <property> <name>dfs.image.compress</name> <value>{{IMAGE_COMPRESS}}</value> </property> <property> <name>dfs.image.compression.codec</name> <value>{{IMAGE_COMPRESSION_CODEC}}</value> </property> <property> <name>dfs.client.read.shortcircuit</name> <value>{{CLIENT_READ_SHORTCIRCUIT}}</value> </property> <property> <name>dfs.client.read.shortcircuit.streams.cache.size</name> <value>{{CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE}}</value> </property> <property> <name>dfs.client.read.shortcircuit.streams.cache.size.expiry.ms</name> <value>{{CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS}}</value> </property> <property> <name>dfs.client.failover.proxy.provider.hdfs</name> <value>{{CLIENT_FAILOVER_PROXY_PROVIDER_HDFS}}</value> </property> <property> <name>dfs.domain.socket.path</name> <value>{{CLIENT_READ_SHORTCIRCUIT_PATH}}</value> </property> <property> <name>dfs.permissions.enabled</name> <value>{{PERMISSIONS_ENABLED}}</value> </property> {{#SECURE_MODE}} <property> <name>ignore.secure.ports.for.testing</name> <value>true</value> </property> <!-- Security Configuration --> <property> <name>hadoop.security.auth_to_local</name> <value> RULE:[2:$1@$0](.*)s/.*/{{TASK_USER}}/ RULE:[1:$1@$0](.*)s/.*/{{TASK_USER}}/ </value> </property> <property> <name>dfs.block.access.token.enable</name> <value>true</value> </property> <property> <name>dfs.namenode.kerberos.principal.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.datanode.kerberos.principal.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.journalnode.kerberos.principal.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.cluster.administrators</name> <value>core,root,hdfs,nobody</value> </property> <property> <name>dfs.web.authentication.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/{{TASK_NAME}}.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.web.authentication.kerberos.keytab</name> <value>keytabs/{{KERBEROS_PRIMARY}}.{{TASK_NAME}}.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> {{#DATANODE}} <!-- DataNode Security Configuration --> <property> <name>dfs.datanode.keytab.file</name> <value>keytabs/{{KERBEROS_PRIMARY}}.data-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.datanode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/data-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.datanode.data.dir.perm</name> <value>700</value> </property> {{/DATANODE}} {{#NAMENODE}} <!-- NameNode Security Configuration --> <property> <name>dfs.namenode.keytab.file</name> <value>keytabs/{{KERBEROS_PRIMARY}}.name-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.namenode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/name-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.namenode.kerberos.internal.spnego.principal</name> <value>{{KERBEROS_PRIMARY_HTTP}}/name-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> {{/NAMENODE}} {{#ZKFC}} <!-- NameNode Security Configuration --> <property> <name>dfs.namenode.keytab.file</name> <value>keytabs/{{KERBEROS_PRIMARY}}.zkfc-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.namenode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/zkfc-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.namenode.kerberos.internal.spnego.principal</name> <value>{{KERBEROS_PRIMARY_HTTP}}/zkfc-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> {{/ZKFC}} {{#JOURNALNODE}} <!-- JournalNode Security Configuration --> <property> <name>dfs.journalnode.keytab.file</name> <value>keytabs/hdfs.journal-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.journalnode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/journal-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.journalnode.kerberos.internal.spnego.principal</name> <value>{{KERBEROS_PRIMARY_HTTP}}/journal-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> {{/JOURNALNODE}} {{/SECURE_MODE}} </configuration> "
			}]
		}, {
			name: "format",
			goal: "FINISHED",
			resource - set: {
				id: "zkfc-resources",
				resource - specifications: [{
					@type: "DefaultResourceSpec",
					name: "cpus",
					value: {
						type: "SCALAR",
						scalar: {
							value: 0.3
						},
						ranges: null,
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					envKey: null
				}, {
					@type: "DefaultResourceSpec",
					name: "mem",
					value: {
						type: "SCALAR",
						scalar: {
							value: 512
						},
						ranges: null,
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					envKey: null
				}],
				volume - specifications: [],
				role: "hdfs-role",
				principal: "hdfs-principal"
			},
			command - spec: {
				value: "./bootstrap && ./hadoop-2.6.0-cdh5.9.1/bin/hdfs zkfc -formatZK",
				environment: {
					CLIENT_FAILOVER_PROXY_PROVIDER_HDFS: "org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider",
					CLIENT_READ_SHORTCIRCUIT: "true",
					CLIENT_READ_SHORTCIRCUIT_PATH: "/var/lib/hadoop-hdfs/dn_socket",
					CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE: "1000",
					CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS: "1000",
					DATA_NODE_BALANCE_BANDWIDTH_PER_SEC: "41943040",
					DATA_NODE_HANDLER_COUNT: "10",
					DATA_NODE_HTTP_PORT: "9004",
					DATA_NODE_IPC_PORT: "9005",
					DATA_NODE_RPC_PORT: "9003",
					HADOOP_PROXYUSER_HTTPFS_GROUPS: "*",
					HADOOP_PROXYUSER_HTTPFS_HOSTS: "*",
					HADOOP_PROXYUSER_HUE_GROUPS: "*",
					HADOOP_PROXYUSER_HUE_HOSTS: "*",
					HADOOP_PROXYUSER_ROOT_GROUPS: "*",
					HADOOP_PROXYUSER_ROOT_HOSTS: "*",
					HADOOP_ROOT_LOGGER: "INFO,console",
					HA_AUTOMATIC_FAILURE: "true",
					HA_FENCING_METHODS: "shell(/bin/true)",
					IMAGE_COMPRESS: "true",
					IMAGE_COMPRESSION_CODEC: "org.apache.hadoop.io.compress.SnappyCodec",
					JOURNAL_NODE_HTTP_PORT: "8480",
					JOURNAL_NODE_RPC_PORT: "8485",
					NAME_NODE_DATA_NODE_REGISTRATION_IP_HOSTNAME_CHECK: "false",
					NAME_NODE_HANDLER_COUNT: "20",
					NAME_NODE_HEARTBEAT_RECHECK_INTERVAL: "60000",
					NAME_NODE_HTTP_PORT: "9002",
					NAME_NODE_INVALIDATE_WORK_PCT_PER_ITERATION: "0.95",
					NAME_NODE_REPLICATION_WORK_MULTIPLIER_PER_ITERATION: "4",
					NAME_NODE_RPC_PORT: "9001",
					NAME_NODE_SAFEMODE_THRESHOLD_PCT: "0.9",
					PERMISSIONS_ENABLED: "false",
					SERVICE_ZK_ROOT: "dcos-service-hdfs",
					TASK_USER: "root",
					ZKFC: "true"
				}
			},
			health - check - spec: null,
			readiness - check - spec: null,
			config - files: [{
				name: "core-site",
				relative - path: "hadoop-2.6.0-cdh5.9.1/etc/hadoop/core-site.xml",
				template - content: "<?xml version="
				1.0 " encoding="
				UTF - 8 " standalone="
				no "?> <?xml-stylesheet type="
				text / xsl " href="
				configuration.xsl "?><configuration> <property> <name>fs.default.name</name> <value>hdfs://hdfs</value> </property> <property> <name>hadoop.proxyuser.hue.hosts</name> <value>{{HADOOP_PROXYUSER_HUE_HOSTS}}</value> </property> <property> <name>hadoop.proxyuser.hue.groups</name> <value>{{HADOOP_PROXYUSER_HUE_GROUPS}}</value> </property> <property> <name>hadoop.proxyuser.root.hosts</name> <value>{{HADOOP_PROXYUSER_ROOT_HOSTS}}</value> </property> <property> <name>hadoop.proxyuser.root.groups</name> <value>{{HADOOP_PROXYUSER_ROOT_GROUPS}}</value> </property> <property> <name>hadoop.proxyuser.httpfs.groups</name> <value>{{HADOOP_PROXYUSER_HTTPFS_GROUPS}}</value> </property> <property> <name>hadoop.proxyuser.httpfs.hosts</name> <value>{{HADOOP_PROXYUSER_HTTPFS_HOSTS}}</value> </property> <property> <name>ha.zookeeper.parent-znode</name> <value>/{{SERVICE_ZK_ROOT}}/hadoop-ha</value> </property> {{#SECURE_MODE}} <property> <!-- The ZKFC nodes use this property to verify they are connecting to the namenode with the expected principal. --> <name>hadoop.security.service.user.name.key.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>hadoop.security.authentication</name> <value>kerberos</value> </property> <property> <name>hadoop.security.authorization</name> <value>true</value> </property> {{/SECURE_MODE}} </configuration> "
			}, {
				name: "hdfs-site",
				relative - path: "hadoop-2.6.0-cdh5.9.1/etc/hadoop/hdfs-site.xml",
				template - content: "<?xml version="
				1.0 " encoding="
				UTF - 8 " standalone="
				no "?> <?xml-stylesheet type="
				text / xsl " href="
				configuration.xsl "?> <configuration> <property> <name>dfs.nameservice.id</name> <value>hdfs</value> </property> <property> <name>dfs.nameservices</name> <value>hdfs</value> </property> <property> <name>dfs.ha.namenodes.hdfs</name> <value>name-0-node,name-1-node</value> </property> <!-- namenode --> <property> <name>dfs.namenode.shared.edits.dir</name> <value>qjournal://journal-0-node.{{FRAMEWORK_NAME}}.mesos:{{JOURNAL_NODE_RPC_PORT}};journal-1-node.{{FRAMEWORK_NAME}}.mesos:{{JOURNAL_NODE_RPC_PORT}};journal-2-node.{{FRAMEWORK_NAME}}.mesos:{{JOURNAL_NODE_RPC_PORT}}/hdfs</value> </property> <property> <name>dfs.namenode.name.dir</name> <value>{{MESOS_SANDBOX}}/name-data</value> </property> <property> <name>dfs.namenode.safemode.threshold-pct</name> <value>{{NAME_NODE_SAFEMODE_THRESHOLD_PCT}}</value> </property> <property> <name>dfs.namenode.heartbeat.recheck-interval</name> <value>{{NAME_NODE_HEARTBEAT_RECHECK_INTERVAL}}</value> </property> <property> <name>dfs.namenode.handler.count</name> <value>{{NAME_NODE_HANDLER_COUNT}}</value> </property> <property> <name>dfs.namenode.invalidate.work.pct.per.iteration</name> <value>{{NAME_NODE_INVALIDATE_WORK_PCT_PER_ITERATION}}</value> </property> <property> <name>dfs.namenode.replication.work.multiplier.per.iteration</name> <value>{{NAME_NODE_REPLICATION_WORK_MULTIPLIER_PER_ITERATION}}</value> </property> <property> <name>dfs.namenode.datanode.registration.ip-hostname-check</name> <value>{{NAME_NODE_DATA_NODE_REGISTRATION_IP_HOSTNAME_CHECK}}</value> </property> <!-- name-0-node --> <property> <name>dfs.namenode.rpc-address.hdfs.name-0-node</name> <value>name-0-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.namenode.rpc-bind-host.hdfs.name-0-node</name> <value>0.0.0.0</value> </property> <property> <name>dfs.namenode.http-address.hdfs.name-0-node</name> <value>name-0-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.namenode.http-bind-host.hdfs.name-0-node</name> <value>0.0.0.0</value> </property> <!-- name-1-node --> <property> <name>dfs.namenode.rpc-address.hdfs.name-1-node</name> <value>name-1-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.namenode.rpc-bind-host.hdfs.name-1-node</name> <value>0.0.0.0</value> </property> <property> <name>dfs.namenode.http-address.hdfs.name-1-node</name> <value>name-1-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.namenode.http-bind-host.hdfs.name-1-node</name> <value>0.0.0.0</value> </property> <!-- journalnode --> <property> <name>dfs.journalnode.rpc-address</name> <value>0.0.0.0:{{JOURNAL_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.journalnode.http-address</name> <value>0.0.0.0:{{JOURNAL_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.journalnode.edits.dir</name> <value>{{MESOS_SANDBOX}}/journal-data</value> </property> <!-- datanode --> <property> <name>dfs.datanode.address</name> <value>0.0.0.0:{{DATA_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.datanode.http.address</name> <value>0.0.0.0:{{DATA_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.datanode.ipc.address</name> <value>0.0.0.0:{{DATA_NODE_IPC_PORT}}</value> </property> <property> <name>dfs.datanode.data.dir</name> <value>{{MESOS_SANDBOX}}/data-data</value> </property> <property> <name>dfs.datanode.balance.bandwidthPerSec</name> <value>41943040</value> </property> <property> <name>dfs.datanode.handler.count</name> <value>{{DATA_NODE_HANDLER_COUNT}}</value> </property> <!-- HA --> <property> <name>ha.zookeeper.quorum</name> <value>master.mesos:2181</value> </property> <property> <name>dfs.ha.fencing.methods</name> <value>{{HA_FENCING_METHODS}}</value> </property> <property> <name>dfs.ha.automatic-failover.enabled</name> <value>{{HA_AUTOMATIC_FAILURE}}</value> </property> {{#NAMENODE}} <property> <name>dfs.ha.namenode.id</name> <value>name-{{POD_INSTANCE_INDEX}}-node</value> </property> {{/NAMENODE}} <property> <name>dfs.image.compress</name> <value>{{IMAGE_COMPRESS}}</value> </property> <property> <name>dfs.image.compression.codec</name> <value>{{IMAGE_COMPRESSION_CODEC}}</value> </property> <property> <name>dfs.client.read.shortcircuit</name> <value>{{CLIENT_READ_SHORTCIRCUIT}}</value> </property> <property> <name>dfs.client.read.shortcircuit.streams.cache.size</name> <value>{{CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE}}</value> </property> <property> <name>dfs.client.read.shortcircuit.streams.cache.size.expiry.ms</name> <value>{{CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS}}</value> </property> <property> <name>dfs.client.failover.proxy.provider.hdfs</name> <value>{{CLIENT_FAILOVER_PROXY_PROVIDER_HDFS}}</value> </property> <property> <name>dfs.domain.socket.path</name> <value>{{CLIENT_READ_SHORTCIRCUIT_PATH}}</value> </property> <property> <name>dfs.permissions.enabled</name> <value>{{PERMISSIONS_ENABLED}}</value> </property> {{#SECURE_MODE}} <property> <name>ignore.secure.ports.for.testing</name> <value>true</value> </property> <!-- Security Configuration --> <property> <name>hadoop.security.auth_to_local</name> <value> RULE:[2:$1@$0](.*)s/.*/{{TASK_USER}}/ RULE:[1:$1@$0](.*)s/.*/{{TASK_USER}}/ </value> </property> <property> <name>dfs.block.access.token.enable</name> <value>true</value> </property> <property> <name>dfs.namenode.kerberos.principal.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.datanode.kerberos.principal.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.journalnode.kerberos.principal.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.cluster.administrators</name> <value>core,root,hdfs,nobody</value> </property> <property> <name>dfs.web.authentication.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/{{TASK_NAME}}.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.web.authentication.kerberos.keytab</name> <value>keytabs/{{KERBEROS_PRIMARY}}.{{TASK_NAME}}.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> {{#DATANODE}} <!-- DataNode Security Configuration --> <property> <name>dfs.datanode.keytab.file</name> <value>keytabs/{{KERBEROS_PRIMARY}}.data-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.datanode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/data-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.datanode.data.dir.perm</name> <value>700</value> </property> {{/DATANODE}} {{#NAMENODE}} <!-- NameNode Security Configuration --> <property> <name>dfs.namenode.keytab.file</name> <value>keytabs/{{KERBEROS_PRIMARY}}.name-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.namenode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/name-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.namenode.kerberos.internal.spnego.principal</name> <value>{{KERBEROS_PRIMARY_HTTP}}/name-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> {{/NAMENODE}} {{#ZKFC}} <!-- NameNode Security Configuration --> <property> <name>dfs.namenode.keytab.file</name> <value>keytabs/{{KERBEROS_PRIMARY}}.zkfc-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.namenode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/zkfc-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.namenode.kerberos.internal.spnego.principal</name> <value>{{KERBEROS_PRIMARY_HTTP}}/zkfc-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> {{/ZKFC}} {{#JOURNALNODE}} <!-- JournalNode Security Configuration --> <property> <name>dfs.journalnode.keytab.file</name> <value>keytabs/hdfs.journal-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.journalnode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/journal-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.journalnode.kerberos.internal.spnego.principal</name> <value>{{KERBEROS_PRIMARY_HTTP}}/journal-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> {{/JOURNALNODE}} {{/SECURE_MODE}} </configuration> "
			}]
		}],
		placement - rule: {
			@type: "AndRule",
			rules: [{
				@type: "TaskTypeRule",
				type: "zkfc",
				converter: {
					@type: "TaskTypeLabelConverter"
				},
				behavior: "AVOID"
			}, {
				@type: "TaskTypeRule",
				type: "name",
				converter: {
					@type: "TaskTypeLabelConverter"
				},
				behavior: "COLOCATE"
			}]
		}
	}, {
		type: "data",
		user: null,
		count: 3,
		container: null,
		uris: [
			"https://downloads.mesosphere.com/hdfs/assets/hadoop-2.6.0-cdh5.9.1-dcos.tar.gz",
			"https://downloads.mesosphere.com/hdfs/assets/1.0.0-2.6.0/bootstrap.zip"
		],
		task - specs: [{
			name: "node",
			goal: "RUNNING",
			resource - set: {
				id: "node-resource-set",
				resource - specifications: [{
					@type: "DefaultResourceSpec",
					name: "cpus",
					value: {
						type: "SCALAR",
						scalar: {
							value: 0.3
						},
						ranges: null,
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					envKey: null
				}, {
					@type: "DefaultResourceSpec",
					name: "mem",
					value: {
						type: "SCALAR",
						scalar: {
							value: 512
						},
						ranges: null,
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					envKey: null
				}, {
					@type: "PortsSpec",
					name: "ports",
					value: {
						type: "RANGES",
						scalar: null,
						ranges: {
							range: [{
								begin: 9003,
								end: 9003
							}, {
								begin: 9004,
								end: 9004
							}, {
								begin: 9005,
								end: 9005
							}]
						},
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					port - specs: [{
						@type: "PortSpec",
						name: "ports",
						value: {
							type: "RANGES",
							scalar: null,
							ranges: {
								range: [{
									begin: 9003,
									end: 9003
								}]
							},
							set: null,
							text: null
						},
						role: "hdfs-role",
						principal: "hdfs-principal",
						port - name: "data-rpc",
						envKey: null
					}, {
						@type: "PortSpec",
						name: "ports",
						value: {
							type: "RANGES",
							scalar: null,
							ranges: {
								range: [{
									begin: 9004,
									end: 9004
								}]
							},
							set: null,
							text: null
						},
						role: "hdfs-role",
						principal: "hdfs-principal",
						port - name: "data-http",
						envKey: null
					}, {
						@type: "PortSpec",
						name: "ports",
						value: {
							type: "RANGES",
							scalar: null,
							ranges: {
								range: [{
									begin: 9005,
									end: 9005
								}]
							},
							set: null,
							text: null
						},
						role: "hdfs-role",
						principal: "hdfs-principal",
						port - name: "data-ipc",
						envKey: null
					}],
					envKey: null
				}],
				volume - specifications: [{
					@type: "DefaultVolumeSpec",
					type: "ROOT",
					container - path: "data-data",
					name: "disk",
					value: {
						type: "SCALAR",
						scalar: {
							value: 5000
						},
						ranges: null,
						set: null,
						text: null
					},
					role: "hdfs-role",
					principal: "hdfs-principal",
					envKey: "DISK_SIZE"
				}],
				role: "hdfs-role",
				principal: "hdfs-principal"
			},
			command - spec: {
				value: "./bootstrap && mkdir -p /var/lib/hadoop-hdfs && chown root /var/lib/hadoop-hdfs && ./hadoop-2.6.0-cdh5.9.1/bin/hdfs datanode ",
				environment: {
					CLIENT_FAILOVER_PROXY_PROVIDER_HDFS: "org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider",
					CLIENT_READ_SHORTCIRCUIT: "true",
					CLIENT_READ_SHORTCIRCUIT_PATH: "/var/lib/hadoop-hdfs/dn_socket",
					CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE: "1000",
					CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS: "1000",
					DATANODE: "true",
					DATA_NODE_BALANCE_BANDWIDTH_PER_SEC: "41943040",
					DATA_NODE_HANDLER_COUNT: "10",
					DATA_NODE_HTTP_PORT: "9004",
					DATA_NODE_IPC_PORT: "9005",
					DATA_NODE_RPC_PORT: "9003",
					FRAMEWORK_NAME: "",
					HADOOP_PROXYUSER_HTTPFS_GROUPS: "*",
					HADOOP_PROXYUSER_HTTPFS_HOSTS: "*",
					HADOOP_PROXYUSER_HUE_GROUPS: "*",
					HADOOP_PROXYUSER_HUE_HOSTS: "*",
					HADOOP_PROXYUSER_ROOT_GROUPS: "*",
					HADOOP_PROXYUSER_ROOT_HOSTS: "*",
					HADOOP_ROOT_LOGGER: "INFO,console",
					HA_AUTOMATIC_FAILURE: "true",
					HA_FENCING_METHODS: "shell(/bin/true)",
					IMAGE_COMPRESS: "true",
					IMAGE_COMPRESSION_CODEC: "org.apache.hadoop.io.compress.SnappyCodec",
					JOURNAL_NODE_HTTP_PORT: "8480",
					JOURNAL_NODE_RPC_PORT: "8485",
					NAME_NODE_DATA_NODE_REGISTRATION_IP_HOSTNAME_CHECK: "false",
					NAME_NODE_HANDLER_COUNT: "20",
					NAME_NODE_HEARTBEAT_RECHECK_INTERVAL: "60000",
					NAME_NODE_HTTP_PORT: "9002",
					NAME_NODE_INVALIDATE_WORK_PCT_PER_ITERATION: "0.95",
					NAME_NODE_REPLICATION_WORK_MULTIPLIER_PER_ITERATION: "4",
					NAME_NODE_RPC_PORT: "9001",
					NAME_NODE_SAFEMODE_THRESHOLD_PCT: "0.9",
					PERMISSIONS_ENABLED: "false",
					SERVICE_ZK_ROOT: "dcos-service-hdfs",
					TASK_USER: "root"
				}
			},
			health - check - spec: null,
			readiness - check - spec: null,
			config - files: [{
				name: "core-site",
				relative - path: "hadoop-2.6.0-cdh5.9.1/etc/hadoop/core-site.xml",
				template - content: "<?xml version="
				1.0 " encoding="
				UTF - 8 " standalone="
				no "?> <?xml-stylesheet type="
				text / xsl " href="
				configuration.xsl "?><configuration> <property> <name>fs.default.name</name> <value>hdfs://hdfs</value> </property> <property> <name>hadoop.proxyuser.hue.hosts</name> <value>{{HADOOP_PROXYUSER_HUE_HOSTS}}</value> </property> <property> <name>hadoop.proxyuser.hue.groups</name> <value>{{HADOOP_PROXYUSER_HUE_GROUPS}}</value> </property> <property> <name>hadoop.proxyuser.root.hosts</name> <value>{{HADOOP_PROXYUSER_ROOT_HOSTS}}</value> </property> <property> <name>hadoop.proxyuser.root.groups</name> <value>{{HADOOP_PROXYUSER_ROOT_GROUPS}}</value> </property> <property> <name>hadoop.proxyuser.httpfs.groups</name> <value>{{HADOOP_PROXYUSER_HTTPFS_GROUPS}}</value> </property> <property> <name>hadoop.proxyuser.httpfs.hosts</name> <value>{{HADOOP_PROXYUSER_HTTPFS_HOSTS}}</value> </property> <property> <name>ha.zookeeper.parent-znode</name> <value>/{{SERVICE_ZK_ROOT}}/hadoop-ha</value> </property> {{#SECURE_MODE}} <property> <!-- The ZKFC nodes use this property to verify they are connecting to the namenode with the expected principal. --> <name>hadoop.security.service.user.name.key.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>hadoop.security.authentication</name> <value>kerberos</value> </property> <property> <name>hadoop.security.authorization</name> <value>true</value> </property> {{/SECURE_MODE}} </configuration> "
			}, {
				name: "hdfs-site",
				relative - path: "hadoop-2.6.0-cdh5.9.1/etc/hadoop/hdfs-site.xml",
				template - content: "<?xml version="
				1.0 " encoding="
				UTF - 8 " standalone="
				no "?> <?xml-stylesheet type="
				text / xsl " href="
				configuration.xsl "?> <configuration> <property> <name>dfs.nameservice.id</name> <value>hdfs</value> </property> <property> <name>dfs.nameservices</name> <value>hdfs</value> </property> <property> <name>dfs.ha.namenodes.hdfs</name> <value>name-0-node,name-1-node</value> </property> <!-- namenode --> <property> <name>dfs.namenode.shared.edits.dir</name> <value>qjournal://journal-0-node.{{FRAMEWORK_NAME}}.mesos:{{JOURNAL_NODE_RPC_PORT}};journal-1-node.{{FRAMEWORK_NAME}}.mesos:{{JOURNAL_NODE_RPC_PORT}};journal-2-node.{{FRAMEWORK_NAME}}.mesos:{{JOURNAL_NODE_RPC_PORT}}/hdfs</value> </property> <property> <name>dfs.namenode.name.dir</name> <value>{{MESOS_SANDBOX}}/name-data</value> </property> <property> <name>dfs.namenode.safemode.threshold-pct</name> <value>{{NAME_NODE_SAFEMODE_THRESHOLD_PCT}}</value> </property> <property> <name>dfs.namenode.heartbeat.recheck-interval</name> <value>{{NAME_NODE_HEARTBEAT_RECHECK_INTERVAL}}</value> </property> <property> <name>dfs.namenode.handler.count</name> <value>{{NAME_NODE_HANDLER_COUNT}}</value> </property> <property> <name>dfs.namenode.invalidate.work.pct.per.iteration</name> <value>{{NAME_NODE_INVALIDATE_WORK_PCT_PER_ITERATION}}</value> </property> <property> <name>dfs.namenode.replication.work.multiplier.per.iteration</name> <value>{{NAME_NODE_REPLICATION_WORK_MULTIPLIER_PER_ITERATION}}</value> </property> <property> <name>dfs.namenode.datanode.registration.ip-hostname-check</name> <value>{{NAME_NODE_DATA_NODE_REGISTRATION_IP_HOSTNAME_CHECK}}</value> </property> <!-- name-0-node --> <property> <name>dfs.namenode.rpc-address.hdfs.name-0-node</name> <value>name-0-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.namenode.rpc-bind-host.hdfs.name-0-node</name> <value>0.0.0.0</value> </property> <property> <name>dfs.namenode.http-address.hdfs.name-0-node</name> <value>name-0-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.namenode.http-bind-host.hdfs.name-0-node</name> <value>0.0.0.0</value> </property> <!-- name-1-node --> <property> <name>dfs.namenode.rpc-address.hdfs.name-1-node</name> <value>name-1-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.namenode.rpc-bind-host.hdfs.name-1-node</name> <value>0.0.0.0</value> </property> <property> <name>dfs.namenode.http-address.hdfs.name-1-node</name> <value>name-1-node.{{FRAMEWORK_NAME}}.mesos:{{NAME_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.namenode.http-bind-host.hdfs.name-1-node</name> <value>0.0.0.0</value> </property> <!-- journalnode --> <property> <name>dfs.journalnode.rpc-address</name> <value>0.0.0.0:{{JOURNAL_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.journalnode.http-address</name> <value>0.0.0.0:{{JOURNAL_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.journalnode.edits.dir</name> <value>{{MESOS_SANDBOX}}/journal-data</value> </property> <!-- datanode --> <property> <name>dfs.datanode.address</name> <value>0.0.0.0:{{DATA_NODE_RPC_PORT}}</value> </property> <property> <name>dfs.datanode.http.address</name> <value>0.0.0.0:{{DATA_NODE_HTTP_PORT}}</value> </property> <property> <name>dfs.datanode.ipc.address</name> <value>0.0.0.0:{{DATA_NODE_IPC_PORT}}</value> </property> <property> <name>dfs.datanode.data.dir</name> <value>{{MESOS_SANDBOX}}/data-data</value> </property> <property> <name>dfs.datanode.balance.bandwidthPerSec</name> <value>41943040</value> </property> <property> <name>dfs.datanode.handler.count</name> <value>{{DATA_NODE_HANDLER_COUNT}}</value> </property> <!-- HA --> <property> <name>ha.zookeeper.quorum</name> <value>master.mesos:2181</value> </property> <property> <name>dfs.ha.fencing.methods</name> <value>{{HA_FENCING_METHODS}}</value> </property> <property> <name>dfs.ha.automatic-failover.enabled</name> <value>{{HA_AUTOMATIC_FAILURE}}</value> </property> {{#NAMENODE}} <property> <name>dfs.ha.namenode.id</name> <value>name-{{POD_INSTANCE_INDEX}}-node</value> </property> {{/NAMENODE}} <property> <name>dfs.image.compress</name> <value>{{IMAGE_COMPRESS}}</value> </property> <property> <name>dfs.image.compression.codec</name> <value>{{IMAGE_COMPRESSION_CODEC}}</value> </property> <property> <name>dfs.client.read.shortcircuit</name> <value>{{CLIENT_READ_SHORTCIRCUIT}}</value> </property> <property> <name>dfs.client.read.shortcircuit.streams.cache.size</name> <value>{{CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE}}</value> </property> <property> <name>dfs.client.read.shortcircuit.streams.cache.size.expiry.ms</name> <value>{{CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS}}</value> </property> <property> <name>dfs.client.failover.proxy.provider.hdfs</name> <value>{{CLIENT_FAILOVER_PROXY_PROVIDER_HDFS}}</value> </property> <property> <name>dfs.domain.socket.path</name> <value>{{CLIENT_READ_SHORTCIRCUIT_PATH}}</value> </property> <property> <name>dfs.permissions.enabled</name> <value>{{PERMISSIONS_ENABLED}}</value> </property> {{#SECURE_MODE}} <property> <name>ignore.secure.ports.for.testing</name> <value>true</value> </property> <!-- Security Configuration --> <property> <name>hadoop.security.auth_to_local</name> <value> RULE:[2:$1@$0](.*)s/.*/{{TASK_USER}}/ RULE:[1:$1@$0](.*)s/.*/{{TASK_USER}}/ </value> </property> <property> <name>dfs.block.access.token.enable</name> <value>true</value> </property> <property> <name>dfs.namenode.kerberos.principal.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.datanode.kerberos.principal.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.journalnode.kerberos.principal.pattern</name> <value>{{KERBEROS_PRIMARY}}/*@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.cluster.administrators</name> <value>core,root,hdfs,nobody</value> </property> <property> <name>dfs.web.authentication.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/{{TASK_NAME}}.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.web.authentication.kerberos.keytab</name> <value>keytabs/{{KERBEROS_PRIMARY}}.{{TASK_NAME}}.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> {{#DATANODE}} <!-- DataNode Security Configuration --> <property> <name>dfs.datanode.keytab.file</name> <value>keytabs/{{KERBEROS_PRIMARY}}.data-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.datanode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/data-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.datanode.data.dir.perm</name> <value>700</value> </property> {{/DATANODE}} {{#NAMENODE}} <!-- NameNode Security Configuration --> <property> <name>dfs.namenode.keytab.file</name> <value>keytabs/{{KERBEROS_PRIMARY}}.name-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.namenode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/name-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.namenode.kerberos.internal.spnego.principal</name> <value>{{KERBEROS_PRIMARY_HTTP}}/name-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> {{/NAMENODE}} {{#ZKFC}} <!-- NameNode Security Configuration --> <property> <name>dfs.namenode.keytab.file</name> <value>keytabs/{{KERBEROS_PRIMARY}}.zkfc-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.namenode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/zkfc-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.namenode.kerberos.internal.spnego.principal</name> <value>{{KERBEROS_PRIMARY_HTTP}}/zkfc-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> {{/ZKFC}} {{#JOURNALNODE}} <!-- JournalNode Security Configuration --> <property> <name>dfs.journalnode.keytab.file</name> <value>keytabs/hdfs.journal-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos.keytab</value> </property> <property> <name>dfs.journalnode.kerberos.principal</name> <value>{{KERBEROS_PRIMARY}}/journal-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> <property> <name>dfs.journalnode.kerberos.internal.spnego.principal</name> <value>{{KERBEROS_PRIMARY_HTTP}}/journal-{{POD_INSTANCE_INDEX}}-node.{{FRAMEWORK_NAME}}.mesos@{{KERBEROS_REALM}}</value> </property> {{/JOURNALNODE}} {{/SECURE_MODE}} </configuration> "
			}, {
				name: "hadoop-metrics2",
				relative - path: "hadoop-2.6.0-cdh5.9.1/etc/hadoop/hadoop-metrics2.properties",
				template - content: "# Autogenerated by the Mesos Framework, DO NOT EDIT *.sink.statsd.class=org.apache.hadoop.metrics2.sink.StatsDSink datanode.sink.statsd.period=10 datanode.sink.statsd.server.host={{STATSD_UDP_HOST}} datanode.sink.statsd.server.port={{STATSD_UDP_PORT}} datanode.sink.statsd.skip.hostname=false"
			}]
		}],
		placement - rule: {
			@type: "TaskTypeRule",
			type: "data",
			converter: {
				@type: "TaskTypeLabelConverter"
			},
			behavior: "AVOID"
		}
	}],
	replacement - failure - policy: {
		permanentFailureTimoutMins: null,
		minReplaceDelayMins: 0
	}
}
```

## List Configs

You can list all configuration IDs by sending a GET request to `/v1/configurations`.

CLI Example
```
$ dcos hdfs config list
```

HTTP Example
```
$ curl -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/configurations
[
    "9a8d4308-ab9d-4121-b460-696ec3368ad6"
]
```

## View Specified Config

You can view a specific configuration by sending a GET request to `/v1/configurations/<config-id>`.

CLI Example
```
$ dcos hdfs config show 9a8d4308-ab9d-4121-b460-696ec3368ad6
```

HTTP Example
```
$ curl -H "Authorization:token=<auth_token>" <dcos_url>/service/hdfs/v1/configurations/9a8d4308-ab9d-4121-b460-696ec3368ad6
{
    ... same format as target config above ...
}
```

