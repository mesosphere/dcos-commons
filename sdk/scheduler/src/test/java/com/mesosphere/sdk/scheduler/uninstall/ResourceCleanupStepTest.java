package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.offer.CreateOfferRecommendation;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.UnreserveOfferRecommendation;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.testutils.DefaultCapabilitiesTestSuite;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

public class ResourceCleanupStepTest extends DefaultCapabilitiesTestSuite {
    private static final String DIFFERENT_RESOURCE_ID = "nope";
    private ResourceCleanupStep resourceCleanupStep;

    @Before
    public void beforeEach() throws Exception {
        resourceCleanupStep = new ResourceCleanupStep(TestConstants.RESOURCE_ID, Status.PENDING);
    }

    @Test
    public void testStart() throws Exception {
        assert resourceCleanupStep.getStatus().equals(Status.PENDING);
        assert resourceCleanupStep.start().equals(Optional.empty());
        assert resourceCleanupStep.getStatus().equals(Status.PREPARED);
    }

    @Test
    public void testMatchingUpdateOfferStatus() throws Exception {
        OfferRecommendation offerRecommendation = new UnreserveOfferRecommendation(null,
                ResourceTestUtils.getReservedCpus(1.0, TestConstants.RESOURCE_ID));
        resourceCleanupStep.start();
        resourceCleanupStep.updateOfferStatus(Collections.singletonList(offerRecommendation));
        assert resourceCleanupStep.getStatus().equals(Status.COMPLETE);
    }

    @Test
    public void testNonMatchingUpdateOfferStatus() throws Exception {
        OfferRecommendation offerRecommendation = new UnreserveOfferRecommendation(null,
                ResourceTestUtils.getReservedCpus(1.0, DIFFERENT_RESOURCE_ID));
        resourceCleanupStep.start();
        resourceCleanupStep.updateOfferStatus(Collections.singletonList(offerRecommendation));
        assert resourceCleanupStep.getStatus().equals(Status.PREPARED);
    }

    @Test
    public void testMixedUpdateOfferStatus() throws Exception {
        OfferRecommendation rec1 = new CreateOfferRecommendation(null, ResourceTestUtils.getReservedRootVolume(999.0));
        OfferRecommendation rec2 = new UnreserveOfferRecommendation(null,
                ResourceTestUtils.getReservedCpus(1.0, TestConstants.RESOURCE_ID));
        resourceCleanupStep.start();
        resourceCleanupStep.updateOfferStatus(Arrays.asList(rec1, rec2));
        assert resourceCleanupStep.getStatus().equals(Status.COMPLETE);
    }

    @Test
    public void testGetAsset() throws Exception {
        assert resourceCleanupStep.getPodInstanceRequirement().equals(Optional.empty());
    }

    @Test
    public void testGetErrors() throws Exception {
        assert resourceCleanupStep.getErrors().equals(Collections.emptyList());
    }
}
