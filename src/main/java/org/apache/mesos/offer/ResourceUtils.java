package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.*;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.Protos.Resource.DiskInfo.Persistence;
import org.apache.mesos.Protos.Resource.DiskInfo.Source;
import org.apache.mesos.Protos.Resource.ReservationInfo;
import org.apache.mesos.Protos.Value.Range;
import org.apache.mesos.Protos.Value.Ranges;
import org.apache.mesos.specification.ResourceSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * This class encapsulates common methods for manipulating Resources.
 */
public class ResourceUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceUtils.class);

    public static Resource getUnreservedResource(String name, Value value) {
        return setResource(Resource.newBuilder().setRole("*"), name, value);
    }

    public static Resource getDesiredResource(ResourceSpecification resourceSpecification) {
        return getDesiredResource(
                resourceSpecification.getRole(),
                resourceSpecification.getPrincipal(),
                resourceSpecification.getName(),
                resourceSpecification.getValue());
    }

    public static Resource getUnreservedMountVolume(double diskSize, String mountRoot) {
        Value diskValue = Value.newBuilder()
                .setType(Value.Type.SCALAR)
                .setScalar(Value.Scalar.newBuilder().setValue(diskSize))
                .build();
        Resource.Builder resBuilder = Resource.newBuilder(ResourceUtils.getUnreservedResource("disk", diskValue));
        resBuilder.setRole("*");
        resBuilder.setDisk(getUnreservedMountVolumeDiskInfo(mountRoot));

        return resBuilder.build();
    }

    public static Resource getDesiredMountVolume(String role, String principal, double diskSize, String containerPath) {
        Value diskValue = Value.newBuilder()
                .setType(Value.Type.SCALAR)
                .setScalar(Value.Scalar.newBuilder().setValue(diskSize))
                .build();
        Resource.Builder resBuilder = Resource.newBuilder(getUnreservedResource("disk", diskValue));
        resBuilder.setRole(role);
        resBuilder.setReservation(getDesiredReservationInfo(principal));
        resBuilder.setDisk(getDesiredMountVolumeDiskInfo(principal, containerPath));
        return resBuilder.build();
    }

    public static Resource getExpectedMountVolume(
            double diskSize,
            String resourceId,
            String role,
            String principal,
            String mountRoot,
            String containerPath,
            String persistenceId) {
        Value diskValue = Value.newBuilder()
                .setType(Value.Type.SCALAR)
                .setScalar(Value.Scalar.newBuilder().setValue(diskSize))
                .build();
        Resource.Builder resBuilder = Resource.newBuilder(ResourceUtils.getUnreservedResource("disk", diskValue));
        resBuilder.setRole(role);
        resBuilder.setDisk(getExpectedMountVolumeDiskInfo(mountRoot, containerPath, persistenceId, principal));
        resBuilder.setReservation(getExpectedReservationInfo(resourceId, principal));

        return resBuilder.build();
    }

    public static Resource getUnreservedRootVolume(double diskSize) {
        Value diskValue = Value.newBuilder()
                .setType(Value.Type.SCALAR)
                .setScalar(Value.Scalar.newBuilder().setValue(diskSize))
                .build();
        Resource.Builder resBuilder = Resource.newBuilder(ResourceUtils.getUnreservedResource("disk", diskValue));
        resBuilder.setRole("*");
        return resBuilder.build();
    }

    public static Resource getDesiredRootVolume(String role, String principal, double diskSize, String containerPath) {
        Value diskValue = Value.newBuilder()
                .setType(Value.Type.SCALAR)
                .setScalar(Value.Scalar.newBuilder().setValue(diskSize))
                .build();
        Resource.Builder resBuilder = Resource.newBuilder(getUnreservedResource("disk", diskValue));
        resBuilder.setRole(role);
        resBuilder.setReservation(getDesiredReservationInfo(principal));
        resBuilder.setDisk(getDesiredRootVolumeDiskInfo(principal, containerPath));
        return resBuilder.build();
    }

    public static Resource getExpectedRootVolume(
            double diskSize,
            String resourceId,
            String role,
            String principal,
            String persistenceId) {
        Value diskValue = Value.newBuilder()
                .setType(Value.Type.SCALAR)
                .setScalar(Value.Scalar.newBuilder().setValue(diskSize))
                .build();
        Resource.Builder resBuilder = Resource.newBuilder(ResourceUtils.getUnreservedResource("disk", diskValue));
        resBuilder.setRole(role);
        resBuilder.setDisk(getExpectedRootVolumeDiskInfo(persistenceId, principal));
        resBuilder.setReservation(getExpectedReservationInfo(resourceId, principal));

        return resBuilder.build();
    }

    public static Resource getDesiredResource(String role, String principal, String name, Value value) {
        return Resource.newBuilder(getUnreservedResource(name, value))
                .setRole(role)
                .setReservation(getDesiredReservationInfo(principal))
                .build();
    }

    public static Resource getUnreservedScalar(String name, double value) {
        Value val = Value.newBuilder()
                .setType(Value.Type.SCALAR)
                .setScalar(Value.Scalar.newBuilder().setValue(value))
                .build();
        Resource.Builder resBuilder = Resource.newBuilder(ResourceUtils.getUnreservedResource(name, val));
        resBuilder.setRole("*");

        return resBuilder.build();
    }

    public static Resource getExpectedScalar(
            String name,
            double value,
            String resourceId,
            String role,
            String principal) {
        Value val = Value.newBuilder()
                .setType(Value.Type.SCALAR)
                .setScalar(Value.Scalar.newBuilder().setValue(value))
                .build();
        Resource.Builder resBuilder = Resource.newBuilder(ResourceUtils.getUnreservedResource(name, val));
        resBuilder.setRole(role);
        resBuilder.setReservation(getExpectedReservationInfo(resourceId, principal));

        return resBuilder.build();
    }

    public static Resource getDesiredScalar(String role, String principal, String name, double value) {
        Value val = Value.newBuilder()
                .setType(Value.Type.SCALAR)
                .setScalar(Value.Scalar.newBuilder().setValue(value))
                .build();
        return getDesiredResource(role, principal, name, val);
    }

    public static Resource getUnreservedRanges(String name, List<Range> ranges) {
        Value val = Value.newBuilder()
                .setType(Value.Type.RANGES)
                .setRanges(Value.Ranges.newBuilder().addAllRange(ranges))
                .build();
        Resource.Builder resBuilder = Resource.newBuilder(ResourceUtils.getUnreservedResource(name, val));
        resBuilder.setRole("*");

        return resBuilder.build();
    }

    public static Resource getDesiredRanges(String role, String principal, String name, List<Range> ranges) {
        return getDesiredResource(
                role,
                principal,
                name,
                Value.newBuilder()
                        .setType(Value.Type.RANGES)
                        .setRanges(Ranges.newBuilder()
                                .addAllRange(ranges)
                                .build())
                        .build());
    }

    public static Resource getExpectedRanges(
            String name,
            List<Range> ranges,
            String resourceId,
            String role,
            String principal) {

        Value val = Value.newBuilder()
                .setType(Value.Type.RANGES)
                .setRanges(Value.Ranges.newBuilder().addAllRange(ranges))
                .build();
        Resource.Builder resBuilder = Resource.newBuilder(ResourceUtils.getUnreservedResource(name, val));
        resBuilder.setRole(role);
        resBuilder.setReservation(getExpectedReservationInfo(resourceId, principal));

        return resBuilder.build();
    }

    public static TaskInfo.Builder serializeCommandInfo(Protos.TaskInfo.Builder builder) {
        Protos.CommandInfo command = builder.getCommand();

        if (builder.hasExecutor()) {
            builder.clearCommand();
            builder.setData(command.toByteString());
        }

        return builder;
    }

    public static Resource setDynamicPortName(Resource resource, String name) {
        Labels labels = setDynamicPortName(resource.getReservation().getLabels(), name);

        return Resource.newBuilder(resource)
                .setReservation(
                        ReservationInfo.newBuilder(resource.getReservation())
                        .clearLabels()
                        .setLabels(labels))
                .build();
    }

    private static Labels setDynamicPortName(Labels labels, String name) {
        Labels.Builder labelBuilder = Labels.newBuilder(labels);
        labelBuilder.addLabelsBuilder()
                .setKey(MesosResource.DYNAMIC_PORT_KEY)
                .setValue(name)
                .build();

        return labelBuilder.build();
    }

    public static Protos.Environment getEnvironment(List<Resource> resources) {
        Protos.Environment.Builder envBuilder = Protos.Environment.newBuilder();
        for (Resource resource : resources) {
            String portName = DynamicPortRequirement.getPortName(resource);
            if (portName != null) {
                String portNumber = String.valueOf(resource.getRanges().getRange(0).getBegin());
                envBuilder.addVariables(Protos.Environment.Variable.newBuilder()
                        .setName(portName)
                        .setValue(portNumber));
            }
        }

        return envBuilder.build();
    }

    public static Protos.Environment updateEnvironment(Protos.Environment env, List<Resource> resources) {
        Protos.Environment resEnv = ResourceUtils.getEnvironment(resources);
        return Protos.Environment.newBuilder(env)
                .addAllVariables(resEnv.getVariablesList())
                .build();
    }

    public static TaskInfo.Builder updateEnvironment(Protos.TaskInfo.Builder builder, List<Resource> resources) {
        Protos.Environment updateEnv = ResourceUtils.updateEnvironment(
                builder.getCommand().getEnvironment(), resources);
        if (updateEnv.getVariablesCount() > 0) {
            return builder.setCommand(Protos.CommandInfo.newBuilder(builder.getCommand())
                    .setEnvironment(updateEnv));
        } else {
            return builder;
        }
    }

    public static ExecutorInfo.Builder updateEnvironment(
            Protos.ExecutorInfo.Builder builder, List<Resource> resources) {
        Protos.Environment updateEnv = ResourceUtils.updateEnvironment(
                builder.getCommand().getEnvironment(), resources);
        if (updateEnv.getVariablesCount() > 0) {
            return builder.setCommand(Protos.CommandInfo.newBuilder(builder.getCommand())
                    .setEnvironment(updateEnv));
        } else {
            return builder;
        }
    }

    public static Resource setValue(Resource resource, Value value) {
        return setResource(Resource.newBuilder(resource), resource.getName(), value);
    }

    public static Resource setResourceId(Resource resource, String resourceId) {
        return Resource.newBuilder(resource)
                .setReservation(setResourceId(resource.getReservation(), resourceId))
                .build();
    }

    public static String getResourceId(Resource resource) {
        if (resource.hasReservation() && resource.getReservation().hasLabels()) {
            for (Label label : resource.getReservation().getLabels().getLabelsList()) {
                if (label.getKey().equals(MesosResource.RESOURCE_ID_KEY)) {
                    return label.getValue();
                }
            }
        }
        return null;
    }

    public static String getPersistenceId(Resource resource) {
        if (resource.hasDisk() && resource.getDisk().hasPersistence()) {
            return resource.getDisk().getPersistence().getId();
        }

        return null;
    }

    public static TaskInfo clearResourceIds(TaskInfo taskInfo) {
        List<Resource> clearedTaskResources = clearResourceIds(taskInfo.getResourcesList());
        TaskInfo.Builder taskInfoBuilder = TaskInfo.newBuilder(taskInfo)
                .clearResources()
                .addAllResources(clearedTaskResources);

        if (taskInfo.hasExecutor()) {
            taskInfoBuilder.setExecutor(clearResourceIds(taskInfo.getExecutor()));
        }

        return taskInfoBuilder.build();
    }

    public static ExecutorInfo clearResourceIds(ExecutorInfo executorInfo) {
        List<Resource> clearedResources = clearResourceIds(executorInfo.getResourcesList());
        return ExecutorInfo.newBuilder(executorInfo)
                .clearResources()
                .addAllResources(clearedResources)
                .build();
    }

    public static TaskInfo clearPersistence(TaskInfo taskInfo) {
        List<Protos.Resource> resources = new ArrayList<>();
        for (Protos.Resource resource : taskInfo.getResourcesList()) {
            if (resource.hasDisk()) {
                resource = Protos.Resource.newBuilder(resource).setDisk(
                    Protos.Resource.DiskInfo.newBuilder(resource.getDisk()).clearPersistence()
                ).build();
            }
            resources.add(resource);
        }
        return Protos.TaskInfo.newBuilder(taskInfo).clearResources().addAllResources(resources).build();
    }

    public static boolean areDifferent(
            ResourceSpecification oldResourceSpecification,
            ResourceSpecification newResourceSpecification) {

        Protos.Value oldValue = oldResourceSpecification.getValue();
        Protos.Value newValue = newResourceSpecification.getValue();
        if (!ValueUtils.equal(oldValue, newValue)) {
            LOGGER.info(String.format("Values '%s' and '%s' are different.", oldValue, newValue));
            return true;
        }

        String oldRole = oldResourceSpecification.getRole();
        String newRole = newResourceSpecification.getRole();
        if (!Objects.equals(oldRole, newRole)) {
            LOGGER.info(String.format("Roles '%s' and '%s' are different.", oldRole, newRole));
            return true;
        }

        String oldPrincipal = oldResourceSpecification.getPrincipal();
        String newPrincipal = newResourceSpecification.getPrincipal();
        if (!Objects.equals(oldPrincipal, newPrincipal)) {
            LOGGER.info(String.format("Principals '%s' and '%s' are different.", oldPrincipal, newPrincipal));
            return true;
        }

        return false;
    }

    public static Protos.Resource updateResource(Protos.Resource resource, ResourceSpecification resourceSpecification)
            throws IllegalArgumentException {
        Protos.Resource.Builder builder = Protos.Resource.newBuilder(resource);
        switch (resource.getType()) {
            case SCALAR:
                return builder.setScalar(resourceSpecification.getValue().getScalar()).build();
            case RANGES:
                return builder.setRanges(resourceSpecification.getValue().getRanges()).build();
            case SET:
                return builder.setSet(resourceSpecification.getValue().getSet()).build();
            default:
                throw new IllegalArgumentException("Unexpected Resource type encountered: " + resource.getType());
        }
    }

    private static List<Resource> clearResourceIds(List<Resource> resources) {
        List<Resource> clearedResources = new ArrayList<>();

        for (Resource resource : resources) {
            clearedResources.add(clearResourceId(resource));
        }

        return clearedResources;
    }

    private static Resource clearResourceId(Resource resource) {
        if (resource.hasReservation()) {
            List<Label> labels = resource.getReservation().getLabels().getLabelsList();

            Resource.Builder resourceBuilder = Resource.newBuilder(resource);
            Resource.ReservationInfo.Builder reservationBuilder = Resource.ReservationInfo
                    .newBuilder(resource.getReservation());

            Labels.Builder labelsBuilder = Labels.newBuilder();
            for (Label label : labels) {
                if (!label.getKey().equals(MesosResource.RESOURCE_ID_KEY)) {
                    labelsBuilder.addLabels(label);
                }
            }

            reservationBuilder.setLabels(labelsBuilder.build());
            resourceBuilder.setReservation(reservationBuilder.build());
            return resourceBuilder.build();
        } else {
            return resource;
        }
    }

    private static Resource setResource(Resource.Builder resBuilder, String name, Value value) {
        Value.Type type = value.getType();

        resBuilder
                .setName(name)
                .setType(type);

        switch (type) {
            case SCALAR:
                return resBuilder.setScalar(value.getScalar()).build();
            case RANGES:
                return resBuilder.setRanges(value.getRanges()).build();
            case SET:
                return resBuilder.setSet(value.getSet()).build();
            default:
                return null;
        }
    }

    private static ReservationInfo setResourceId(ReservationInfo resInfo, String resourceId) {
        return ReservationInfo.newBuilder(resInfo)
                .setLabels(setResourceId(resInfo.getLabels(), resourceId))
                .build();
    }

    public static Labels setResourceId(Labels labels, String resourceId) {
        Labels.Builder labelsBuilder = Labels.newBuilder();

        // Copy everything except blank resource ID label
        for (Label label : labels.getLabelsList()) {
            String key = label.getKey();
            String value = label.getValue();
            if (!key.equals(MesosResource.RESOURCE_ID_KEY)) {
                labelsBuilder.addLabels(Label.newBuilder()
                        .setKey(key)
                        .setValue(value)
                        .build());
            }
        }

        labelsBuilder.addLabels(Label.newBuilder()
                .setKey(MesosResource.RESOURCE_ID_KEY)
                .setValue(resourceId)
                .build());

        return labelsBuilder.build();
    }

    private static ReservationInfo getDesiredReservationInfo(String principal) {
        return getDesiredReservationInfo(principal, "");
    }

    private static ReservationInfo getDesiredReservationInfo(String principal, String reservationId) {
        return ReservationInfo.newBuilder()
                .setPrincipal(principal)
                .setLabels(getDesiredReservationLabels(reservationId))
                .build();
    }

    private static ReservationInfo getExpectedReservationInfo(String resourceId, String principal) {
        return ReservationInfo.newBuilder()
                .setPrincipal(principal)
                .setLabels(Labels.newBuilder()
                        .addLabels(Label.newBuilder()
                                .setKey(MesosResource.RESOURCE_ID_KEY)
                                .setValue(resourceId)
                                .build())
                        .build())
                .build();
    }

    private static Labels getDesiredReservationLabels(String resourceId) {
        return Labels.newBuilder()
                .addLabels(
                        Label.newBuilder()
                                .setKey(MesosResource.RESOURCE_ID_KEY)
                                .setValue(resourceId)
                                .build())
                .build();
    }

    private static DiskInfo getUnreservedMountVolumeDiskInfo(String mountRoot) {
        return DiskInfo.newBuilder()
                .setSource(Source.newBuilder()
                        .setType(Source.Type.MOUNT)
                        .setMount(Source.Mount.newBuilder()
                                .setRoot(mountRoot)
                                .build())
                        .build())
                .build();
    }

    private static DiskInfo getDesiredMountVolumeDiskInfo(String principal, String containerPath) {
        return DiskInfo.newBuilder()
                .setPersistence(Persistence.newBuilder()
                        .setId("")
                        .setPrincipal(principal)
                        .build())
                .setSource(getDesiredMountVolumeSource())
                .setVolume(Volume.newBuilder()
                        .setContainerPath(containerPath)
                        .setMode(Volume.Mode.RW)
                        .build())
                .build();
    }

    private static DiskInfo getExpectedMountVolumeDiskInfo(
            String mountRoot,
            String containerPath,
            String persistenceId,
            String principal) {
        return DiskInfo.newBuilder(getUnreservedMountVolumeDiskInfo(mountRoot))
                .setPersistence(Persistence.newBuilder()
                        .setId(persistenceId)
                        .setPrincipal(principal)
                        .build())
                .setVolume(Volume.newBuilder()
                        .setContainerPath(containerPath)
                        .setMode(Volume.Mode.RW)
                        .build())
                .build();
    }

    private static DiskInfo getDesiredRootVolumeDiskInfo(String principal, String containerPath) {
        return DiskInfo.newBuilder()
                .setPersistence(Persistence.newBuilder()
                        .setId("")
                        .setPrincipal(principal)
                        .build())
                .setVolume(Volume.newBuilder()
                        .setContainerPath(containerPath)
                        .setMode(Volume.Mode.RW)
                        .build())
                .build();
    }

    private static DiskInfo getExpectedRootVolumeDiskInfo(String persistenceId, String principal) {
        return DiskInfo.newBuilder()
                .setPersistence(Persistence.newBuilder()
                        .setId(persistenceId)
                        .setPrincipal(principal)
                        .build())
                .build();
    }

    private static DiskInfo.Source getDesiredMountVolumeSource() {
        return Source.newBuilder().setType(Source.Type.MOUNT).build();
    }
}
