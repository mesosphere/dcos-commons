---
post_title: Limitations
menu_order: 80
feature_maturity: preview
enterprise: 'no'
---

* Seed Node Replace

While the Cassandra service supports a node replace operation via the command
`dcos cassandra pods replace ≤node_id≥`, the node replace operation performs a
`replace_address`, which is sufficient for non-seed nodes, but is insufficient
with regard to a seed node.

For more information and a comparable procedure for "Replacing a dead node or
dead seed node" from DataStax, see the following:
http://docs.datastax.com/en/cassandra/3.0/cassandra/operations/opsReplaceNode.html

The following script automates the DC/OS Cassandra service seed node replace
procedure and will be used to explain the equivalent manual procedure:

<!--- snip{ dcos_cassandra_seed_node_replace.sh -->
```shell
#!/bin/sh
# DC/OS Cassandra (C*) seed node restart script.
#
# This script helps automate the restart (or not) of non-seed nodes following
# a seed node restart. Restarting non-seed nodes is necessary as C* internally
# uses the resolved ip address for the seed node.
#
# Tested for the following DC/OS versions:
# - 1.10

cassandra_cli_install () {
    if ! dcos cassandra --help >/dev/null 2>&1; then
        dcos package install cassandra --cli
    fi
}

cassandra_nodes_list () {
    if [ -z "$CASSANDRA_NODES" ]; then
        CASSANDRA_NODES=$(dcos cassandra pods list \
            | jq 'map(.) | join(" ")' \
            | sed 's/\"//g')
    fi
    echo "$CASSANDRA_NODES"
}

cassandra_nodes_seed_get () {
    nodes=$(cassandra_nodes_list)
    for node in $nodes; do
        echo "$node"
        return
    done
}

cassandra_nodes_node_ip_get () {
    node=$1
    dcos cassandra pods info $node \
        |awk '/\"ipAddress\":/{i=$2; gsub("\"", "", i); gsub(",", "", i); print i}'
}

cassandra_nodes_node_taskstate_get () {
    node=$1
    dcos cassandra pods status $node \
        |awk '/\"state\":.*\"TASK_/ { print }' \
        |awk -F':' '{s=$2; gsub("\"", "", s); gsub(/^ /, "", s); gsub(/ $/, "", s); print s}'
}

cassandra_nodes_node_wait_for_taskstate () {
    node=$1
    task_state=$2
    retry_delay=1
    max_wait=60
    wait_remaining=$max_wait
    while [ "x$(cassandra_nodes_node_taskstate_get $node)" != "x$task_state" ]; do
        echo "waiting for $node to enter $task_state state"
        sleep $retry_delay
        wait_remaining=$((wait_remaining - 1))
        if [ $wait_remaining -lt 0 ]; then
            echo "Breaking out of wait for $node to reach $task_state, errors may abound..."
            break
        fi
    done
}

cassandra_nodes_node_restart () {
    node=$1
    dcos cassandra pods restart $node
}

cassandra_nodes_node_replace () {
    node=$1
    dcos cassandra pods replace $node
}

cassandra_nodes_node_taskid_get () {
    node=$1
    dcos task |awk "/${node}-server/ { print \$5}"
}

cassandra_nodes_node_nodetool_ () {
    node=$1
    nodetool_subcommand=$2
    shift 2
    nodetool_args="$@"

    java_dir=$(dcos task exec $node bash -c 'echo $PWD/$(ls -d jre* |tail -n 1)' |grep -v 'Overwriting environment')
    cassandra_dir=$(dcos task exec $node bash -c 'echo $PWD/$(ls -d apache-cassandra-* |head -n 1)' |grep -v 'Overwriting environment')
    cassandra_jmx_port=7199

    dcos task exec $node bash -c "export JAVA_HOME=${java_dir}; ${cassandra_dir}/bin/nodetool -p ${cassandra_jmx_port} ${nodetool_subcommand} ${nodetool_args}"
}

cassandra_nodes_node_nodetool_remove_by_dcosid () {
    node=$1
    node_to_remove=$2
    node_ip=$(dcos task |grep ${node_to_remove}|awk '{print $2}')
    cassandra_id=$(cassandra_nodes_node_nodetool_ ${node} status |awk "/${node_ip}/ {print \$7}")
    cassandra_nodes_node_nodetool_ ${node} removenode ${cassandra_id}
}

cassandra_nodes_seed_restart () {
    seed_node=$(cassandra_nodes_seed_get)
    echo "got seed_node: $seed_node"
    seed_ip_pre=$(cassandra_nodes_node_ip_get $seed_node)
    echo "seed_ip_pre: $seed_ip_pre"
    cassandra_nodes_node_replace $seed_node
    echo "restarted seed node, zzz a sec"
    cassandra_nodes_node_wait_for_taskstate $seed_node 'TASK_STAGING'
    cassandra_nodes_node_wait_for_taskstate $seed_node 'TASK_RUNNING'
    seed_ip_post=$(cassandra_nodes_node_ip_get $seed_node)
    echo "seed_ip_post: $seed_ip_post"
    if [ "x$seed_ip_pre" = "x$seed_ip_post" ]; then
        echo "the seed node ip did not change, so no need to restart the other nodes"
    else
        echo "the seed node ip changed, so A) removing the node and B) performing a rolling restart of the other nodes"
        for node in $(cassandra_nodes_list); do
            if [ "x$node" != "x$seed_node" ]; then
                non_seed_node=$node
                break
            fi
        done

        for node in $(cassandra_nodes_list); do
            if [ "x$node" = "x$seed_node" ]; then
                cassandra_nodes_node_nodetool_remove_by_dcosid $non_seed_node $seed_node
            else
                cassandra_nodes_node_restart $node
                sleep 1
                cassandra_nodes_node_wait_for_taskstate $node 'TASK_STAGING'
                cassandra_nodes_node_wait_for_taskstate $node 'TASK_RUNNING'
            fi
        done
    fi

    echo "if all went well, there should be only up nodes (UN in first field) in the following:"
    cassandra_nodes_node_nodetool_ $seed_node status
}

main () {
    cassandra_nodes_seed_restart
}
main
```
<!--- snip} script dcos_cassandra_seed_node_replace.sh -->

The corresponding manual procedure requires several facts to be repeatedly
used/applied. To this end, environment variables will be used. The shell
commands scattered throughout the manual procedure are assumed to be executed
within a single environment/context.

The corresponding manual procedure follows:

1. Take note of the seed node's id (SEED_NODE) and ip (SEED_IP) by issuing the
following commands:

1.a.

```shell
SEED_NODE=$( \
dcos cassandra pods list \
    |jq 'map(.) |join(" ")' |awk '{gsub("\"", "", $1); print $1}' \
)

1.b.

SEED_IP=$( \
dcos cassandra pods info $SEED_NODE \
    |awk '/\"ipAddress\":/{i=$2; gsub("\"", "", i); gsub(",", "", i); print i}'
)
```

2. In order to replace a seed node, issue the following command:

```shell
dcos cassandra pods replace $SEED_NODE
```

3. Wait until the seed node has been successfully replaced. Watch for the task
with an id starting with 'node-0' to progress through "Staging" (TASK_STAGING)
to the "Running" (TASK_RUNNING) state.
3.a. Within the shell, issue the following to wait for the task state for the
seed node:

```shell
# create a function to generalize obtaining a task state
cassandra_task_state () {
    node=$1
    dcos cassandra pods status ${node} \
        |awk '/\"state\":.*\"TASK_/ { print }' \
        |awk -F':' '{s=$2; gsub("\"", "", s); gsub(/^ /, "", s); gsub(/ $/, "", s); print s}'
}

# create a function to generalize waiting for a task state
cassandra_waitfor_task_state () {
    node=$1
    task_state=$2
    retry_delay=1 #second(s)
    max_wait=60 #iterations
    wait_remaining=${max_wait}
    while [ "x$(cassandra_task_state ${node})" != "x${task_state}" ]; do
        sleep ${retry_delay}
        wait_remaining=$((wait_remaining - 1))
        if [ ${wait_remaining -lt 0 ]; then
            break
        fi
    done
}

cassandra_waitfor_task_state $SEED_NODE TASK_STAGING
cassandra_waitfor_task_state $SEED_NODE TASK_RUNNING
```

4. After the seed node has been replaced, obtain the new seed node's ip by
issuing the command in step 1.b.

5. If the seed node's ip did not change, exit.

6. Since the seed node's ip changed, notify the underlying Cassandra cluster of
the change.
6.a. Remove the previous seed node from Cassandra's known cluster state, by
issuing the following commands:

```shell
# create a function to generalize the execution of Cassandra's nodetool (corresponds
# to cassandra_nodes_node_nodetool_ in the full script):

nodetool_ () {
    node=$1
    nodetool_subcommand=$2
    shift 2
    nodetool_args="$@"

    # local environment variables to make the `dcos task exec ... nodetool ...` easier.
    java_dir=$(dcos task exec $SEED_NODE bash -c 'echo $PWD/$(ls -d jre* |tail -n 1)' |grep -v 'Overwriting environment')
    cassandra_dir=$(dcos task exec $SEED_NODE bash -c 'echo $PWD/$(ls -d apache-cassandra-* |head -n 1)' |grep -v 'Overwriting environment')
    cassandra_jmx_port=7199

    dcos task exec $SEED_NODE bash -c "export JAVA_HOME=$(java_dir); ${cassandra_dir}/bin/nodetool -p ${cassandra_jmx_port} ${nodetool_subcommand} ${nodetool_args}"
}

# get the Cassandra id to use in the `nodetool removenode ...` command
node_ip=$(dcos task |grep $SEED_NODE |awk '{print $2}')
cassandra_id=$(nodetool_ $SEED_NODE status |awk "/${node_ip}/ {print \$7}")

# remove the old seed node using the new seed node's nodetool
nodetool_ $SEED_NODE removenode ${cassandra_id}
```

6.b. Perform a rolling restart of the non-seed nodes in the Cassandra cluster
by issuing the following commands:

```shell
for node in $(dcos cassandra pods list \
    |jq 'map(.) |join(" ")' |awk '{gsub("\"", "", $0); print $0}' \
); do
    if [ "x${node} != "x$SEED_NODE" ]; then
        dcos cassandra pods restart ${node}
        cassandra_waitfor_task_state $SEED_NODE TASK_STAGING
        cassandra_waitfor_task_state $SEED_NODE TASK_RUNNING
    fi
done
```

Expected Results:

* The Cassandra service is healthy again from a DC/OS perspective, all expected
  tasks are in "Running" state.
* The underlying Cassandra view of the cluster hash key ownership is healthy,
  all nodes in the DC/OS view are in "Up" state in Cassandra's `nodetool status`.
