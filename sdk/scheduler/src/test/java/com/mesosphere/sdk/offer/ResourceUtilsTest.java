package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.testutils.DefaultCapabilitiesTestSuite;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

public class ResourceUtilsTest extends DefaultCapabilitiesTestSuite {
    private static final String UNEXPECTED_RESOURCE_1_ID = "unexpected-volume-id-1";
    private static final Protos.Resource UNEXPECTED_RESOURCE_1 =
            ResourceTestUtils.getReservedRootVolume(
                    1000.0,
                    UNEXPECTED_RESOURCE_1_ID,
                    UNEXPECTED_RESOURCE_1_ID);

    @Test
    public void resourceNotOwnedForEmptyRoles() {
        final Protos.FrameworkInfo frameworkInfo = Protos.FrameworkInfo.newBuilder()
                .setName(TestConstants.SERVICE_NAME)
                .setUser(TestConstants.SERVICE_USER)
                .build();

        Assert.assertFalse(ResourceUtils.isOwnedByThisFramework(UNEXPECTED_RESOURCE_1, frameworkInfo));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void resourceNotOwnedForDifferentRole() {
        final Protos.FrameworkInfo frameworkInfo = Protos.FrameworkInfo.newBuilder()
                .setName(TestConstants.SERVICE_NAME)
                .setUser(TestConstants.SERVICE_USER)
                .setRole("different-role")
                .build();

        Assert.assertFalse(ResourceUtils.isOwnedByThisFramework(UNEXPECTED_RESOURCE_1, frameworkInfo));
    }

    @Test
    public void resourceNotOwnedForDifferentRoles() {
        final Protos.FrameworkInfo frameworkInfo = Protos.FrameworkInfo.newBuilder()
                .setName(TestConstants.SERVICE_NAME)
                .setUser(TestConstants.SERVICE_USER)
                .addRoles("different-role-0")
                .addRoles("different-role-1")
                .build();

        Assert.assertFalse(ResourceUtils.isOwnedByThisFramework(UNEXPECTED_RESOURCE_1, frameworkInfo));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void resourceNotOwnedForDifferentRolesWithLegacy() {
        final Protos.FrameworkInfo frameworkInfo = Protos.FrameworkInfo.newBuilder()
                .setName(TestConstants.SERVICE_NAME)
                .setUser(TestConstants.SERVICE_USER)
                .setRole("different-role-0")
                .addRoles("different-role-1")
                .build();

        Assert.assertFalse(ResourceUtils.isOwnedByThisFramework(UNEXPECTED_RESOURCE_1, frameworkInfo));
    }

    @Test
    public void resourceOwnedForExactRoles() {
        final Protos.FrameworkInfo frameworkInfo = Protos.FrameworkInfo.newBuilder()
                .setName(TestConstants.SERVICE_NAME)
                .setUser(TestConstants.SERVICE_USER)
                .addRoles(TestConstants.ROLE)
                .build();

        Assert.assertTrue(ResourceUtils.isOwnedByThisFramework(UNEXPECTED_RESOURCE_1, frameworkInfo));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void resourceOwnedForExactRolesLegacy() {
        final Protos.FrameworkInfo frameworkInfo = Protos.FrameworkInfo.newBuilder()
                .setName(TestConstants.SERVICE_NAME)
                .setUser(TestConstants.SERVICE_USER)
                .setRole(TestConstants.ROLE)
                .build();

        Assert.assertTrue(ResourceUtils.isOwnedByThisFramework(UNEXPECTED_RESOURCE_1, frameworkInfo));
    }

    @Test
    public void resourceOwnedForSubsetOfRoles() {
        final Protos.FrameworkInfo frameworkInfo = Protos.FrameworkInfo.newBuilder()
                .setName(TestConstants.SERVICE_NAME)
                .setUser(TestConstants.SERVICE_USER)
                .addRoles(TestConstants.ROLE)
                .addRoles("another-role")
                .build();

        Assert.assertTrue(ResourceUtils.isOwnedByThisFramework(UNEXPECTED_RESOURCE_1, frameworkInfo));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void resourceOwnedForSubsetOfRolesWithLegacy() {
        final Protos.FrameworkInfo frameworkInfo = Protos.FrameworkInfo.newBuilder()
                .setName(TestConstants.SERVICE_NAME)
                .setUser(TestConstants.SERVICE_USER)
                .addRoles(TestConstants.ROLE)
                .setRole("another-role")
                .build();

        Assert.assertTrue(ResourceUtils.isOwnedByThisFramework(UNEXPECTED_RESOURCE_1, frameworkInfo));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void resourceNotOwnedForPartialSubsetOfRoles() {
        final Protos.FrameworkInfo frameworkInfo = Protos.FrameworkInfo.newBuilder()
                .setName(TestConstants.SERVICE_NAME)
                .setUser(TestConstants.SERVICE_USER)
                .addRoles(TestConstants.ROLE)
                .setRole("another-role")
                .build();

        Protos.Resource alienResource = UNEXPECTED_RESOURCE_1.toBuilder().setRole("alien-role").build();
        Assert.assertFalse(ResourceUtils.isOwnedByThisFramework(alienResource, frameworkInfo));
    }
}
