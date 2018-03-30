package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.testutils.DefaultCapabilitiesTestSuite;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;

import java.util.Arrays;
import java.util.Collections;

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
    public void resourceNotProcessableForEmptyRoles() {
        Assert.assertFalse(ResourceUtils.isProcessable(UNEXPECTED_RESOURCE_1, Collections.emptySet()));
    }

    @Test
    public void resourceNotProcessableForDifferentRole() {
        Assert.assertFalse(ResourceUtils.isProcessable(UNEXPECTED_RESOURCE_1, Collections.singleton("different-role")));
    }

    @Test
    public void resourceNotProcessableForDifferentRoles() {
        Assert.assertFalse(ResourceUtils.isProcessable(
                UNEXPECTED_RESOURCE_1, Arrays.asList("different-role-0", "different-role-1")));
    }

    @Test
    public void resourceProcessableForExactRoles() {
        Assert.assertTrue(ResourceUtils.isProcessable(
                UNEXPECTED_RESOURCE_1, Collections.singleton(TestConstants.ROLE)));
    }

    @Test
    public void resourceProcessableForSubsetOfRoles() {
        Assert.assertTrue(ResourceUtils.isProcessable(
                UNEXPECTED_RESOURCE_1, Arrays.asList(TestConstants.ROLE, "another-role")));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void resourceProcessableForPartialSubsetOfRoles() {
        Protos.Resource alienResource = UNEXPECTED_RESOURCE_1.toBuilder().setRole("alien-role").build();
        Assert.assertFalse(ResourceUtils.isProcessable(
                alienResource, Arrays.asList(TestConstants.ROLE, "another-role")));
    }
}
