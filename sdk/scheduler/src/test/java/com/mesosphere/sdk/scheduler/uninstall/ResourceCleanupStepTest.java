package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.testutils.DefaultCapabilitiesTestSuite;
import com.mesosphere.sdk.testutils.TestConstants;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

public class ResourceCleanupStepTest extends DefaultCapabilitiesTestSuite {
    private static final String DIFFERENT_RESOURCE_ID = "nope";
    private ResourceCleanupStep resourceCleanupStep;

    @Before
    public void beforeEach() throws Exception {
        resourceCleanupStep = new ResourceCleanupStep(TestConstants.RESOURCE_ID, Optional.empty());
    }

    @Test
    public void testStart() throws Exception {
        Assert.assertEquals(Status.PENDING, resourceCleanupStep.getStatus());
        Assert.assertFalse(resourceCleanupStep.start().isPresent());
        Assert.assertEquals(Status.PREPARED, resourceCleanupStep.getStatus());
    }

    @Test
    public void testMatchingUpdateOfferStatus() throws Exception {
        resourceCleanupStep.start();
        resourceCleanupStep.updateResourceStatus(Collections.singleton(TestConstants.RESOURCE_ID));
        Assert.assertEquals(Status.COMPLETE, resourceCleanupStep.getStatus());
    }

    @Test
    public void testNonMatchingUpdateOfferStatus() throws Exception {
        resourceCleanupStep.start();
        resourceCleanupStep.updateResourceStatus(Collections.singleton(DIFFERENT_RESOURCE_ID));
        Assert.assertEquals(Status.PREPARED, resourceCleanupStep.getStatus());
    }

    @Test
    public void testMixedUpdateOfferStatus() throws Exception {
        resourceCleanupStep.start();
        resourceCleanupStep.updateResourceStatus(
                new HashSet<>(Arrays.asList(TestConstants.RESOURCE_ID, DIFFERENT_RESOURCE_ID)));
        Assert.assertEquals(Status.COMPLETE, resourceCleanupStep.getStatus());
    }

    @Test
    public void testGetAsset() throws Exception {
        Assert.assertFalse(resourceCleanupStep.getPodInstanceRequirement().isPresent());
    }

    @Test
    public void testGetErrors() throws Exception {
        Assert.assertEquals(Collections.emptyList(), resourceCleanupStep.getErrors());
    }
}
