package com.mesosphere.sdk.testutils;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Labels;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Value;
import org.apache.mesos.Protos.Volume;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.Protos.Resource.ReservationInfo;
import org.apache.mesos.Protos.Resource.DiskInfo.Persistence;
import org.apache.mesos.Protos.Resource.DiskInfo.Source;
import org.apache.mesos.Protos.Value.Range;
import org.apache.mesos.Protos.Value.Ranges;

import com.mesosphere.sdk.offer.MesosResource;
import com.mesosphere.sdk.offer.ResourceCollectUtils;
import java.util.Arrays;
import java.util.List;

public class ResourceTestUtils {
    private static Resource getUnreservedResource(String name, Value value) {
        return setResource(Resource.newBuilder().setRole("*"), name, value);
    }

    private static Resource getUnreservedMountVolume(double diskSize, String mountRoot) {
        Value diskValue = Value.newBuilder()
                .setType(Value.Type.SCALAR)
                .setScalar(Value.Scalar.newBuilder().setValue(diskSize))
                .build();
        Resource.Builder resBuilder = Resource.newBuilder(getUnreservedResource("disk", diskValue));
        resBuilder.setRole("*");
        resBuilder.setDisk(getUnreservedMountVolumeDiskInfo(mountRoot));

        return resBuilder.build();
    }

    private static Resource getDesiredMountVolume(String role, String principal, double diskSize, String containerPath) {
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
        Resource.Builder resBuilder = Resource.newBuilder(getUnreservedResource("disk", diskValue));
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
        Resource.Builder resBuilder = Resource.newBuilder(getUnreservedResource("disk", diskValue));
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
            String containerPath,
            String role,
            String principal,
            String persistenceId) {
        Value diskValue = Value.newBuilder()
                .setType(Value.Type.SCALAR)
                .setScalar(Value.Scalar.newBuilder().setValue(diskSize))
                .build();
        Resource.Builder resBuilder = Resource.newBuilder(getUnreservedResource("disk", diskValue));
        resBuilder.setRole(role);
        resBuilder.setDisk(getExpectedRootVolumeDiskInfo(persistenceId, containerPath, principal));
        resBuilder.setReservation(getExpectedReservationInfo(resourceId, principal));

        return resBuilder.build();
    }

    private static Resource getExpectedResource(String role, String principal, String name, Value value) {
        return getExpectedResource(role, principal, name, value, "");
    }

    private static Resource getExpectedResource(String role,
                                               String principal,
                                               String name,
                                               Value value,
                                               String resourceId) {
        return Resource.newBuilder(getUnreservedResource(name, value))
                .setRole(role)
                .setReservation(getDesiredReservationInfo(principal, resourceId))
                .build();
    }

    public static Resource getUnreservedScalar(String name, double value) {
        Value val = Value.newBuilder()
                .setType(Value.Type.SCALAR)
                .setScalar(Value.Scalar.newBuilder().setValue(value))
                .build();
        Resource.Builder resBuilder = Resource.newBuilder(getUnreservedResource(name, val));
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
        Resource.Builder resBuilder = Resource.newBuilder(getUnreservedResource(name, val));
        resBuilder.setRole(role);
        resBuilder.setReservation(getExpectedReservationInfo(resourceId, principal));

        return resBuilder.build();
    }

    public static Resource getDesiredScalar(String role, String principal, String name, double value) {
        Value val = Value.newBuilder()
                .setType(Value.Type.SCALAR)
                .setScalar(Value.Scalar.newBuilder().setValue(value))
                .build();
        return getExpectedResource(role, principal, name, val);
    }

    public static Resource getUnreservedRanges(String name, List<Range> ranges) {
        Value val = Value.newBuilder()
                .setType(Value.Type.RANGES)
                .setRanges(Value.Ranges.newBuilder().addAllRange(ranges))
                .build();
        Resource.Builder resBuilder = Resource.newBuilder(getUnreservedResource(name, val));
        resBuilder.setRole("*");

        return resBuilder.build();
    }

    private static Resource getDesiredRanges(String role, String principal, String name, List<Range> ranges) {
        return getExpectedResource(
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
        Resource.Builder resBuilder = Resource.newBuilder(getUnreservedResource(name, val));
        resBuilder.setRole(role);
        resBuilder.setReservation(getExpectedReservationInfo(resourceId, principal));

        return resBuilder.build();
    }

    public static Resource setResourceId(Resource resource, String resourceId) {
        return Resource.newBuilder(resource)
                .setReservation(setResourceId(resource.getReservation(), resourceId))
                .build();
    }

    public static String getResourceId(Resource resource) {
        return ResourceCollectUtils.getResourceId(resource).orElse(null);
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

    private static Labels setResourceId(Labels labels, String resourceId) {
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

    private static DiskInfo getExpectedRootVolumeDiskInfo(
            String persistenceId,
            String containerPath,
            String principal) {
        return DiskInfo.newBuilder()
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

    private static DiskInfo.Source getDesiredMountVolumeSource() {
        return Source.newBuilder().setType(Source.Type.MOUNT).build();
    }

    public static Resource setLabel(Resource resource, String key, String value) {
        Resource.Builder builder = resource.toBuilder();
        builder.getReservationBuilder().getLabelsBuilder().addLabelsBuilder().setKey(key).setValue(value);

        return builder.build();
    }

    static String getLabel(Resource resource, String key) {
        for (Label l : resource.getReservation().getLabels().getLabelsList()) {
            if (l.getKey().equals(key)) {
                return l.getValue();
            }
        }

        return null;
    }

    static Resource removeLabel(Resource resource, String key) {
        Resource.Builder builder = resource.toBuilder();
        builder.getReservationBuilder().clearLabels();
        for (Label l : resource.getReservation().getLabels().getLabelsList()) {
            if (!l.getKey().equals(key)) {
                builder.getReservationBuilder().getLabelsBuilder().addLabels(l);
            }
        }

        return builder.build();
    }

    public static Resource getDesiredRootVolume(double diskSize) {
        return getDesiredRootVolume(
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                diskSize,
                TestConstants.CONTAINER_PATH);
    }

    public static Resource getDesiredMountVolume(double diskSize) {
        return getDesiredMountVolume(
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                diskSize,
                TestConstants.CONTAINER_PATH);
    }

    public static Resource getUnreservedMountVolume(double diskSize) {
        return getUnreservedMountVolume(diskSize, TestConstants.MOUNT_ROOT);
    }

    public static Resource getExpectedMountVolume(double diskSize) {
        return getExpectedMountVolume(diskSize, TestConstants.RESOURCE_ID);
    }

    public static Resource getExpectedMountVolume(double diskSize, String resourceId) {
        return getExpectedMountVolume(
                diskSize,
                resourceId,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                TestConstants.MOUNT_ROOT,
                TestConstants.CONTAINER_PATH,
                TestConstants.PERSISTENCE_ID);
    }

    public static Resource getExpectedRootVolume(double diskSize) {
        return getExpectedRootVolume(diskSize, TestConstants.RESOURCE_ID);
    }

    public static Resource getExpectedRootVolume(double diskSize, String resourceId) {
        return getExpectedRootVolume(
                diskSize,
                resourceId,
                TestConstants.CONTAINER_PATH,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                TestConstants.PERSISTENCE_ID);
    }

    private static Resource getDesiredScalar(String name, double value) {
        return getDesiredScalar(
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                name,
                value);
    }

    public static Resource getExpectedScalar(String name, double value, String resourceId) {
        return getExpectedScalar(
                name,
                value,
                resourceId,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);
    }

    public static final Resource getExpectedRanges(String name, long begin, long end, String resourceId) {
        return getExpectedRanges(
                name,
                Arrays.asList(Protos.Value.Range.newBuilder().setBegin(begin).setEnd(end).build()),
                resourceId,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);
    }

    public static final Resource getDesiredRanges(String name, long begin, long end) {
        return getDesiredRanges(
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                name,
                Arrays.asList(Protos.Value.Range.newBuilder().setBegin(begin).setEnd(end).build()));
    }

    public static Resource getUnreservedCpu(double cpus) {
        return getUnreservedScalar("cpus", cpus);
    }

    public static Resource getUnreservedMem(double mem) {
        return getUnreservedScalar("mem", mem);
    }

    public static Resource getUnreservedDisk(double disk) {
        return getUnreservedScalar("disk", disk);
    }

    public static Resource getUnreservedPorts(int begin, int end) {
        return getUnreservedRanges(
                "ports",
                Arrays.asList(Protos.Value.Range.newBuilder()
                        .setBegin(begin)
                        .setEnd(end)
                        .build()));
    }

    public static Resource getExpectedCpu(double cpus) {
        return ResourceTestUtils.getExpectedScalar("cpus", cpus, TestConstants.RESOURCE_ID);
    }

    public static Resource getDesiredCpu(double cpus) {
        return ResourceTestUtils.getDesiredScalar("cpus", cpus);
    }

    public static Resource getDesiredMem(double mem) {
        return ResourceTestUtils.getDesiredScalar("mem", mem);
    }

}
