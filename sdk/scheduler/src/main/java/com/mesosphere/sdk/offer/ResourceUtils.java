package com.mesosphere.sdk.offer;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.specification.ResourceSpec;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.*;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.Protos.Resource.DiskInfo.Persistence;
import org.apache.mesos.Protos.Resource.DiskInfo.Source;
import org.apache.mesos.Protos.Resource.ReservationInfo;
import org.apache.mesos.Protos.Value.Range;
import org.apache.mesos.Protos.Value.Ranges;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This class encapsulates common methods for manipulating Resources.
 */
public class ResourceUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceUtils.class);

    public static final String VIP_PREFIX = "VIP_";

    public static Resource getUnreservedResource(String name, Value value) {
        return setResource(Resource.newBuilder().setRole("*"), name, value);
    }

    public static Resource getDesiredResource(ResourceSpec resourceSpec) {
        return getDesiredResource(
                resourceSpec.getRole(),
                resourceSpec.getPrincipal(),
                resourceSpec.getName(),
                resourceSpec.getValue());
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

    public static TaskInfo.Builder addVIP(
            TaskInfo.Builder builder, String vipName, Integer vipPort, Resource resource) {
        if (builder.hasDiscovery()) {
            addVIP(builder.getDiscoveryBuilder(), vipName, vipPort, (int) resource.getRanges().getRange(0).getBegin());
        } else {
            builder.setDiscovery(getVIPDiscoveryInfo(builder.getName(), vipName, vipPort, resource));
        }

        return builder;
    }

    public static ExecutorInfo.Builder addVIP(
            ExecutorInfo.Builder builder, String vipName, Integer vipPort, Resource resource) {
        if (builder.hasDiscovery()) {
            addVIP(builder.getDiscoveryBuilder(), vipName, vipPort, (int) resource.getRanges().getRange(0).getBegin());
        } else {
            builder.setDiscovery(getVIPDiscoveryInfo(builder.getName(), vipName, vipPort, resource));
        }

        return builder;
    }

    private static DiscoveryInfo.Builder addVIP(
            DiscoveryInfo.Builder builder, String vipName, Integer vipPort, int destPort) {
        builder.getPortsBuilder()
                .addPortsBuilder()
                .setNumber(destPort)
                .setProtocol("tcp")
                .getLabelsBuilder()
                .addLabels(getVIPLabel(vipName, vipPort));

        return builder;
    }

    public static DiscoveryInfo getVIPDiscoveryInfo(String taskName, String vipName, Integer vipPort, Resource r) {
        DiscoveryInfo.Builder discoveryInfoBuilder = DiscoveryInfo.newBuilder()
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
                .setName(taskName);

        discoveryInfoBuilder.getPortsBuilder().addPortsBuilder()
                .setNumber((int) r.getRanges().getRange(0).getBegin())
                .setProtocol("tcp")
                .getLabelsBuilder()
                .addLabels(getVIPLabel(vipName, vipPort));

        return discoveryInfoBuilder.build();
    }

    public static Label getVIPLabel(String vipName, Integer vipPort) {
        return Label.newBuilder()
                .setKey(String.format("%s%s", VIP_PREFIX, UUID.randomUUID().toString()))
                .setValue(String.format("%s:%d", vipName, vipPort))
                .build();
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
            ResourceSpec oldResourceSpec,
            ResourceSpec newResourceSpec) {

        Protos.Value oldValue = oldResourceSpec.getValue();
        Protos.Value newValue = newResourceSpec.getValue();
        if (!ValueUtils.equal(oldValue, newValue)) {
            LOGGER.info(String.format("Values '%s' and '%s' are different.", oldValue, newValue));
            return true;
        }

        String oldRole = oldResourceSpec.getRole();
        String newRole = newResourceSpec.getRole();
        if (!Objects.equals(oldRole, newRole)) {
            LOGGER.info(String.format("Roles '%s' and '%s' are different.", oldRole, newRole));
            return true;
        }

        String oldPrincipal = oldResourceSpec.getPrincipal();
        String newPrincipal = newResourceSpec.getPrincipal();
        if (!Objects.equals(oldPrincipal, newPrincipal)) {
            LOGGER.info(String.format("Principals '%s' and '%s' are different.", oldPrincipal, newPrincipal));
            return true;
        }

        return false;
    }

    public static Protos.Resource updateResource(Protos.Resource resource, ResourceSpec resourceSpec)
            throws IllegalArgumentException {
        Protos.Resource.Builder builder = Protos.Resource.newBuilder(resource);
        switch (resource.getType()) {
            case SCALAR:
                return builder.setScalar(resourceSpec.getValue().getScalar()).build();
            case RANGES:
                return builder.setRanges(resourceSpec.getValue().getRanges()).build();
            case SET:
                return builder.setSet(resourceSpec.getValue().getSet()).build();
            default:
                throw new IllegalArgumentException("Unexpected Resource type encountered: " + resource.getType());
        }
    }

    /**
     * This method replaces the {@link Resource} on the {@link TaskInfo.Builder} with the supplied resource, if a
     * resource with that name already exists on that task. If it isn't, an {@link IllegalArgumentException} is thrown.
     * @param builder the task to install the resource on
     * @param resource the resource to install on the task
     * @return the supplied builder, modified to include the resource
     */
    public static TaskInfo.Builder setResource(TaskInfo.Builder builder, Resource resource) {
        for (int i = 0; i < builder.getResourcesCount(); ++i) {
            if (builder.getResources(i).getName().equals(resource.getName())) {
                builder.setResources(i, resource);
                return builder;
            }
        }

        throw new IllegalArgumentException(String.format(
                "Task has no resource with name '%s': %s",
                resource.getName(), TextFormat.shortDebugString(builder.build())));
    }

    /**
     * This method gets the {@link Resource} with the supplied resourceName from the supplied {@link TaskInfo}, throwing
     * an {@link IllegalArgumentException} if not found.
     * @param taskInfo the task info whose resource will be returned
     * @param resourceName the resourceName of the resource to return
     * @return the resource with the supplied resourceName
     */
    public static Resource getResource(TaskInfo taskInfo, String resourceName) {
        for (Resource r : taskInfo.getResourcesList()) {
            if (r.getName().equals(resourceName)) {
                return r;
            }
        }

        throw new IllegalArgumentException(
                String.format(
                        "Task has no resource with name '%s': %s",
                        resourceName, TextFormat.shortDebugString(taskInfo)));
    }

    public static ExecutorInfo.Builder setResource(ExecutorInfo.Builder builder, Resource resource) {
        for (int i = 0; i < builder.getResourcesCount(); ++i) {
            if (builder.getResources(i).getName().equals(resource.getName())) {
                builder.setResources(i, resource);
            }
        }

        throw new IllegalArgumentException(String.format(
                "Executor has no resource with name '%s': %s",
                resource.getName(), TextFormat.shortDebugString(builder.build())));
    }

    public static Resource getResource(ExecutorInfo executorInfo, String name) {
        for (Resource r : executorInfo.getResourcesList()) {
            if (r.getName().equals(name)) {
                return r;
            }
        }

        throw new IllegalArgumentException(String.format(
                "Executor has no resource with name '%s': %s",
                name, TextFormat.shortDebugString(executorInfo)));
    }

    public static Resource mergeRanges(Resource lhs, Resource rhs) {
        return lhs.toBuilder().setRanges(
                RangeAlgorithms.fromRangeList(RangeAlgorithms.mergeRanges(
                        lhs.getRanges().getRangeList(), rhs.getRanges().getRangeList()))).build();
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
