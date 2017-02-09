---
post_title: Managing
menu_order: 50
feature_maturity: experimental
enterprise: 'no'
---

# Add a Data Node
Increase the `DATA_NODES` value from the DC/OS dashboard as described in the Configuring section. This creates an update plan as described in that section. An additional node will be added as the last block of that plan.

## Node Info

To view general information about a node, run the following command from the CLI.
```bash
$ dcos hdfs --name=<service-name> node describe <node-id>
```
Result:

```json
"journalnode-0": {
		"type": "JOURNAL_NODE",
		"name": "journalnode-0",
		"slave_id": "7f208a37-2127-4a79-9dd2-5a39835d2d4c-S1",
		"id": "journalnode-0__4dc0c7d1-aeb5-4ecb-934f-e0e0f2e46895",
		"hostname": "10.0.3.165",
		"state": "TASK_RUNNING",
		"cpus": 0.5,
		"disk_mb": 10240,
		"heap_mb": 2048,
		"memory_mb": 4096,
		"volumes": ["volume"]
	}
```

<table class="table">

  <tr>
    <th>Property</th>
    <th>Type</th>
    <th>Description</th>
  </tr>

  <tr>
    <td>type</td>
    <td>string</td>
    <td>The type of the node. This will be one of JOURNAL_NODE, NAME_NODE, or DATA_NODE.</td>
  </tr>
  <tr>
    <td>name</td>
    <td>string</td>
    <td>The name of the node. This is the name that can be used to reference the node in CLI commands, and it is the display name of the node in the DC/OS UI.</td>
  </tr>
  <tr>
    <td>id</td>
    <td>string</td>
    <td>The unique identifier for the an instance of the node. This ID is regenerated every time the node is re-launched.</td>
  </tr>
   <tr>
    <td>hostname</td>
    <td>string</td>
    <td>The hostname or IP address of the DC/OS agent on which the node is running.</td>
  </tr>

   <tr>
    <td>slave_id</td>
    <td>string</td>
    <td>The identifier of the DC/OS agent node where the node is running.</td>
  </tr>

  <tr>
    <td>state</td>
    <td>string</td>
    <td>The state of the task for the node. If the node is being installed, this value may be TASK_STATING or TASK_STARTING. Under normal operating conditions the state should be TASK_RUNNING. The state may be temporarily displayed as TASK_FINISHED during configuration updates or upgrades.</td>
  </tr>
  <tr>
    <td>cpus</td>
    <td>number</td>
    <td>The cpu shares allocated to the node.</td>
  </tr>
  <tr>
    <td>memory_mb</td>
    <td>integer</td>
    <td>The amount of memory, in MB, allocated to the node.</td>
  </tr>
  <tr>
    <td>heap_mb</td>
    <td>integer</td>
    <td>The amount of JVM heap, in MB, allocated to the node.</td>
  </tr>
  <tr>
    <td>disk_mb</td>
    <td>integer</td>
    <td>The amount of disk in MB, allocated to the node.</td>
  </tr>
  <tr>
    <td>volume</td>
    <td>array</td>
    <td>A list of volumes mounted into the nodes container.</td>
  </tr>

</table>
