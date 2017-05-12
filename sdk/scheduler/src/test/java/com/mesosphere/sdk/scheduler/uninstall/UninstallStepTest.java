package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.offer.CreateOfferRecommendation;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.UnreserveOfferRecommendation;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

public class UninstallStepTest {
    private static final String RESOURCE_ID = "resource id";
    private static final String DIFFERENT_RESOURCE_ID = "nope";
    private UninstallStep uninstallStep;

    @Before
    public void beforeEach() throws Exception {
        uninstallStep = new UninstallStep(RESOURCE_ID, Status.PENDING);
    }

    @Test
    public void testStart() throws Exception {
        assert uninstallStep.getStatus().equals(Status.PENDING);
        assert uninstallStep.start().equals(Optional.empty());
        assert uninstallStep.getStatus().equals(Status.PREPARED);
    }

    @Test
    public void testMatchingUpdateOfferStatus() throws Exception {
        OfferRecommendation offerRecommendation = new UnreserveOfferRecommendation(null,
                ResourceUtils.getExpectedScalar("cpus",
                        1.0,
                        RESOURCE_ID,
                        TestConstants.ROLE,
                        TestConstants.PRINCIPAL));
        uninstallStep.start();
        uninstallStep.updateOfferStatus(Collections.singletonList(offerRecommendation));
        assert uninstallStep.getStatus().equals(Status.COMPLETE);
    }

    @Test
    public void testNonMatchingUpdateOfferStatus() throws Exception {
        OfferRecommendation offerRecommendation = new UnreserveOfferRecommendation(null,
                ResourceUtils.getExpectedScalar("cpus",
                        1.0,
                        DIFFERENT_RESOURCE_ID,
                        TestConstants.ROLE,
                        TestConstants.PRINCIPAL));
        uninstallStep.start();
        uninstallStep.updateOfferStatus(Collections.singletonList(offerRecommendation));
        assert uninstallStep.getStatus().equals(Status.PREPARED);
    }

    @Test
    public void testMixedUpdateOfferStatus() throws Exception {
        OfferRecommendation rec1 = new CreateOfferRecommendation(null, ResourceUtils.getDesiredRootVolume(
                TestConstants.ROLE, TestConstants.PRINCIPAL, 999.0, "container-path"));
        OfferRecommendation rec2 = new UnreserveOfferRecommendation(null,
                ResourceUtils.getExpectedScalar("cpus", 1.0, RESOURCE_ID, TestConstants.ROLE, TestConstants.PRINCIPAL));
        uninstallStep.start();
        uninstallStep.updateOfferStatus(Arrays.asList(rec1, rec2));
        assert uninstallStep.getStatus().equals(Status.COMPLETE);
    }

    @Test
    public void testGetAsset() throws Exception {
        assert uninstallStep.getAsset().equals(Optional.empty());
    }

    @Test
    public void testGetErrors() throws Exception {
        assert uninstallStep.getErrors().equals(Collections.emptyList());
    }
}