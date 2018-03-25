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
        ResourceSpec resourceSpec = new DefaultResourceSpec(
                "cpus",
                VALUE,
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL);
        ResourceBuilder resourceBuilder = ResourceBuilder.fromSpec(resourceSpec, Optional.empty(), namespace);

        Protos.Resource resource = resourceBuilder.build();
        validateScalarResource(resource, Optional.empty(), namespace);
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
            ResourceSpec resourceSpec = new DefaultResourceSpec(
                    "cpus",
                    VALUE,
                    TestConstants.ROLE,
                    TestConstants.PRE_RESERVED_ROLE,
                    TestConstants.PRINCIPAL);
            ResourceBuilder resourceBuilder = ResourceBuilder.fromSpec(resourceSpec, Optional.empty(), namespace);

            Protos.Resource resource = resourceBuilder.build();
            Assert.assertEquals(2, resource.getReservationsCount());
            validateScalarResourceRefined(resource, Optional.empty(), namespace);
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
        ResourceSpec resourceSpec = new DefaultResourceSpec(
                "cpus",
                VALUE,
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL);
        validateScalarResource(
                ResourceBuilder.fromSpec(resourceSpec, resourceId, namespace).build(), resourceId, namespace);
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
        testNewFromRootVolumeSpec(Optional.empty());
        testNewFromRootVolumeSpec(Optional.of("foo"));

        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testNewFromRootVolumeSpec(Optional.empty());
            testNewFromRootVolumeSpec(Optional.of("foo"));
        } finally {
            context.reset();
        }
    }

    private static void testNewFromRootVolumeSpec(Optional<String> namespace) {
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
                namespace,
                Optional.empty(),
                Optional.empty());

        Protos.Resource resource = resourceBuilder.build();
        validateDisk(resource, Optional.empty(), namespace);
    }

    @Test
    public void testExistingFromRootVolumeSpec() {
        testExistingFromRootVolumeSpec(Optional.empty());
        testExistingFromRootVolumeSpec(Optional.of("/path/to/namespace"));

        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testExistingFromRootVolumeSpec(Optional.empty());
            testExistingFromRootVolumeSpec(Optional.of("/path/to/namespace"));
        } finally {
            context.reset();
        }
    }

    private static void testExistingFromRootVolumeSpec(Optional<String> namespace) {
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
                namespace,
                persistenceId,
                Optional.empty());

        Protos.Resource resource = resourceBuilder.build();
        validateDisk(resource, resourceId, namespace);
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
        testNewFromMountVolumeSpec(Optional.empty());
        testNewFromMountVolumeSpec(Optional.of("foo"));

        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testNewFromMountVolumeSpec(Optional.empty());
            testNewFromMountVolumeSpec(Optional.of("foo"));
        } finally {
            context.reset();
        }
    }

    private static void testNewFromMountVolumeSpec(Optional<String> namespace) {
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
                namespace,
                Optional.empty(),
                Optional.of(TestConstants.MOUNT_SOURCE_ROOT));

        Protos.Resource resource = resourceBuilder.build();
        validateDisk(resource, Optional.empty(), namespace);

        Protos.Resource.DiskInfo diskInfo = resource.getDisk();
        Assert.assertTrue(diskInfo.hasSource());
        Protos.Resource.DiskInfo.Source source = diskInfo.getSource();
        Assert.assertEquals("MOUNT", source.getType().toString());
        Assert.assertTrue(source.hasMount());
        Assert.assertEquals(TestConstants.MOUNT_SOURCE_ROOT, source.getMount().getRoot());
    }

    @Test
    public void testExistingFromMountVolumeSpec() {
        testExistingFromMountVolumeSpec(Optional.empty());
        testExistingFromMountVolumeSpec(Optional.of("some/namespace"));

        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testExistingFromMountVolumeSpec(Optional.empty());
            testExistingFromMountVolumeSpec(Optional.of("some/namespace"));
        } finally {
            context.reset();
        }
    }

    private static void testExistingFromMountVolumeSpec(Optional<String> namespace) {
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
                namespace,
                persistenceId,
                Optional.of(TestConstants.MOUNT_SOURCE_ROOT));

        Protos.Resource resource = resourceBuilder.build();
        validateDisk(resource, resourceId, namespace);

        Protos.Resource.DiskInfo diskInfo = resource.getDisk();
        Assert.assertTrue(diskInfo.hasSource());
        Protos.Resource.DiskInfo.Source source = diskInfo.getSource();
        Assert.assertEquals("MOUNT", source.getType().toString());
        Assert.assertTrue(source.hasMount());
        Assert.assertEquals(TestConstants.MOUNT_SOURCE_ROOT, source.getMount().getRoot());

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
        ResourceSpec resourceSpec = new DefaultResourceSpec(
                "cpus",
                VALUE,
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL);
        Optional<String> resourceId = Optional.of(UUID.randomUUID().toString());
        Protos.Resource originalResource =
                ResourceBuilder.fromSpec(resourceSpec, resourceId, namespace).build();
        Protos.Resource reconstructedResource =
                ResourceBuilder.fromExistingResource(originalResource).build();

        Assert.assertEquals(originalResource, reconstructedResource);
    }

    @Test
    public void testFromExistingRootVolume() {
        testFromExistingRootVolume(Optional.empty());
        testFromExistingRootVolume(Optional.of("foo/bar"));

        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testFromExistingRootVolume(Optional.empty());
            testFromExistingRootVolume(Optional.of("foo/bar"));
        } finally {
            context.reset();
        }
    }

    private static void testFromExistingRootVolume(Optional<String> namespace) {
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
                namespace,
                persistenceId,
                Optional.empty())
                .build();
        Protos.Resource reconstructedResource =
                ResourceBuilder.fromExistingResource(originalResource).build();

        Assert.assertEquals(originalResource, reconstructedResource);
    }

    @Test
    public void testFromExistingMountVolume() {
        testFromExistingMountVolume(Optional.empty());
        testFromExistingMountVolume(Optional.of("namespace/path"));

        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testFromExistingMountVolume(Optional.empty());
            testFromExistingMountVolume(Optional.of("namespace/path"));
        } finally {
            context.reset();
        }
    }

    private static void testFromExistingMountVolume(Optional<String> namespace) {
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
                namespace,
                persistenceId,
                sourceRoot)
                .build();
        Protos.Resource reconstructedResource =
                ResourceBuilder.fromExistingResource(originalResource).build();

        Assert.assertEquals(originalResource, reconstructedResource);
    }

    private static void validateDisk(Protos.Resource resource, Optional<String> resourceId, Optional<String> namespace) {
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

        validateScalarResource(resource, resourceId, namespace);
    }

    private static void validateScalarResource(
            Protos.Resource resource, Optional<String> resourceId, Optional<String> namespace) {
        if (Capabilities.getInstance().supportsPreReservedResources()) {
            validateScalarResourceRefined(resource, resourceId, namespace);
        } else {
            validateScalarResourceLegacy(resource, resourceId, namespace);
        }
    }

    @SuppressWarnings("deprecation")
    private static void validateScalarResourceRefined(
            Protos.Resource resource, Optional<String> resourceId, Optional<String> namespace) {
        Assert.assertEquals(Protos.Value.Type.SCALAR, resource.getType());
        Assert.assertEquals(Constants.ANY_ROLE, resource.getRole());
        Assert.assertFalse(resource.hasReservation());

        Protos.Resource.ReservationInfo reservationInfo = resource.getReservations(resource.getReservationsCount() - 1);
        Assert.assertEquals(TestConstants.PRINCIPAL, reservationInfo.getPrincipal());
        Assert.assertEquals(TestConstants.ROLE, reservationInfo.getRole());

        validateLabels(reservationInfo, resourceId, namespace);
    }

    @SuppressWarnings("deprecation")
    private static void validateScalarResourceLegacy(Protos.Resource resource, Optional<String> resourceId, Optional<String> namespace) {
        Assert.assertEquals(Protos.Value.Type.SCALAR, resource.getType());
        Assert.assertEquals(TestConstants.ROLE, resource.getRole());
        Assert.assertTrue(resource.hasReservation());
        Assert.assertEquals(0, resource.getReservationsCount());

        Protos.Resource.ReservationInfo reservationInfo = resource.getReservation();
        Assert.assertEquals(TestConstants.PRINCIPAL, reservationInfo.getPrincipal());
        Assert.assertFalse(reservationInfo.hasRole());

        validateLabels(reservationInfo, resourceId, namespace);
    }

    private static void validateLabels(
            Protos.Resource.ReservationInfo reservationInfo, Optional<String> resourceId, Optional<String> namespace) {
        if (resourceId.isPresent()) {
            Assert.assertEquals(resourceId.get(), AuxLabelAccess.getResourceId(reservationInfo).get());
        } else {
            Assert.assertEquals(36, AuxLabelAccess.getResourceId(reservationInfo).get().length());
        }
        if (namespace.isPresent()) {
            Assert.assertEquals(2, reservationInfo.getLabels().getLabelsCount());
            Assert.assertEquals(namespace.get(), AuxLabelAccess.getResourceNamespace(reservationInfo).get());
        } else {
            // Just the resource id label:
            Assert.assertEquals(1, reservationInfo.getLabels().getLabelsCount());
        }
    }
}
