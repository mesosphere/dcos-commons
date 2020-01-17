package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.testutils.DefaultCapabilitiesTestSuite;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

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

    private static final String UNEXPECTED_RESOURCE_2_ID = "unexpected-volume-id-2";
    private static final Protos.Resource UNEXPECTED_RESOURCE_2 =
            ResourceTestUtils.getReservedRootVolume(
                    1000.0,
                    UNEXPECTED_RESOURCE_2_ID,
                    UNEXPECTED_RESOURCE_2_ID,
                    "unknown-framework-id");

    private static final String EXPECTED_RESOURCE_1_ID = "expected-volume-id-1";
    private static final Protos.Resource EXPECTED_RESOURCE_1 =
            ResourceTestUtils.getReservedRootVolume(
                    1000.0,
                    EXPECTED_RESOURCE_1_ID,
                    EXPECTED_RESOURCE_1_ID,
                    TestConstants.FRAMEWORK_ID.getValue());



    @Test
    public void resourceNotProcessableForEmptyRoles() {
        Assert.assertFalse(ResourceUtils.isProcessable(UNEXPECTED_RESOURCE_1, Collections.emptySet(), Optional.of(TestConstants.FRAMEWORK_ID)));
    }

    @Test
    public void resourceNotProcessableForDifferentRole() {
        Assert.assertFalse(ResourceUtils.isProcessable(UNEXPECTED_RESOURCE_1, Collections.singleton("different-role"), Optional.of(TestConstants.FRAMEWORK_ID)));
    }

    @Test
    public void resourceNotProcessableForDifferentRoles() {
        Assert.assertFalse(ResourceUtils.isProcessable(
                UNEXPECTED_RESOURCE_1, Arrays.asList("different-role-0", "different-role-1"), Optional.of(TestConstants.FRAMEWORK_ID)));
    }

    @Test
    public void resourceProcessableForExactRoles() {
        Assert.assertTrue(ResourceUtils.isProcessable(
                UNEXPECTED_RESOURCE_1, Collections.singleton(TestConstants.ROLE), Optional.of(TestConstants.FRAMEWORK_ID)));
    }

    @Test
    public void resourceProcessableForSubsetOfRoles() {
        Assert.assertTrue(ResourceUtils.isProcessable(
                UNEXPECTED_RESOURCE_1, Arrays.asList(TestConstants.ROLE, "another-role"), Optional.of(TestConstants.FRAMEWORK_ID)));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void resourceProcessableForPartialSubsetOfRoles() {
        Protos.Resource alienResource = UNEXPECTED_RESOURCE_1.toBuilder().setRole("alien-role").build();
        Assert.assertFalse(ResourceUtils.isProcessable(
                alienResource, Arrays.asList(TestConstants.ROLE, "another-role"), Optional.of(TestConstants.FRAMEWORK_ID)));
    }

    @Test
    public void resourceNotProcessableForDifferentFrameworkId() {
        Assert.assertFalse(ResourceUtils.isProcessable(
                UNEXPECTED_RESOURCE_2, Collections.singleton(TestConstants.ROLE), Optional.of(TestConstants.FRAMEWORK_ID)));
    }

    @Test
    public void resourceProcessableForSameFrameworkId() {
        Assert.assertTrue(ResourceUtils.isProcessable(
                EXPECTED_RESOURCE_1, Collections.singleton(TestConstants.ROLE), Optional.of(TestConstants.FRAMEWORK_ID)));
    }
}
