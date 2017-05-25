package com.mesosphere.sdk.testutils;

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

import com.mesosphere.sdk.offer.MesosResource;
import com.mesosphere.sdk.offer.ResourceCollectionUtils;
import java.util.Arrays;
import java.util.List;

/**
 * Utility methods for creating {@link Resource} protobufs in tests.
 */
public class ResourceTestUtils {

    private ResourceTestUtils() {
        // do not instantiate
    }

    private static Resource getUnreservedResource(String name, Value value) {
        Value.Type type = value.getType();
        Resource.Builder resBuilder = Resource.newBuilder()
                .setRole("*")
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
        DiskInfo mountVolumeDiskInfo = DiskInfo.newBuilder(getUnreservedMountVolumeDiskInfo(mountRoot))
                .setPersistence(Persistence.newBuilder()
                        .setId(persistenceId)
                        .setPrincipal(principal)
                        .build())
                .setVolume(Volume.newBuilder()
                        .setContainerPath(containerPath)
                        .setMode(Volume.Mode.RW)
                        .build())
                .build();
        return Resource.newBuilder(getUnreservedResource("disk", diskValue))
                .setRole(role)
                .setDisk(mountVolumeDiskInfo)
                .setReservation(getExpectedReservationInfo(resourceId, principal))
                .build();
    }

    public static Resource getUnreservedRootVolume(double diskSize) {
        Value diskValue = Value.newBuilder()
                .setType(Value.Type.SCALAR)
                .setScalar(Value.Scalar.newBuilder().setValue(diskSize))
                .build();
        return Resource.newBuilder(getUnreservedResource("disk", diskValue))
                .setRole("*")
                .build();
    }

    public static Resource getDesiredRootVolume(String role, String principal, double diskSize, String containerPath) {
        Value diskValue = Value.newBuilder()
                .setType(Value.Type.SCALAR)
                .setScalar(Value.Scalar.newBuilder().setValue(diskSize))
                .build();
        DiskInfo rootVolumeDiskInfo = DiskInfo.newBuilder()
                .setPersistence(Persistence.newBuilder()
                        .setId("")
                        .setPrincipal(principal)
                        .build())
                .setVolume(Volume.newBuilder()
                        .setContainerPath(containerPath)
                        .setMode(Volume.Mode.RW)
                        .build())
                .build();
        return Resource.newBuilder(getUnreservedResource("disk", diskValue))
                .setRole(role)
                .setReservation(getExpectedReservationInfo("", principal))
                .setDisk(rootVolumeDiskInfo)
                .build();
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
        DiskInfo rootVolumeDiskInfo = DiskInfo.newBuilder()
                .setPersistence(Persistence.newBuilder()
                        .setId(persistenceId)
                        .setPrincipal(principal)
                        .build())
                .setVolume(Volume.newBuilder()
                        .setContainerPath(containerPath)
                        .setMode(Volume.Mode.RW)
                        .build())
                .build();
        return Resource.newBuilder(getUnreservedResource("disk", diskValue))
                .setRole(role)
                .setDisk(rootVolumeDiskInfo)
                .setReservation(getExpectedReservationInfo(resourceId, principal))
                .build();
    }

    private static Resource getExpectedResource(String role, String principal, String name, Value value) {
        return Resource.newBuilder(getUnreservedResource(name, value))
                .setRole(role)
                .setReservation(getExpectedReservationInfo("", principal))
                .build();
    }

    public static Resource getUnreservedScalar(String name, double value) {
        Value val = Value.newBuilder()
                .setType(Value.Type.SCALAR)
                .setScalar(Value.Scalar.newBuilder().setValue(value))
                .build();
        return Resource.newBuilder(getUnreservedResource(name, val))
                .setRole("*")
                .build();
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
        return Resource.newBuilder(getUnreservedResource(name, val))
                .setRole(role)
                .setReservation(getExpectedReservationInfo(resourceId, principal))
                .build();
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
        return Resource.newBuilder(getUnreservedResource(name, val))
                .setRole("*")
                .build();
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
        return Resource.newBuilder(getUnreservedResource(name, val))
                .setRole(role)
                .setReservation(getExpectedReservationInfo(resourceId, principal))
                .build();
    }

    public static Resource setResourceId(Resource resource, String resourceId) {
        Resource.Builder builder = Resource.newBuilder(resource);
        Labels.Builder newLabels = Labels.newBuilder();
        for (Label label : builder.getReservationBuilder().getLabels().getLabelsList()) {
            if (!label.getKey().equals(MesosResource.RESOURCE_ID_KEY)) {
                newLabels.addLabels(label);
            }
        }
        newLabels.addLabelsBuilder()
                .setKey(MesosResource.RESOURCE_ID_KEY)
                .setValue(resourceId);
        builder.getReservationBuilder().setLabels(newLabels);
        return builder.build();
    }

    public static String getResourceId(Resource resource) {
        return ResourceCollectionUtils.getResourceId(resource).orElse(null);
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
        Value diskValue = Value.newBuilder()
                .setType(Value.Type.SCALAR)
                .setScalar(Value.Scalar.newBuilder().setValue(diskSize))
                .build();
        DiskInfo mountVolumeDiskInfo = DiskInfo.newBuilder()
                .setPersistence(Persistence.newBuilder()
                        .setId("")
                        .setPrincipal(TestConstants.PRINCIPAL)
                        .build())
                .setSource(Source.newBuilder().setType(Source.Type.MOUNT).build())
                .setVolume(Volume.newBuilder()
                        .setContainerPath(TestConstants.CONTAINER_PATH)
                        .setMode(Volume.Mode.RW)
                        .build())
                .build();
        return Resource.newBuilder(getUnreservedResource("disk", diskValue))
                .setRole(TestConstants.ROLE)
                .setReservation(getExpectedReservationInfo("", TestConstants.PRINCIPAL))
                .setDisk(mountVolumeDiskInfo)
                .build();
    }

    public static Resource getUnreservedMountVolume(double diskSize) {
        Value diskValue = Value.newBuilder()
                .setType(Value.Type.SCALAR)
                .setScalar(Value.Scalar.newBuilder().setValue(diskSize))
                .build();
        return Resource.newBuilder(getUnreservedResource("disk", diskValue))
                .setRole("*")
                .setDisk(getUnreservedMountVolumeDiskInfo(TestConstants.MOUNT_ROOT))
                .build();
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
                Arrays.asList(Value.Range.newBuilder().setBegin(begin).setEnd(end).build()),
                resourceId,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);
    }

    public static final Resource getDesiredRanges(String name, long begin, long end) {
        Value.Builder valueBuilder = Value.newBuilder()
                .setType(Value.Type.RANGES);
        valueBuilder.getRangesBuilder().addRangeBuilder()
                .setBegin(begin)
                .setEnd(end);
        return getExpectedResource(
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                name,
                valueBuilder.build());
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
                Arrays.asList(Value.Range.newBuilder()
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
