<!-- THIS CONTENT DUPLICATES THE DC/OS OPERATION GUIDE -->

Support for an improved uninstall process was introduced in DC/OS 1.10. In order to take advantage of this new support, changes were required to the `{{ include.data.packageName }}` package, which were included as of versions 2.0.0-x and greater.

### DC/OS 1.10 and newer, and package 2.0.0-x and newer

If you are using DC/OS 1.10 or newer and the installed service has a version greater than 2.0.0-x:

1. Uninstall the service. From the DC/OS CLI, enter `dcos package uninstall --app-id=<instancename> {{ include.data.packageName }}`.

For example, to uninstall the {{ include.data.techName }} instance named `{{ include.data.serviceName }}-dev`, run:

```bash
dcos package uninstall --app-id={{ include.data.serviceName }}-dev {{ include.data.packageName }}
```

#### Uninstall flow

Uninstalling the service consists of the following steps:
- The scheduler is relaunched in Marathon with the environment variable `SDK_UNINSTALL` set to "true". This puts the Scheduler in an uninstall mode.
- The scheduler performs the uninstall with the following actions:
  - All running tasks for the service are terminated so that Mesos will reoffer their resources.
  - As the task resources are offered by Mesos, they are unreserved by the scheduler.
    - **Warning**: Any data stored in reserved disk resources will be irretrievably lost.
  - Once all known resources have been unreserved, the scheduler's persistent state in ZooKeeper is deleted.
- The cluster automatically removes the scheduler task once it advertises the completion of the uninstall process.

Note that once the uninstall operation has begun, it cannot be cancelled because it can leave the service in an uncertain, half-destroyed state.

#### Debugging an uninstall

In the vast majority of cases, this uninstall process goes off without a hitch. However in certain situations there can be snags along the way. For example, perhaps a machine in the cluster has permanently gone away, and the service being uninstalled had some resources allocated on that machine. This can result in the uninstall being stuck, because that machine's resources never being offered to the uninstalling scheduler. In turn, the uninstalling scheduler will not be able to successfully the resources it has on that machine.

This situation is indicated by looking at `deploy` plan while the uninstall is proceeding. The `deploy` plan may be viewed using either of the following methods:
- CLI: `dcos {{ include.data.packageName }} --name={{ include.data.serviceName}} plan show deploy` (after running `dcos package install --cli {{ include.data.packageName }}` if needed)
- HTTP: `https://yourcluster.com/service/{{ include.data.serviceName }}/v1/plans/deploy`

```bash
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName}} plan show deploy
deploy (IN_PROGRESS)
├─ kill-tasks (COMPLETE)
│  ├─ kill-task-node-0-server__1a4114bc-48bb-47f6-be99-1b5ca6d55c4e (COMPLETE)
│  ├─ kill-task-node-1-server__0c42118e-04fd-40e1-b49d-0d3f71d2d243 (COMPLETE)
│  └─ kill-task-node-2-server__e00cad38-f27f-4332-b1df-5118ca480d50 (COMPLETE)
├─ unreserve-resources (IN_PROGRESS)
│  ├─ unreserve-f41351a2-b478-4e13-a94c-705f530989ef (COMPLETE)
│  ├─ unreserve-48f64612-8427-4cde-86f4-4edeb9efff37 (COMPLETE)
│  ├─ unreserve-402d51f5-6014-4ca3-bd13-324dae62b888 (PENDING)
│  ├─ unreserve-cb95e869-277f-48b9-954f-08c0d7a26bcf (PENDING)
│  ├─ unreserve-cbd748d0-df7b-4d01-b0b7-6acf915d8f98 (COMPLETE)
│  ├─ unreserve-00ed63d6-427c-4492-9713-772390cc5241 (COMPLETE)
│  ├─ unreserve-5dd56b1d-4522-4bbd-88b5-de9fa0f181f2 (PENDING)
│  └─ unreserve-c9915f07-f446-4e14-a6b4-12c8dd2f914b (COMPLETE)
└─ deregister-service (PENDING)
   └─ deregister (PENDING)
```

As we can see above, some of the resources to unreserve are stuck in a `PENDING` state. We can force them into a `COMPLETE` state, and thereby allow the scheduler to finish the uninstall operation. This may be done using either of the following methods:
- CLI: `dcos {{ include.data.packageName }} --name={{ include.data.serviceName}} plan show deploy`
- HTTP: `https://yourcluster.com/service/{{ include.data.serviceName }}/v1/plans/deploy/forceComplete?phase=unreserve-resources&step=unreserve-<UUID>`

```bash
$ dcos cassandra plan force-complete deploy unreserve-resources unreserve-402d51f5-6014-4ca3-bd13-324dae62b888
$ dcos cassandra plan force-complete deploy unreserve-resources unreserve-cb95e869-277f-48b9-954f-08c0d7a26bcf
$ dcos cassandra plan force-complete deploy unreserve-resources unreserve-5dd56b1d-4522-4bbd-88b5-de9fa0f181f2
```

At this point the scheduler should show a `COMPLETE` state for these steps in the plan, allowing it to proceed normally with the uninstall operation:

```
$ dcos {{ include.data.packageName }} --name={{ include.data.serviceName}} plan show deploy
deploy (IN_PROGRESS)
├─ kill-tasks (COMPLETE)
│  ├─ kill-task-node-0-server__1a4114bc-48bb-47f6-be99-1b5ca6d55c4e (COMPLETE)
│  ├─ kill-task-node-1-server__0c42118e-04fd-40e1-b49d-0d3f71d2d243 (COMPLETE)
│  └─ kill-task-node-2-server__e00cad38-f27f-4332-b1df-5118ca480d50 (COMPLETE)
├─ unreserve-resources (COMPLETE)
│  ├─ unreserve-f41351a2-b478-4e13-a94c-705f530989ef (COMPLETE)
│  ├─ unreserve-48f64612-8427-4cde-86f4-4edeb9efff37 (COMPLETE)
│  ├─ unreserve-402d51f5-6014-4ca3-bd13-324dae62b888 (COMPLETE)
│  ├─ unreserve-cb95e869-277f-48b9-954f-08c0d7a26bcf (COMPLETE)
│  ├─ unreserve-cbd748d0-df7b-4d01-b0b7-6acf915d8f98 (COMPLETE)
│  ├─ unreserve-00ed63d6-427c-4492-9713-772390cc5241 (COMPLETE)
│  ├─ unreserve-5dd56b1d-4522-4bbd-88b5-de9fa0f181f2 (COMPLETE)
│  └─ unreserve-c9915f07-f446-4e14-a6b4-12c8dd2f914b (COMPLETE)
└─ deregister-service (PENDING)
   └─ deregister (PENDING)
```

#### Manual uninstall

If all else fails, one can simply manually perform the uninstall themselves. To do this, perform the following steps:
- Delete the uninstalling scheduler from Marathon
- Manually run `janitor.py`, as described in the DC/OS 1.9 instructions below, to clean up the remaining resources.

### DC/OS 1.9 and older, or package older than 2.0.0-x

If you are running DC/OS 1.9 or older, or a version of the service that is older than 2.0.0-x, follow these steps:

1. Stop the service. From the DC/OS CLI, enter `dcos package uninstall --app-id=<instancename> <packagename>`.
   For example, `dcos package uninstall --app-id={{ include.data.serviceName }}-dev {{ include.data.packageName }}`.
1. Clean up remaining reserved resources with the framework cleaner script, `janitor.py`. See [DC/OS documentation](https://docs.mesosphere.com/1.9/deploying-services/uninstall/#framework-cleaner) for more information about the framework cleaner script.

For example, to uninstall {{ include.data.techName }} instance named `{{ include.data.serviceName }}-dev`, run:

```bash
$ MY_SERVICE_NAME={{ include.data.serviceName }}-dev
$ dcos package uninstall --app-id=$MY_SERVICE_NAME {{ include.data.packageName }}`.
$ dcos node ssh --master-proxy --leader "docker run mesosphere/janitor /janitor.py \
    -r $MY_SERVICE_NAME-role \
    -p $MY_SERVICE_NAME-principal \
    -z dcos-service-$MY_SERVICE_NAME"
```

<!-- END DUPLICATE BLOCK -->
