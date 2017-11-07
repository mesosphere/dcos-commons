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
    private Protos.Value value = Protos.Value.newBuilder()
            .setType(Protos.Value.Type.SCALAR)
            .setScalar(Protos.Value.Scalar.newBuilder().setValue(1.0))
            .build();
    @Test
    public void testUnreservedResource() {
        ResourceBuilder resourceBuilder = ResourceBuilder.fromUnreservedValue("cpus", value);
        Protos.Resource resource = resourceBuilder.build();
        Assert.assertEquals("cpus", resource.getName());
        Assert.assertEquals(Protos.Value.Type.SCALAR, resource.getType());
        Assert.assertEquals(1.0, resource.getScalar().getValue(), 0.0);
        Assert.assertEquals(Constants.ANY_ROLE, resource.getRole());
        Assert.assertFalse(resource.hasReservation());
        Assert.assertEquals(0, resource.getReservationsCount());
    }

    @Test
    public void testNewFromResourceSpec() {
        ResourceSpec resourceSpec = new DefaultResourceSpec(
                "cpus",
                value,
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL);
        ResourceBuilder resourceBuilder = ResourceBuilder.fromSpec(resourceSpec, Optional.empty());

        Protos.Resource resource = resourceBuilder.build();
        validateScalarResoure(resource);
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
    public void testNewFromResourceSpecRefined() {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testNewFromResourceSpec();
        } finally {
            context.reset();
        }
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
    @Test
    public void testRefineStaticResource() {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            ResourceSpec resourceSpec = new DefaultResourceSpec(
                    "cpus",
                    value,
                    TestConstants.ROLE,
                    TestConstants.PRE_RESERVED_ROLE,
                    TestConstants.PRINCIPAL);
            ResourceBuilder resourceBuilder = ResourceBuilder.fromSpec(resourceSpec, Optional.empty());

            Protos.Resource resource = resourceBuilder.build();
            Assert.assertEquals(2, resource.getReservationsCount());
            validateScalarResourceRefined(resource);
            Assert.assertEquals(Protos.Resource.ReservationInfo.Type.STATIC, resource.getReservations(0).getType());
            Assert.assertEquals(TestConstants.PRE_RESERVED_ROLE, resource.getReservations(0).getRole());
        } finally {
            context.reset();
        }
    }

    @Test
    public void testExistingFromResourceSpec() {
        Optional<String> resourceId = Optional.of(UUID.randomUUID().toString());
        ResourceSpec resourceSpec = new DefaultResourceSpec(
                "cpus",
                value,
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL);
        ResourceBuilder resourceBuilder = ResourceBuilder.fromSpec(resourceSpec, resourceId);

        Protos.Resource resource = resourceBuilder.build();
        validateScalarResoure(resource);

        Protos.Resource.ReservationInfo reservationInfo = Capabilities.getInstance().supportsPreReservedResources() ?
                resource.getReservations(0) :
                resource.getReservation();
        Protos.Label label = reservationInfo.getLabels().getLabels(0);
        Assert.assertEquals(resourceId.get(), label.getValue());
    }

    @Test
    public void testExistingFromResourceSpecRefined() {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testExistingFromResourceSpec();
        } finally {
            context.reset();
        }
    }

    @Test
    public void testNewFromRootVolumeSpec() {
        VolumeSpec volumeSpec = new DefaultVolumeSpec(
                10,
                VolumeSpec.Type.ROOT,
                TestConstants.CONTAINER_PATH,
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL);
        ResourceBuilder resourceBuilder = ResourceBuilder.fromSpec(
                volumeSpec,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());

        Protos.Resource resource = resourceBuilder.build();
        validateScalarResoure(resource);
        validateDisk(resource);
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
    public void testNewFromRootVolumeSpecRefined() {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testNewFromRootVolumeSpec();
        } finally {
            context.reset();
        }
    }

    @Test
    public void testExistingFromRootVolumeSpec() {
        VolumeSpec volumeSpec = new DefaultVolumeSpec(
                10,
                VolumeSpec.Type.ROOT,
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
                Optional.empty());

        Protos.Resource resource = resourceBuilder.build();
        validateScalarResoure(resource);
        validateDisk(resource);

        Protos.Resource.ReservationInfo reservationInfo = Capabilities.getInstance().supportsPreReservedResources() ?
                resource.getReservations(0) :
                resource.getReservation();
        Protos.Label label = reservationInfo.getLabels().getLabels(0);
        Assert.assertEquals(resourceId.get(), label.getValue());
        Assert.assertEquals(persistenceId.get(), resource.getDisk().getPersistence().getId());
    }

    @Test
    public void testExistingFromRootVolumeSpecRefined() {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testExistingFromRootVolumeSpec();
        } finally {
            context.reset();
        }
    }

    @Test
    public void testNewFromMountVolumeSpec() {
        VolumeSpec volumeSpec = new DefaultVolumeSpec(
                10,
                VolumeSpec.Type.MOUNT,
                TestConstants.CONTAINER_PATH,
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL);
        ResourceBuilder resourceBuilder = ResourceBuilder.fromSpec(
                volumeSpec,
                Optional.empty(),
                Optional.empty(),
                Optional.of(TestConstants.MOUNT_SOURCE_ROOT));

        Protos.Resource resource = resourceBuilder.build();
        validateScalarResoure(resource);
        validateDisk(resource);

        Protos.Resource.DiskInfo diskInfo = resource.getDisk();
        Assert.assertTrue(diskInfo.hasSource());
        Protos.Resource.DiskInfo.Source source = diskInfo.getSource();
        Assert.assertEquals("MOUNT", source.getType().toString());
        Assert.assertTrue(source.hasMount());
        Assert.assertEquals(TestConstants.MOUNT_SOURCE_ROOT, source.getMount().getRoot());
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
    public void testNewFromMountVolumeSpecRefined() {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testNewFromMountVolumeSpec();
        } finally {
            context.reset();
        }
    }

    @Test
    public void testExistingFromMountVolumeSpec() {
        VolumeSpec volumeSpec = new DefaultVolumeSpec(
                10,
                VolumeSpec.Type.MOUNT,
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
                Optional.of(TestConstants.MOUNT_SOURCE_ROOT));

        Protos.Resource resource = resourceBuilder.build();
        validateScalarResoure(resource);
        validateDisk(resource);

        Protos.Resource.DiskInfo diskInfo = resource.getDisk();
        Assert.assertTrue(diskInfo.hasSource());
        Protos.Resource.DiskInfo.Source source = diskInfo.getSource();
        Assert.assertEquals("MOUNT", source.getType().toString());
        Assert.assertTrue(source.hasMount());
        Assert.assertEquals(TestConstants.MOUNT_SOURCE_ROOT, source.getMount().getRoot());

        Protos.Resource.ReservationInfo reservationInfo = Capabilities.getInstance().supportsPreReservedResources() ?
                resource.getReservations(0) :
                resource.getReservation();
        Protos.Label label = reservationInfo.getLabels().getLabels(0);
        Assert.assertEquals(resourceId.get(), label.getValue());
        Assert.assertEquals(persistenceId.get(), resource.getDisk().getPersistence().getId());
    }

    @Test
    public void testExistingFromMountVolumeSpecRefined() {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testExistingFromMountVolumeSpec();
        } finally {
            context.reset();
        }
    }

    @Test
    public void testFromExistingScalarResource() {
        ResourceSpec resourceSpec = new DefaultResourceSpec(
                "cpus",
                value,
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL);
        Optional<String> resourceId = Optional.of(UUID.randomUUID().toString());
        Protos.Resource originalResource = ResourceBuilder.fromSpec(resourceSpec, resourceId).build();
        Protos.Resource reconstructedResource = ResourceBuilder.fromExistingResource(originalResource).build();

        Assert.assertEquals(originalResource, reconstructedResource);
    }

    @Test
    public void testFromExistingScalarResourceRefined() {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testFromExistingScalarResource();
        } finally {
            context.reset();
        }
    }

    @Test
    public void testFromExistingRootVolume() {
        VolumeSpec volumeSpec = new DefaultVolumeSpec(
                10,
                VolumeSpec.Type.ROOT,
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
                Optional.empty())
                .build();
        Protos.Resource reconstructedResource = ResourceBuilder.fromExistingResource(originalResource).build();

        Assert.assertEquals(originalResource, reconstructedResource);
    }

    @Test
    public void testFromExistingRootVolumeRefined() {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testFromExistingRootVolume();
        } finally {
            context.reset();
        }
    }

    @Test
    public void testFromExistingMountVolume() {
        VolumeSpec volumeSpec = new DefaultVolumeSpec(
                10,
                VolumeSpec.Type.MOUNT,
                TestConstants.CONTAINER_PATH,
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL);
        Optional<String> resourceId = Optional.of(UUID.randomUUID().toString());
        Optional<String> persistenceId = Optional.of(UUID.randomUUID().toString());
        Optional<String> sourceRoot = Optional.of(TestConstants.MOUNT_SOURCE_ROOT);
        Protos.Resource originalResource = ResourceBuilder.fromSpec(
                volumeSpec,
                resourceId,
                persistenceId,
                sourceRoot)
                .build();
        Protos.Resource reconstructedResource = ResourceBuilder.fromExistingResource(originalResource).build();

        Assert.assertEquals(originalResource, reconstructedResource);
    }

    @Test
    public void testFromExistingMountVolumeRefined() {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testFromExistingMountVolume();
        } finally {
            context.reset();
        }
    }

    private void validateScalarResoure(Protos.Resource resource) {
        if (Capabilities.getInstance().supportsPreReservedResources()) {
            validateScalarResourceRefined(resource);
        } else {
            validateScalarResourceLegacy(resource);
        }
    }

    private void validateScalarResourceRefined(Protos.Resource resource) {
        Assert.assertEquals(Protos.Value.Type.SCALAR, resource.getType());
        Assert.assertEquals(Constants.ANY_ROLE, resource.getRole());
        Assert.assertFalse(resource.hasReservation());

        Protos.Resource.ReservationInfo reservationInfo = resource.getReservations(resource.getReservationsCount() - 1);
        Assert.assertEquals(TestConstants.PRINCIPAL, reservationInfo.getPrincipal());
        Assert.assertEquals(TestConstants.ROLE, reservationInfo.getRole());
        Assert.assertEquals(1, reservationInfo.getLabels().getLabelsCount());
        Assert.assertEquals(36, AuxLabelAccess.getResourceId(reservationInfo).get().length());
    }

    private void validateScalarResourceLegacy(Protos.Resource resource) {
        Assert.assertEquals(Protos.Value.Type.SCALAR, resource.getType());
        Assert.assertEquals(TestConstants.ROLE, resource.getRole());
        Assert.assertTrue(resource.hasReservation());
        Assert.assertEquals(0, resource.getReservationsCount());

        Protos.Resource.ReservationInfo reservationInfo = resource.getReservation();
        Assert.assertEquals(TestConstants.PRINCIPAL, reservationInfo.getPrincipal());
        Assert.assertFalse(reservationInfo.hasRole());
        Assert.assertEquals(1, reservationInfo.getLabels().getLabelsCount());
        Assert.assertEquals(36, AuxLabelAccess.getResourceId(reservationInfo).get().length());
    }

    private void validateDisk(Protos.Resource resource) {
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
    }
}
