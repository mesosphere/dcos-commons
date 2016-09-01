Building a Stateful DC/OS Service
======================

## Service Specification
As a motivating example, consider the case in which a stateful service is composed of 2 different types of nodes.  A common case is that some set of nodes persistently stores metadata regarding the location of data, while another set of nodes stores the data.  Let us term these two kinds of nodes `metadata` and `data`.  Let's call this service `my-service`.

An initial deployment of such a service might consist of 2 `metatdata` nodes and 3 `data` nodes.  To deploy such a system on DC/OS we can write a `ServiceSpecification` to describe this service.

A `ServiceSpecification` is nothing more than a name for the service and a list of `TaskTypeSpecification`s.  Each `TaskTypeSpecification` is a count of the desired TaskType, the type name, the command to launch the Task and some information about the resources it would like to consume.  These normally include `cpu`, `mem`, and `volume` resources.

For example in the case of a `metadata` node we might write a `TaskTypeSpecification` like the following:

```java
TaskTypeSpecification taskTypeSpecification = new TaskTypeSpecification() {
    @Override
    public int getCount() {
        return 2;
    }

    @Override
    public String getName() {
        return "metadata";
    }

    @Override
    public Protos.CommandInfo getCommand() {
        return Protos.CommandInfo.newBuilder()
                .setValue("./metadata")
                .addUris(
                        Protos.CommandInfo.URI.newBuilder()
                        .setValue("http://path/to/my/metadata/bits")
                        .build())
                .build();
    }

    @Override
    public Collection<ResourceSpecification> getResources() {
        return Arrays.asList(
                new DefaultResourceSpecification(
                        "cpus",
                        ValueUtils.getValue(ResourceUtils.getUnreservedScalar("cpus", 1.0)),
                        SchedulerUtils.nameToRole("my-service"),
                        SchedulerUtils.nameToPrincipal("my-service")),
                new DefaultResourceSpecification(
                        "mem",
                        ValueUtils.getValue(ResourceUtils.getUnreservedScalar("mem", 1024)),
                        SchedulerUtils.nameToRole("my-service"),
                        SchedulerUtils.nameToPrincipal("my-service")));
    }

    @Override
    public Optional<Collection<VolumeSpecification>> getVolumes() {
        VolumeSpecification volumeSpecification = new DefaultVolumeSpecification(
                5000,
                VolumeSpecification.Type.ROOT,
                "my-service-container-path",
                SchedulerUtils.nameToRole("my-service"),
                SchedulerUtils.nameToPrincipal("my-service"));

        return Optional.of(Arrays.asList(volumeSpecification));
    }
}
```

Writing a `TaskTypeSpecification` for the `data` node type is left as an exercise for the reader.  Once a `ServiceSpecification` is constructed it may be deployed on a DC/OS cluster by running the following on a machine with access to a Mesos master node.

```java
public static void main(String[] args) throws Exception {
    LOGGER.info("Starting reference scheduler with args: " + Arrays.asList(args));
    new DefaultService().register(getServiceSpecification());
}
```

This runs a Mesos Scheduler which will deploy the service as per the `ServiceSpecification`.  This system will be fault tolerant in that it will relaunch all failed Tasks on the same machine and using the same resources as when it was initially deployed.  This behavior will occur under any combination of Scheduler and Task failures.  By default if a Task fails to launch for greater than 20 minutes, it is destructively replaced.  All this behavior is overridable by replacement of default implementation of interfaces with more sophisticated custom components.  //TODO: Guide for customization

Deploying a Service's Scheduler reliably is accomplished most easily by defining a Universe service package.
## Defining a Universe package
// TODO: content
## Service Updates
Service updates can be made simply be restarting a Scheduler with a new `ServiceSpecification`.  Changes to `VolumeSpecification`s are not supported and so should be considered carefully at the initial point of `ServiceSpecification`.

Changes to a ServiceSpecification will initiate a rolling update of the Service.  For example, a change to the count element of the `TaskTypeSpecification` for the `metadata` would cause a new `metadata` node to be started.  If a change were made to the `cpu` `ResourceSpecification` then each `metadata` node will be restarted with this new configuration.  Similarly if the command used to start a `metadata` node were made, each of the nodes would be restarted using the new command.
