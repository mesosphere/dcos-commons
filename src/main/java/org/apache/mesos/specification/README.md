Building a Stateful DC/OS Service
=================================
## Background
The goal for the stateful service SDK is to provide a standard, simple way to reliably run a highly available stateful service on DC/OS.  In the end, running a service of any kind on DC/OS amounts to running processes in Containers.  So the most basic prerequisite is a set of executables, stored in some location.

### Example Service
For the sake of explanation let us describe a fictional, but illustrative example service named `data-store`.  It is composed of `meta-data` nodes and `data` nodes.  In order to run a meta-data node one downloads the meta-data executable from a location `http://storage/meta-data.zip` and runs the command `./meta-data` after decompression.  A data node follows the same convention with locations and commands being `http://storage/data.zip` and `./data` respectively.

The goal state for `data-store` is to keep 2 `meta-data` nodes running and to provide storage through N `data` nodes.  As more data is stored the service will scale by providing more `data` nodes.  The minimum initial deployment of `data-store` is 2 `meta-data` nodes and 3 `data` nodes.

### Robust Services
The goal of creating a distributed stateful service is to be highly available, meaning that a service should continue to be responsive to clients even when some significant portion of the service has failed.  Further, it should not lose data even in extreme circumstances which might impact availability.  In general we address these requirements in three broad categories.

1. Deployment
2. Recovery
3. Maintenance

Deployment addresses going from a state where no service is deployed on a cluster, to a state where the service has been deployed.  It also covers moving from one valid service configuration to another.  This includes service specific configuration options, as well as vertical and horizontal scaling.

Recovery is concerned with repairing a service which encounters temporary faults such as machine reboots or network partitions, as well as permanent failures such as catastrophic hardware failures.

Maintenance is service specific but often includes such operations as backup and restore.  It might include other operations which improve performance or reliability of a service when performed periodically or in response to user input.

This document addresses deployment and recovery.  Maintenance operations will be described elsewhere.

## Service Specification
In general the core responsibility of a service author wishing to deploy their service on DC/OS is to provide a description of their service which illustrates the desired state of the system.  This approach is commonly referred to as "declarative".  The service author declares the desired state, and a system preforms operations to reach that goal.

In this SDK, the declaration of a service's desired state is encapsulated in a `ServiceSpecification`.  If someone were asked to provide the `ServiceSpecification` for a new deployment of an instance of the `data-store` service, she might write down something like this.

```
ServiceSpecification:
  name: data-store

  TaskType:
    name: meta-data
    count: 2
    location: http://storage/meta-data.zip
    command: ./meta-data

  TaskType:
    name: data
    count: 3
    location: http://storage/data.zip
    command: ./data
```

A little more detail is required to turn this into a real deployment on a cluster, but in spirit this is the role of the `ServiceSpecification` in this SDK.

A `ServiceSpecification` is nothing more than a name for the service and a list of `TaskTypeSpecification`s.  Each `TaskTypeSpecification` is a count of the desired TaskType, the type name, the command to launch the Task and some information about the resources it would like to consume.  These normally include `cpu`, `mem`, and `volume` resources.

For example in the case of a `meta-data` node we might write a `TaskTypeSpecification` like the following:

```java
TaskTypeSpecification taskTypeSpecification = new TaskTypeSpecification() {
    private final String role = SchedulerUtils.nameToRole("data-store");
    private final String principal = SchedulerUtils.nameToPrincipal("data-store");

    @Override
    public int getCount() {
        return 2;
    }

    @Override
    public String getName() {
        return "meta-data";
    }

    @Override
    public Protos.CommandInfo getCommand(int id) {
        return Protos.CommandInfo.newBuilder()
                .setValue("./metadata " + id)
                .addUris(
                        Protos.CommandInfo.URI.newBuilder()
                        .setValue("http://storage/meta-data.zip")
                        .build())
                .build();
    }

    @Override
    public Collection<ResourceSpecification> getResources() {
        return Arrays.asList(
                new DefaultResourceSpecification(
                        "cpus",
                        ValueUtils.getValue(ResourceUtils.getUnreservedScalar("cpus", 1.0)),
                        role,
                        principal),
                new DefaultResourceSpecification(
                        "mem",
                        ValueUtils.getValue(ResourceUtils.getUnreservedScalar("mem", 512)),
                        role,
                        principal));
    }

    @Override
    public Collection<VolumeSpecification> getVolumes() {
        VolumeSpecification volumeSpecification = new DefaultVolumeSpecification(
                5000,
                VolumeSpecification.Type.ROOT,
                "meta-data-container-path",
                role,
                principal);

        return Arrays.asList(volumeSpecification);
    }
}
```
The most complicated portion of defining a `TaskTypeSpecification` is defining the command.  It is a [protobuf CommandInfo as defined by the Mesos protobufs](https://github.com/apache/mesos/blob/0.28.x/include/mesos/mesos.proto#L353-L412).  The only element required from a `TaskTypeSpecification` perspective is the [`value` element](https://github.com/apache/mesos/blob/0.28.x/include/mesos/mesos.proto#L405).  It is almost certainly also useful to downlaod any dependencies or the binaries supporting the command `value` by specifying URIs [as defined here in the command](https://github.com/apache/mesos/blob/0.28.x/include/mesos/mesos.proto#L364-L387).  An example of these elements in a fictional form is provided in the `TaskTypeSpecification` above.

Writing a `TaskTypeSpecification` for the `data` node type is left as an exercise for the reader.

## Launching a Service
Once a `ServiceSpecification` is constructed it may be deployed on a DC/OS cluster by running the following on a machine with access to a Mesos master node.

```java
public static void main(String[] args) throws Exception {
    new DefaultService(API_PORT).register(getServiceSpecification());
}
```

This runs a Mesos Scheduler which will deploy the service as per the `ServiceSpecification`.  This Scheduler will ensure that the Tasks running the `meta-data` and `data` nodes will be fault tolerant in that it will relaunch each failed Task using the same resources on the same hosts as when it was initially deployed.  This is the core responsibility of a Scheduler, to deploy and keep up the Tasks specified in the `ServiceSpecification`.

Naturally, the next question is, "What ensures that the Scheduler is running so that it can maintain the availability of the service?"  On DC/OS, the answer is Marathon.  A Scheduler is deployed by Marathon as a Task, just as the `data` and `meta-data` nodes are deployed by the `data-store` Scheduler.  The way one instructs Marathon to deploy a service Scheduler is through definition of a Universe package. See [Universe documentation](https://github.com/mesosphere/universe#universe-purpose) for an in depth description of how to generate such a package.

## Service Updates
Service updates can be made simply be restarting a Scheduler with a new `ServiceSpecification`.  Changes to `VolumeSpecification`s are not supported and so should be considered carefully at the initial point of `ServiceSpecification`.

Changes to a ServiceSpecification will initiate a rolling update of the Service.  For example, a change to the count element of the `TaskTypeSpecification` for `meta-data` would cause a new `meta-data` node to be started.  If a change were made to the `cpu` `ResourceSpecification` then each `meta-data` node will be restarted with this new configuration.  Similarly if the command or location of the `meta-data` process were changed, each of the nodes would be restarted using the new command and/or location.
