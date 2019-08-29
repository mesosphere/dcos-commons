package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.dcos.ResourceRefinementCapabilityContext;
import com.mesosphere.sdk.offer.taskdata.AuxLabelAccess;
import com.mesosphere.sdk.specification.DefaultResourceSpec;
import com.mesosphere.sdk.specification.DefaultVolumeSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import com.mesosphere.sdk.testutils.DefaultCapabilitiesTestSuite;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

/**
 * Test construction of Resource protobufs.
 */
@SuppressWarnings("deprecation")
public class ResourceBuilderTest extends DefaultCapabilitiesTestSuite {
    /*
        name: "cpus"
        type: SCALAR
        scalar {
            value: 1.0
        }
        role: "*"
    */
    private static final Protos.Value VALUE = Protos.Value.newBuilder()
            .setType(Protos.Value.Type.SCALAR)
            .setScalar(Protos.Value.Scalar.newBuilder().setValue(1.0))
            .build();

    @SuppressWarnings("deprecation")
    @Test
    public void testUnreservedResource() {
        ResourceBuilder resourceBuilder = ResourceBuilder.fromUnreservedValue("cpus", VALUE);
        Protos.Resource resource = resourceBuilder.build();
        Assert.assertEquals("cpus", resource.getName());
        Assert.assertEquals(Protos.Value.Type.SCALAR, resource.getType());
        Assert.assertEquals(1.0, resource.getScalar().getValue(), 0.0);
        Assert.assertEquals(Constants.ANY_ROLE, resource.getRole());
        Assert.assertFalse(resource.hasReservation());
        Assert.assertEquals(0, resource.getReservationsCount());
    }

    /*
        name: "cpus"
        type: SCALAR
        scalar {
          value: 1.0
        }
        role: "*"
        reservations {
          principal: "test-principal"
          labels {
            labels {
              key: "resource_id"
              value: "e9edd178-f7dd-4472-b58b-3a3ff7ed51ac"
            }
          }
          role: "test-role"
        }
    */
    @Test
    public void testNewFromResourceSpec() {
        testNewFromResourceSpec(Optional.empty());
        testNewFromResourceSpec(Optional.of("/path/to/namespace"));

        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testNewFromResourceSpec(Optional.empty());
            testNewFromResourceSpec(Optional.of("/path/to/namespace"));
        } finally {
            context.reset();
        }
    }

    private static void testNewFromResourceSpec(Optional<String> namespace) {
        ResourceSpec resourceSpec = DefaultResourceSpec.newBuilder()
                .name("cpus")
                .value(VALUE)
                .role(TestConstants.ROLE)
                .preReservedRole(Constants.ANY_ROLE)
                .principal(TestConstants.PRINCIPAL)
                .build();
        ResourceBuilder resourceBuilder = ResourceBuilder.fromSpec(resourceSpec, Optional.empty());

        Protos.Resource resource = resourceBuilder.build();
        validateScalarResource(resource, Optional.empty());
    }

    @Test
    public void testRefineStaticResource() {
        testRefineStaticResource(Optional.empty());
        testRefineStaticResource(Optional.of("foo"));
    }

    /*
        name: "cpus"
        type: SCALAR
        scalar {
          value: 1.0
        }
        reservations {
          role: "base-role"
          type: STATIC
        }
        reservations {
          principal: "test-principal"
          labels {
            labels {
              key: "resource_id"
              value: "a395f14b-3cc8-4009-9dc4-51838b423aed"
            }
          }
          role: "test-role"
          type: DYNAMIC
        }
    */
    private static void testRefineStaticResource(Optional<String> namespace) {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            ResourceSpec resourceSpec = DefaultResourceSpec.newBuilder()
                    .name("cpus")
                    .value(VALUE)
                    .role(TestConstants.ROLE)
                    .preReservedRole(TestConstants.PRE_RESERVED_ROLE)
                    .principal(TestConstants.PRINCIPAL)
                    .build();
            ResourceBuilder resourceBuilder = ResourceBuilder.fromSpec(resourceSpec, Optional.empty());

            Protos.Resource resource = resourceBuilder.build();
            Assert.assertEquals(2, resource.getReservationsCount());
            validateScalarResourceRefined(resource, Optional.empty());
            Assert.assertEquals(Protos.Resource.ReservationInfo.Type.STATIC, resource.getReservations(0).getType());
            Assert.assertEquals(TestConstants.PRE_RESERVED_ROLE, resource.getReservations(0).getRole());
        } finally {
            context.reset();
        }
    }

    @Test
    public void testExistingFromResourceSpec() {
        testExistingFromResourceSpec(Optional.empty());
        testExistingFromResourceSpec(Optional.of("bar"));

        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testExistingFromResourceSpec(Optional.empty());
            testExistingFromResourceSpec(Optional.of("bar"));
        } finally {
            context.reset();
        }
    }

    private static void testExistingFromResourceSpec(Optional<String> namespace) {
        Optional<String> resourceId = Optional.of(UUID.randomUUID().toString());
        ResourceSpec resourceSpec = DefaultResourceSpec.newBuilder()
                .name("cpus")
                .value(VALUE)
                .role(TestConstants.ROLE)
                .preReservedRole(Constants.ANY_ROLE)
                .principal(TestConstants.PRINCIPAL)
                .build();
        validateScalarResource(
                ResourceBuilder.fromSpec(resourceSpec, resourceId).build(), resourceId);
    }

    /*
        name: "disk"
        type: SCALAR
        scalar {
          value: 10.0
        }
        role: "*"
        disk {
          persistence {
            id: "57bbc7c5-aba7-4508-94a0-3ad714c5e295"
            principal: "test-principal"
          }
          volume {
            container_path: "test-container-path"
            mode: RW
          }
        }
        reservations {
          principal: "test-principal"
          labels {
            labels {
              key: "resource_id"
              value: "0457e8d3-a892-48ed-b845-d38488876592"
            }
          }
          role: "test-role"
        }
    */
    @Test
    public void testNewFromRootVolumeSpec() {
        VolumeSpec volumeSpec = DefaultVolumeSpec.createRootVolume(
                10,
                TestConstants.CONTAINER_PATH,
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL);
        ResourceBuilder resourceBuilder = ResourceBuilder.fromSpec(
                volumeSpec,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());

        Protos.Resource resource = resourceBuilder.build();
        validateDisk(resource, Optional.empty());
    }

    @Test
    public void testExistingFromRootVolumeSpec() {
        VolumeSpec volumeSpec = DefaultVolumeSpec.createRootVolume(
                10,
                TestConstants.CONTAINER_PATH,
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL);
        Optional<String> resourceId = Optional.of(UUID.randomUUID().toString());
        Optional<String> persistenceId = Optional.of(UUID.randomUUID().toString());
        ResourceBuilder resourceBuilder = ResourceBuilder.fromSpec(
                volumeSpec,
                resourceId,
                persistenceId,
                Optional.empty(),
                Optional.empty());

        Protos.Resource resource = resourceBuilder.build();
        validateDisk(resource, resourceId);
        Assert.assertEquals(persistenceId.get(), resource.getDisk().getPersistence().getId());
    }

    /*
        name: "disk"
        type: SCALAR
        scalar {
          value: 10.0
        }
        role: "*"
        disk {
          persistence {
            id: "31374544-3579-44eb-a88e-23b232b15a7a"
            principal: "test-principal"
          }
          volume {
            container_path: "test-container-path"
            mode: RW
          }
          source {
            type: MOUNT
            mount {
              root: "/mnt/source"
            }
          }
        }
        reservations {
          principal: "test-principal"
          labels {
            labels {
              key: "resource_id"
              value: "9d400c17-ec13-4236-9453-d5642b2884c5"
            }
          }
          role: "test-role"
        }
    */
    @Test
    public void testNewFromMountVolumeSpec() {
        VolumeSpec volumeSpec = DefaultVolumeSpec.createMountVolume(
                10,
                TestConstants.CONTAINER_PATH,
                Collections.emptyList(),
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL);
        ResourceBuilder resourceBuilder = ResourceBuilder.fromSpec(
                volumeSpec,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(TestConstants.MOUNT_DISK_SOURCE));

        Protos.Resource resource = resourceBuilder.build();
        validateDisk(resource, Optional.empty());

        Protos.Resource.DiskInfo diskInfo = resource.getDisk();
        Assert.assertTrue(diskInfo.hasSource());
        Assert.assertEquals(TestConstants.MOUNT_DISK_SOURCE, diskInfo.getSource());
    }

    @Test
    public void testExistingFromMountVolumeSpec() {
        VolumeSpec volumeSpec = DefaultVolumeSpec.createMountVolume(
                10,
                TestConstants.CONTAINER_PATH,
                Collections.emptyList(),
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL);
        Optional<String> resourceId = Optional.of(UUID.randomUUID().toString());
        Optional<String> persistenceId = Optional.of(UUID.randomUUID().toString());
        ResourceBuilder resourceBuilder = ResourceBuilder.fromSpec(
                volumeSpec,
                resourceId,
                persistenceId,
                Optional.empty(),
                Optional.of(TestConstants.MOUNT_DISK_SOURCE));

        Protos.Resource resource = resourceBuilder.build();
        validateDisk(resource, resourceId);

        Protos.Resource.DiskInfo diskInfo = resource.getDisk();
        Assert.assertTrue(diskInfo.hasSource());
        Assert.assertEquals(TestConstants.MOUNT_DISK_SOURCE, diskInfo.getSource());

        Assert.assertEquals(persistenceId.get(), resource.getDisk().getPersistence().getId());
    }

    @Test
    public void testFromExistingScalarResource() {
        testFromExistingScalarResource(Optional.empty());
        testFromExistingScalarResource(Optional.of("baz"));

        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testFromExistingScalarResource(Optional.empty());
            testFromExistingScalarResource(Optional.of("baz"));
        } finally {
            context.reset();
        }
    }

    private static void testFromExistingScalarResource(Optional<String> namespace) {
        ResourceSpec resourceSpec = DefaultResourceSpec.newBuilder()
                .name("cpus")
                .value(VALUE)
                .role(TestConstants.ROLE)
                .preReservedRole(Constants.ANY_ROLE)
                .principal(TestConstants.PRINCIPAL)
                .build();
        Optional<String> resourceId = Optional.of(UUID.randomUUID().toString());
        Protos.Resource originalResource = ResourceBuilder.fromSpec(resourceSpec, resourceId).build();
        Protos.Resource reconstructedResource = ResourceBuilder.fromExistingResource(originalResource).build();

        Assert.assertEquals(originalResource, reconstructedResource);
    }

    @Test
    public void testFromExistingRootVolume() {
        VolumeSpec volumeSpec = DefaultVolumeSpec.createRootVolume(
                10,
                TestConstants.CONTAINER_PATH,
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL);
        Optional<String> resourceId = Optional.of(UUID.randomUUID().toString());
        Optional<String> persistenceId = Optional.of(UUID.randomUUID().toString());
        Protos.Resource originalResource = ResourceBuilder.fromSpec(
                volumeSpec,
                resourceId,
                persistenceId,
                Optional.empty(),
                Optional.empty())
                .build();
        Protos.Resource reconstructedResource = ResourceBuilder.fromExistingResource(originalResource).build();

        Assert.assertEquals(originalResource, reconstructedResource);
    }

    @Test
    public void testFromExistingMountVolume() {
        VolumeSpec volumeSpec = DefaultVolumeSpec.createMountVolume(
                10,
                TestConstants.CONTAINER_PATH,
                Collections.emptyList(),
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL);
        Optional<String> resourceId = Optional.of(UUID.randomUUID().toString());
        Optional<String> persistenceId = Optional.of(UUID.randomUUID().toString());
        Protos.Resource originalResource = ResourceBuilder.fromSpec(
                volumeSpec,
                resourceId,
                persistenceId,
                Optional.empty(),
                Optional.of(TestConstants.MOUNT_DISK_SOURCE))
                .build();
        Protos.Resource reconstructedResource = ResourceBuilder.fromExistingResource(originalResource).build();

        Assert.assertEquals(originalResource, reconstructedResource);
    }

    private static void validateDisk(Protos.Resource resource, Optional<String> resourceId) {
        Assert.assertTrue(resource.hasDisk());

        Protos.Resource.DiskInfo diskInfo = resource.getDisk();
        Assert.assertTrue(diskInfo.hasPersistence());

        Protos.Resource.DiskInfo.Persistence persistence = diskInfo.getPersistence();
        Assert.assertEquals(36, persistence.getId().length());
        Assert.assertEquals(TestConstants.PRINCIPAL, persistence.getPrincipal());

        Assert.assertTrue(diskInfo.hasVolume());
        Protos.Volume volume = diskInfo.getVolume();
        Assert.assertEquals(TestConstants.CONTAINER_PATH, volume.getContainerPath());
        Assert.assertEquals(Protos.Volume.Mode.RW, volume.getMode());

        validateScalarResource(resource, resourceId);
    }

    private static void validateScalarResource(
            Protos.Resource resource, Optional<String> resourceId) {
        if (Capabilities.getInstance().supportsPreReservedResources()) {
            validateScalarResourceRefined(resource, resourceId);
        } else {
            validateScalarResourceLegacy(resource, resourceId);
        }
    }

    @SuppressWarnings("deprecation")
    private static void validateScalarResourceRefined(
            Protos.Resource resource, Optional<String> resourceId) {
        Assert.assertEquals(Protos.Value.Type.SCALAR, resource.getType());
        Assert.assertEquals(Constants.ANY_ROLE, resource.getRole());
        Assert.assertFalse(resource.hasReservation());

        Protos.Resource.ReservationInfo reservationInfo = resource.getReservations(resource.getReservationsCount() - 1);
        Assert.assertEquals(TestConstants.PRINCIPAL, reservationInfo.getPrincipal());
        Assert.assertEquals(TestConstants.ROLE, reservationInfo.getRole());

        validateLabels(reservationInfo, resourceId);
    }

    @SuppressWarnings("deprecation")
    private static void validateScalarResourceLegacy(Protos.Resource resource, Optional<String> resourceId) {
        Assert.assertEquals(Protos.Value.Type.SCALAR, resource.getType());
        Assert.assertEquals(TestConstants.ROLE, resource.getRole());
        Assert.assertTrue(resource.hasReservation());
        Assert.assertEquals(0, resource.getReservationsCount());

        Protos.Resource.ReservationInfo reservationInfo = resource.getReservation();
        Assert.assertEquals(TestConstants.PRINCIPAL, reservationInfo.getPrincipal());
        Assert.assertFalse(reservationInfo.hasRole());

        validateLabels(reservationInfo, resourceId);
    }

    private static void validateLabels(
            Protos.Resource.ReservationInfo reservationInfo, Optional<String> resourceId) {
        if (resourceId.isPresent()) {
            Assert.assertEquals(resourceId.get(), AuxLabelAccess.getResourceId(reservationInfo).get());
        } else {
            Assert.assertEquals(36, AuxLabelAccess.getResourceId(reservationInfo).get().length());
        }
        Assert.assertEquals(1, reservationInfo.getLabels().getLabelsCount());
    }
}
