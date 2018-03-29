package com.mesosphere.sdk.framework;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.offer.evaluate.placement.IsLocalRegionRule;
import com.mesosphere.sdk.reconciliation.Reconciler;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.Driver;
import com.mesosphere.sdk.state.FrameworkStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.testutils.DefaultCapabilitiesTestSuite;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class FrameworkSchedulerTest extends DefaultCapabilitiesTestSuite {

    private static final Protos.Filters SHORT_INTERVAL = Protos.Filters.newBuilder()
            .setRefuseSeconds(Constants.SHORT_DECLINE_SECONDS)
            .build();
    private static final Protos.MasterInfo MASTER_INFO =
            TestConstants.MASTER_INFO.toBuilder().setDomain(TestConstants.REMOTE_DOMAIN_INFO).build();
    private static final Protos.MasterInfo MASTER_INFO2 =
            TestConstants.MASTER_INFO.toBuilder().setDomain(TestConstants.LOCAL_DOMAIN_INFO).build();

    @Mock private FrameworkStore mockFrameworkStore;
    @Mock private StateStore mockStateStore;
    @Mock private AbstractScheduler mockAbstractScheduler;
    @Mock private OfferProcessor mockOfferProcessor;
    @Mock private Reconciler mockReconciler;
    @Mock private SchedulerDriver mockSchedulerDriver;
    @Mock private SchedulerDriver mockSchedulerDriver2;

    private FrameworkScheduler scheduler;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockOfferProcessor.getReconciler()).thenReturn(mockReconciler);
        scheduler = new FrameworkScheduler(
                Collections.singleton(TestConstants.ROLE),
                mockFrameworkStore,
                mockStateStore,
                mockAbstractScheduler,
                mockOfferProcessor)
                .disableThreading();
    }

    @Test
    public void testReregister() throws PersisterException {
        scheduler.registered(mockSchedulerDriver, TestConstants.FRAMEWORK_ID, MASTER_INFO);
        verify(mockFrameworkStore).storeFrameworkId(TestConstants.FRAMEWORK_ID);
        Assert.assertEquals(mockSchedulerDriver, Driver.getDriver().get());
        verifyDomainIsSet(MASTER_INFO.getDomain());
        verify(mockAbstractScheduler).registeredWithMesos();
        verify(mockOfferProcessor).start();

        // Call should be treated as a re-registration:
        scheduler.registered(mockSchedulerDriver2, TestConstants.FRAMEWORK_ID, MASTER_INFO2);
        Assert.assertEquals(mockSchedulerDriver2, Driver.getDriver().get());
        verifyDomainIsSet(MASTER_INFO2.getDomain());
        verify(mockAbstractScheduler).registeredWithMesos();

        // Not called a second time:
        verify(mockOfferProcessor, times(1)).start();
    }

    @Test
    public void testOfferReadiness() {
        Driver.setDriver(mockSchedulerDriver);

        List<Protos.Offer> offers = Arrays.asList(getOffer(), getOffer(), getOffer());

        // Offers tossed within the scheduler, not passed to the processor:
        scheduler.resourceOffers(mockSchedulerDriver, offers);
        verify(mockSchedulerDriver, times(3)).declineOffer(any(), eq(SHORT_INTERVAL));
        verify(mockOfferProcessor, never()).enqueue(any());

        scheduler.setReadyToAcceptOffers();

        // Offers passed to the processor:
        scheduler.resourceOffers(mockSchedulerDriver, offers);
        verify(mockOfferProcessor).enqueue(offers);
    }

    @Test
    public void testOfferRescinded() {
        Protos.Offer offer = getOffer();
        scheduler.offerRescinded(mockSchedulerDriver, offer.getId());
        verify(mockOfferProcessor).dequeue(offer.getId());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testFilteredResources() {
        scheduler.setReadyToAcceptOffers();

        String resourceId = "unexpected-volume-id-1";
        Protos.Resource resourceMatchingRole = ResourceTestUtils.getReservedRootVolume(1000.0, resourceId, resourceId);
        Protos.Resource resourceOtherRole = resourceMatchingRole.toBuilder().setRole("other-role").build();

        Protos.Offer offer = getOffer("foo").toBuilder().addResources(resourceMatchingRole).addResources(resourceOtherRole).build();
        Protos.Offer filteredOffer = getOffer("foo").toBuilder().addResources(resourceMatchingRole).build();

        // Resource with other role should be filtered out:
        scheduler.resourceOffers(mockSchedulerDriver, Collections.singletonList(offer));
        verify(mockOfferProcessor).enqueue(Collections.singletonList(filteredOffer));
    }

    @Test
    public void testStatusUnknownTask() {
        Driver.setDriver(mockSchedulerDriver);
        scheduler.registered(mockSchedulerDriver, TestConstants.FRAMEWORK_ID, MASTER_INFO);
        scheduler.statusUpdate(mockSchedulerDriver, TestConstants.TASK_STATUS);
        verify(mockSchedulerDriver).killTask(TestConstants.TASK_STATUS.getTaskId());
    }

    private static void verifyDomainIsSet(Protos.DomainInfo expectedDomain) {
        // Infer the configured domain via a placement rule invocation
        Protos.Offer offerWithExpectedDomain = getOffer().toBuilder().setDomain(expectedDomain).build();
        EvaluationOutcome outcome = new IsLocalRegionRule().filter(offerWithExpectedDomain, null, null);
        Assert.assertTrue(outcome.getReason(), outcome.isPassing());
    }

    private static Protos.Offer getOffer() {
        return getOffer(UUID.randomUUID().toString());
    }

    private static Protos.Offer getOffer(String id) {
        return Protos.Offer.newBuilder()
                .setId(Protos.OfferID.newBuilder().setValue(id))
                .setFrameworkId(TestConstants.FRAMEWORK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .setHostname(TestConstants.HOSTNAME)
                .build();
    }
}
