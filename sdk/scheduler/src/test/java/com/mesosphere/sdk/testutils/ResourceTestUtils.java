package com.mesosphere.sdk.testutils;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.ResourceBuilder;
import com.mesosphere.sdk.specification.DefaultVolumeSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Volume;

import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.taskdata.AuxLabelAccess;

import java.util.Optional;

/**
 * Utility methods for creating {@link Protos.Resource} protobufs in tests.
 */
public class ResourceTestUtils {

    private ResourceTestUtils() {
        // do not instantiate
    }

    public static String getResourceId(Resource resource) {
        return ResourceUtils.getResourceId(resource).orElse(null);
    }

    public static String getPersistenceId(Resource diskResource) {
        return ResourceUtils.getPersistenceId(diskResource).get();
    }

    public static Protos.Resource getReservedMountVolume(double diskSize, Optional<String> profile) {
        return getReservedMountVolume(diskSize, profile, TestConstants.RESOURCE_ID, TestConstants.PERSISTENCE_ID);
    }

    public static Protos.Resource getReservedMountVolume(
            double diskSize,
            Optional<String> profile,
            String resourceId,
            String persistenceId) {
        Protos.Resource.Builder builder = getUnreservedMountVolume(diskSize, profile).toBuilder();
        builder.getDiskBuilder().getPersistenceBuilder()
                .setId(persistenceId)
                .setPrincipal(TestConstants.PRINCIPAL);
        builder.getDiskBuilder().getVolumeBuilder()
                .setContainerPath(TestConstants.CONTAINER_PATH)
                .setMode(Volume.Mode.RW);
        return addReservation(builder, resourceId).build();
    }

    public static Protos.Resource getReservedRootVolume(double diskSize) {
        return getReservedRootVolume(diskSize, TestConstants.RESOURCE_ID, TestConstants.PERSISTENCE_ID);
    }

    public static Protos.Resource getReservedRootVolume(double diskSize, String resourceId, String persistenceId) {
        VolumeSpec volumeSpec = DefaultVolumeSpec.createRootVolume(
                diskSize,
                TestConstants.CONTAINER_PATH,
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL);
        return ResourceBuilder.fromSpec(
                volumeSpec,
                Optional.of(resourceId),
                Optional.of(persistenceId),
                Optional.empty(),
                Optional.empty())
                .build();
    }

    public static Protos.Resource getReservedCpus(double value, String resourceId) {
        return addReservation(getUnreservedCpus(value).toBuilder(), resourceId).build();
    }

    public static Protos.Resource getReservedMem(double value, String resourceId) {
        return addReservation(getUnreservedMem(value).toBuilder(), resourceId).build();
    }

    public static Protos.Resource getReservedDisk(double value, String resourceId) {
        return addReservation(getUnreservedDisk(value).toBuilder(), resourceId).build();
    }

    public static Protos.Resource getReservedPorts(long begin, long end, String resourceId) {
        return addReservation(getUnreservedPorts(begin, end).toBuilder(), resourceId).build();
    }

    public static Protos.Resource getUnreservedCpus(double cpus, String preReservedRole) {
        return getUnreservedScalar(Constants.CPUS_RESOURCE_TYPE, cpus, preReservedRole);
    }

    public static Protos.Resource getUnreservedMem(double mem, String preReservedRole) {
        return getUnreservedScalar(Constants.MEMORY_RESOURCE_TYPE, mem, preReservedRole);
    }

    public static Protos.Resource getUnreservedDisk(double disk, String preReservedRole) {
        return getUnreservedScalar(Constants.DISK_RESOURCE_TYPE, disk, preReservedRole);
    }

    public static Protos.Resource getUnreservedCpus(double cpus) {
        return getUnreservedCpus(cpus, Constants.ANY_ROLE);
    }

    public static Protos.Resource getUnreservedMem(double mem) {
        return getUnreservedMem(mem, Constants.ANY_ROLE);
    }

    public static Protos.Resource getUnreservedDisk(double disk) {
        return getUnreservedDisk(disk, Constants.ANY_ROLE);
    }

    private static Protos.Resource getUnreservedScalar(String name, double value, String role) {
        Protos.Value.Builder builder = Protos.Value.newBuilder()
                .setType(Protos.Value.Type.SCALAR);
        builder.getScalarBuilder().setValue(value);
        return getUnreservedResource(name, builder.build(), role);
    }

    public static Protos.Resource getUnreservedMountVolume(double diskSize, Optional<String> profile) {
        Protos.Resource.Builder builder = getUnreservedDisk(diskSize).toBuilder();
        builder.getDiskBuilder().setSource(TestConstants.MOUNT_DISK_SOURCE);
        if (profile.isPresent()) {
            builder.getDiskBuilder().getSourceBuilder().setProfile(profile.get());
        }
        return builder.build();
    }

    public static Protos.Resource getUnreservedPorts(long begin, long end) {
        Protos.Value.Builder builder = Protos.Value.newBuilder()
                .setType(Protos.Value.Type.RANGES);
        builder.getRangesBuilder().addRangeBuilder()
                .setBegin(begin)
                .setEnd(end);
        return getUnreservedResource(Constants.PORTS_RESOURCE_TYPE, builder.build(), Constants.ANY_ROLE);
    }

    public static Protos.Resource getPrereservedPort(long begin, long end, String preReservedRole) {
        Protos.Value.Builder builder = Protos.Value.newBuilder()
            .setType(Protos.Value.Type.RANGES);
        builder.getRangesBuilder().addRangeBuilder()
            .setBegin(begin)
            .setEnd(end);
        return getUnreservedResource(Constants.PORTS_RESOURCE_TYPE, builder.build(), preReservedRole);
    }

    @SuppressWarnings("deprecation") // for Resource.setRole()
    private static Protos.Resource.Builder addReservation(
            Protos.Resource.Builder builder, String resourceId) {
        Protos.Resource.ReservationInfo.Builder reservationBuilder;
        if (Capabilities.getInstance().supportsPreReservedResources()) {
            reservationBuilder = builder.addReservationsBuilder()
                    .setRole(TestConstants.ROLE)
                    .setPrincipal(TestConstants.PRINCIPAL);
        } else {
            builder.setRole(TestConstants.ROLE);
            reservationBuilder = builder.getReservationBuilder()
                    .setPrincipal(TestConstants.PRINCIPAL);
        }
        AuxLabelAccess.setResourceId(reservationBuilder, resourceId);
        AuxLabelAccess.setResourceNamespace(reservationBuilder, TestConstants.SERVICE_NAME);
        return builder;
    }

    @SuppressWarnings("deprecation") // for Resource.setRole()
    private static Protos.Resource getUnreservedResource(String name, Protos.Value value, String role) {
        Protos.Resource.Builder resBuilder = Protos.Resource.newBuilder()
                .setName(name)
                .setType(value.getType())
                .setRole(role);
        if (!role.equals(Constants.ANY_ROLE)) {
            // Fill in the prereserved role info:
            resBuilder.addReservationsBuilder()
                    .setRole(role)
                    .setPrincipal(TestConstants.PRINCIPAL);
        }
        switch (value.getType()) {
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
}
