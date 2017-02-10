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
        "count": 10
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
	phases: [{
		id: "0c64b701-6b8b-440e-93e1-6c43b05abfc2",
		name: "jn-deploy",
		steps: [{
			id: "ba608f90-32c6-42e0-93c7-a6b51fc2e52b",
			status: "COMPLETE",
			name: "journal-0:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'journal-0:[node] [ba608f90-32c6-42e0-93c7-a6b51fc2e52b]' has status: 'COMPLETE'."
		}, {
			id: "f29c13d8-a477-4e2b-84ac-046cd8a7d283",
			status: "COMPLETE",
			name: "journal-1:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'journal-1:[node] [f29c13d8-a477-4e2b-84ac-046cd8a7d283]' has status: 'COMPLETE'."
		}, {
			id: "75da5719-9296-4872-887a-cc1ab157191b",
			status: "COMPLETE",
			name: "journal-2:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'journal-2:[node] [75da5719-9296-4872-887a-cc1ab157191b]' has status: 'COMPLETE'."
		}],
		status: "COMPLETE"
	}, {
		id: "967aadc8-ef25-402e-b81a-9fcab0e57463",
		name: "nn-deploy",
		steps: [{
			id: "33d02aa7-0927-428b-82d7-cec93cfde090",
			status: "COMPLETE",
			name: "name-0:[format]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'name-0:[format] [33d02aa7-0927-428b-82d7-cec93cfde090]' has status: 'COMPLETE'."
		}, {
			id: "62f048f4-7b6c-4ea0-8509-82b328d40e61",
			status: "STARTING",
			name: "name-0:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'name-0:[node] [62f048f4-7b6c-4ea0-8509-82b328d40e61]' has status: 'STARTING'."
		}, {
			id: "e7b8aa27-39b2-4aba-9d87-3b47c752878f",
			status: "PENDING",
			name: "name-1:[bootstrap]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'name-1:[bootstrap] [e7b8aa27-39b2-4aba-9d87-3b47c752878f]' has status: 'PENDING'."
		}, {
			id: "cd633e0d-6aac-45a9-b764-296676fc228d",
			status: "PENDING",
			name: "name-1:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'name-1:[node] [cd633e0d-6aac-45a9-b764-296676fc228d]' has status: 'PENDING'."
		}],
		status: "IN_PROGRESS"
	}, {
		id: "248fa719-dc57-4ef9-9c48-5e6e1218b6c2",
		name: "zkfc-deploy",
		steps: [{
			id: "812ae290-e6a4-4128-b486-7288e855bfe6",
			status: "PENDING",
			name: "zkfc-0:[format]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'zkfc-0:[format] [812ae290-e6a4-4128-b486-7288e855bfe6]' has status: 'PENDING'."
		}, {
			id: "8a401585-a5af-4540-94f0-dada95093329",
			status: "PENDING",
			name: "zkfc-0:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'zkfc-0:[node] [8a401585-a5af-4540-94f0-dada95093329]' has status: 'PENDING'."
		}, {
			id: "7eabc15d-7feb-4546-8699-0fadac1f303b",
			status: "PENDING",
			name: "zkfc-1:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'zkfc-1:[node] [7eabc15d-7feb-4546-8699-0fadac1f303b]' has status: 'PENDING'."
		}],
		status: "PENDING"
	}, {
		id: "ddb20a39-830b-417b-a901-5b9027e459f7",
		name: "dn-deploy",
		steps: [{
			id: "018deb17-8853-4d3b-820c-6b2caa55743f",
			status: "PENDING",
			name: "data-0:[node]",
			message: "com.mesosphere.sdk.scheduler.plan.DeploymentStep: 'data-0:[node] [018deb17-8853-4d3b-820c-6b2caa55743f]' has status: 'PENDING'."
		}],
		status: "PENDING"
	}],
	errors: [],
	status: "IN_PROGRESS"
}
```

## Viewing the Installation Plan
The plan can be viewed from the API via the REST endpoint. A curl example is provided below. See the REST API Authentication part of the REST API Reference section for information on how this request must be authenticated.

```
curl -v -H "Authorization: token=$(dcos config show core.dcos_acs_token)" http://<dcos_url>/service/hdfs/v1/plans/deploy
```

## Plan Errors
The plan will display any errors that prevent installation in the errors list. The presence of any error indicates that the installation cannot progress. See the Troubleshooting section for information on resolving errors.

## Quorum Journal
The first phase of the installation is the Quorum Journal phase. This phase will deploy three journal nodes to provide a Quorum Journal for the HA name service. Each step in the phase represents an individual journal node.

## Name Service
The second phase of the installation is deployment of the HA name service. This phase deploys two name nodes.  Needed format and bootstrap operations occur as necessary.

## ZKFC
The third phase of the installation is deployment of the ZKFC nodes. This phase deploys two ZKFC nodes to enable ZooKeeper failure detection. Each step represents an individual ZKFC node, and there are always exactly two.

## Distributed Storage
The final phase of the installation is deployment of the distributed storage service. This phase deploys the data nodes that are configured to act as storage for the cluster. The number of data nodes can be reconfigured post installation.

## Pausing Installation
To pause installation, issue a REST API request as shown below. The installation will pause after completing installation of the current node and wait for user input.


```
curl -v -H "Authorization: token=$(dcos config show core.dcos_acs_token)" -X POST http://<dcos_url>/service/hdfs/v1/plans/deploy/interrupt
```

## Resuming Installation
If the installation has been paused, the REST API request below will resume installation at the next pending node.

```
curl -v -H "Authorization: token=$(dcos config show core.dcos_acs_token)" -X POST http://<dcos_url>/service/hdfs/v1/plans/deploy/continue
```
