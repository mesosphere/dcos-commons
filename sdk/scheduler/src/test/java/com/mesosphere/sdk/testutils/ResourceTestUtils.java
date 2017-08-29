package com.mesosphere.sdk.testutils;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.ResourceBuilder;
import com.mesosphere.sdk.specification.DefaultVolumeSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Value;
import org.apache.mesos.Protos.Volume;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.Protos.Resource.DiskInfo.Persistence;
import org.apache.mesos.Protos.Resource.DiskInfo.Source;
import org.apache.mesos.Protos.Value.Range;

import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.taskdata.AuxLabelAccess;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
        return addReservation(
                Resource.newBuilder(getUnreservedResource("disk", diskValue)).setDisk(mountVolumeDiskInfo),
                resourceId,
                role,
                principal).build();
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

    public static Resource getExpectedRootVolume(
            double diskSize,
            String resourceId,
            String containerPath,
            String role,
            String principal,
            String persistenceId) {
        VolumeSpec volumeSpec = new DefaultVolumeSpec(
                diskSize,
                VolumeSpec.Type.ROOT,
                containerPath,
                role,
                Constants.ANY_ROLE,
                principal);
        return ResourceBuilder.fromSpec(
                volumeSpec,
                Optional.of(resourceId),
                Optional.of(persistenceId),
                Optional.empty())
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
        return addReservation(
                Resource.newBuilder(getUnreservedResource(name, val)),
                resourceId,
                role,
                principal).build();
    }

    public static Resource setResourceId(Resource resource, String resourceId) {
        Resource.Builder resourceBuilder = resource.toBuilder();
        AuxLabelAccess.setResourceId(resourceBuilder.getReservationBuilder(), resourceId);
        return resourceBuilder.build();
    }

    public static String getResourceId(Resource resource) {
        return ResourceUtils.getResourceId(resource).orElse(null);
    }

    public static String getPersistenceId(Resource diskResource) {
        return ResourceUtils.getPersistenceId(diskResource).get();
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
        return getExpectedMountVolume(diskSize, TestConstants.RESOURCE_ID, TestConstants.PERSISTENCE_ID);
    }

    public static Resource getExpectedMountVolume(double diskSize, String resourceId, String persistenceId) {
        return getExpectedMountVolume(
                diskSize,
                resourceId,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                TestConstants.MOUNT_ROOT,
                TestConstants.CONTAINER_PATH,
                persistenceId);
    }

    public static Resource getExpectedRootVolume(double diskSize) {
        return getExpectedRootVolume(diskSize, TestConstants.RESOURCE_ID);
    }

    public static Resource getExpectedRootVolume(double diskSize, String resourceId) {
        return getExpectedRootVolume(diskSize, resourceId, TestConstants.PERSISTENCE_ID);
    }

    public static Resource getExpectedRootVolume(double diskSize, String resourceId, String persistenceId) {
        return getExpectedRootVolume(
                diskSize,
                resourceId,
                TestConstants.CONTAINER_PATH,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                persistenceId);
    }

    public static Resource getExpectedScalar(String name, double value, String resourceId) {
        return getExpectedScalar(
                name,
                value,
                resourceId,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);
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
        return addReservation(
                Resource.newBuilder(getUnreservedResource(name, val)),
                resourceId,
                role,
                principal).build();
    }

    private static Resource.Builder addReservation(
            Resource.Builder builder,
            String resourceId,
            String role,
            String principal) {
        if (Capabilities.getInstance().supportsPreReservedResources()) {
            Resource.ReservationInfo.Builder reservationBuilder = builder.addReservationsBuilder()
                    .setRole(role)
                    .setPrincipal(principal);
            AuxLabelAccess.setResourceId(reservationBuilder, resourceId);
        } else {
            builder.setRole(role);
            Resource.ReservationInfo.Builder reservationBuilder = builder.getReservationBuilder()
                    .setPrincipal(principal);
            AuxLabelAccess.setResourceId(reservationBuilder, resourceId);
        }

        return  builder;
    }

    public static final Resource getExpectedRanges(String name, long begin, long end, String resourceId) {
        return getExpectedRanges(
                name,
                Arrays.asList(Value.Range.newBuilder().setBegin(begin).setEnd(end).build()),
                resourceId,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);
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
}
