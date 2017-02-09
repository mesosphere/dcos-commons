---
post_title: Install and Customize
menu_order: 10
feature_maturity: experimental
enterprise: 'no'
---

# About installing HDFS on Enterprise DC/OS

 <a name="install-enterprise"></a>
 In Enterprise DC/OS `strict` [security mode](https://docs.mesosphere.com/1.9/administration/installing/custom/configuration-parameters/#security), HDFS requires a service account. In `permissive`, a service account is optional. Only someone with `superuser` permission can create the service account. Refer to [Provisioning HDFS](https://docs.mesosphere.com/1.9/administration/id-and-access-mgt/service-auth/hdfs-auth/) for instructions.

# Default Installation

## Prerequisite

* A DC/OS cluster with at least 5 agent nodes, each with 8 Gb of memory and 10 Gb of disk available. Each agent must have 8480, 8485, 9000, 9001, 9002, 9005, and 9006, and 9007 available.

## Install
Install HDFS from the DC/OS CLI. Enterprise DC/OS users must follow additional instructions. See the About Installing Kafka on Enterprise DC/OS for more information.

```bash
$ dcos package install hdfs
```

This command creates a new HDFS cluster with 2 name nodes, 3 journal nodes, and 5 data nodes. Two clusters cannot share the same name. To install more than one HDFS cluster, customize the `name` at install time for each additional instance. See the Custom Installation section for more information.

The default installation may not be sufficient for a production deployment, but all cluster operations will work. If you are planning a production deployment with 3 replicas of each value and with local quorum consistency for read and write operations (a very common use case), this configuration is sufficient for development and testing purposes, and it can be scaled to a production deployment.

**Note:** Alternatively, you can [install HDFS from the DC/OS GUI](https://docs.mesosphere.com/1.9/usage/managing-services/install/).

# Custom Installation

If you are ready to ship into production, you will likely need to customize the deployment to suit the workload requirements of your application(s). Customize the default deployment by creating a JSON file, then pass it to `dcos package install` using the `--options` parameter.

Sample JSON options file named `sample-hdfs.json`:

```json
{
    "data_node": {
        "count": 10,
    }
}
```

The command below creates a cluster using `sample-hdfs.json`:

```
$ dcos package install --options=sample-hdfs.json hdfs
```

This cluster will have 10 data nodes instead of the default value of 3.
See the Configuration section for a list of fields that can be customized via a options JSON file when the HDFS cluster is created.

# Minimal Installation
Many of the other Infinity services currently support DC/OS Vagrant deployment. However, DC/OS HDFS currently only supports deployment with an HA name service managed by a Quorum Journal. The resource requirements for such a deployment make it prohibitive to install on a local development machine. The default deployment, is the minimal safe deployment for a DC/OS HDFS cluster. Community contributions to support deployment of a non-HA cluster, e.g. a single name node and data node with no failure detector, would be welcome.

# Multiple HDFS cluster Installation

Installing multiple HDFS clusters is identical to installing an HDFS cluster with a custom configuration, as described above. Use a JSON options file to specify a unique `name` for each installation:

```
$ cat hdfs1.json

{
   "service": {
       "name": "hdfs1"
   }
}

$ dcos package install hdfs --options=hdfs1.json
```

Use the `--name` argument after install time to specify which HDFS instance to query. All `dcos hdfs` CLI commands accept the `--name` argument. If you do not specify a service name, the CLI assumes the default value, `hdfs`.

# Colocation

An individual HDFS deployment will colocate name nodes with journal nodes, but it will not colocate two name nodes or two journal nodes on the same agent node in the cluster. Data nodes may be colocated with both name nodes and journal nodes. If multiple clusters are installed, they may share the same agent nodes in the cluster provided that no ports specified in the service configurations conflict for those node types.

# Installation Plan

When the DC/OS HDFS service is initially installed, it generates an installation plan as shown below.

```json
{
	"phases": [{
		"id": "fd33f9cb-88ba-4db6-9b8b-749bb1f63308",
		"name": "Reconciliation",
		"blocks": [{
			"id": "878859fc-5985-4fc9-8517-0b184485e705",
			"status": "Complete",
			"name": "Reconciliation",
			"message": "Reconciliation complete",
			"has_decision_point": false
		}],
		"status": "Complete"
	}, {
		"id": "15aa294a-6698-45ef-9695-95f1e8fcc47b",
		"name": "Quorum Journal",
		"blocks": [{
			"id": "23c46589-994e-495c-993a-f192a9d26f52",
			"status": "Complete",
			"name": "journalnode-0",
			"message": "journalnode-0 in target state.",
			"has_decision_point": false
		}, {
			"id": "c234f3aa-5845-4dba-8036-f191b87dbe6d",
			"status": "Complete",
			"name": "journalnode-1",
			"message": "journalnode-1 in target state.",
			"has_decision_point": false
		}, {
			"id": "87b9d739-a48d-473c-ad83-1dec8b5a7661",
			"status": "Complete",
			"name": "journalnode-2",
			"message": "journalnode-2 in target state.",
			"has_decision_point": false
		}],
		"status": "Complete"
	}, {
		"id": "21169e1a-3488-4738-9e6e-feabcb04df1f",
		"name": "Name Service",
		"blocks": [{
			"id": "4525fd99-6ebc-4e8d-90de-0a10d55a36ac",
			"status": "Complete",
			"name": "namenode-0",
			"message": "namenode-0 in target state.",
			"has_decision_point": false
		}, {
			"id": "12801661-b15c-4bb5-8d28-16cd00270610",
			"status": "Complete",
			"name": "namenode-1",
			"message": "namenode-1 in target state.",
			"has_decision_point": false
		}],
		"status": "Complete"
	}, {
		"id": "790e4b9e-7b1d-4bb6-871d-fa185eec8789",
		"name": "Distributed Storage",
		"blocks": [{
			"id": "487aaaa8-a351-45b6-9471-17ff7f07f62c",
			"status": "Complete",
			"name": "datanode-0",
			"message": "datanode-0 in target state.",
			"has_decision_point": false
		}, {
			"id": "d52ff01f-2bda-47b7-9551-89828d7d717d",
			"status": "Complete",
			"name": "datanode-1",
			"message": "datanode-1 in target state.",
			"has_decision_point": false
		}, {
			"id": "930f89bd-aa1e-4d9f-b8f6-74e765a989b3",
			"status": "Complete",
			"name": "datanode-2",
			"message": "datanode-2 in target state.",
			"has_decision_point": false
		}],
		"status": "Complete"
	}],
	"errors": [],
	"status": "Complete"
}
```

## Viewing the Installation Plan
The plan can be viewed from the API via the REST endpoint. A curl example is provided below. See the REST API Authentication part of the REST API Reference section for information on how this request must be authenticated.

```
curl -v -H "Authorization: token=$(dcos config show core.dcos_acs_token)" http://<dcos_url>/service/hdfs/v1/plan
```

## Plan Errors
The plan will display any errors that prevent installation in the errors list. The presence of any error indicates that the installation cannot progress. See the Troubleshooting section for information on resolving errors.

## Reconciliation Phase
The first phase of the installation plan is the reconciliation phase. This phase ensures that the DC/OS HDFS service maintains the correct status for the nodes that it has deployed. Reconciliation is a normal operation of the DC/OS HDFS Service and occurs each time the service starts. See [the Mesos documentation](http://mesos.apache.org/documentation/latest/reconciliation) for more information.

## Quorum Journal
The second phase of the installation is the Quorum Journal phase. This phase will deploy the requested number of journal nodes to provide a Quorum Journal for the HA name service. Each block in the phase represents an individual journal node. By default, there are three journal nodes, but you can increase this to five to provide higher availability guarantees for the Quorum Journal.

## Name Service
The third phase of the installation is deployment of the HA name service. This phase deploys two name nodes in HA configuration with ZooKeeper failure detection. Each block represents an individual name node, and there are always exactly two.

## Distributed Storage
The final phase of the installation is deployment of the distributed storage service. This phase deploys the data nodes that are configured to act as storage for the cluster. The number of journal nodes is fixed at installation time, but the number of data nodes can be reconfigured post installation.

## Pausing Installation
To pause installation, issue a REST API request as shown below. The installation will pause after completing installation of the current node and wait for user input.


```
curl -v -H "Authorization: token=$(dcos config show core.dcos_acs_token)" -X POST http://<dcos_url>/service/hdfs/v1/plan/interrupt
```

## Resuming Installation
If the installation has been paused, the REST API request below will resume installation at the next pending node.

```
curl -v -H "Authorization: token=$(dcos config show core.dcos_acs_token)" -X POST http://<dcos_url>/service/hdfs/v1/plan/continue
```
