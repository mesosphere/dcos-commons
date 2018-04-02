package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.CreateOfferRecommendation;
import com.mesosphere.sdk.offer.DestroyOfferRecommendation;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.UnreserveOfferRecommendation;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TaskTestUtils;

import org.apache.mesos.Protos;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;

public class UninstallRecorderTest {

    @Mock private Capabilities mockCapabilities;
    @Mock private StateStore mockStateStore;
    @Mock private ResourceCleanupStep mockStep;

    private UninstallRecorder recorder;

    private Protos.Resource taskResource;
    private Protos.Resource otherResource;
    private Protos.Offer offer;

    private Protos.TaskInfo taskA;
    private Protos.TaskInfo taskB;
    private Protos.TaskInfo emptyTaskA;
    private Protos.TaskInfo emptyTaskB;

    private Protos.TaskInfo emptyTask;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        Capabilities.overrideCapabilities(mockCapabilities);
        recorder = new UninstallRecorder(mockStateStore, Arrays.asList(mockStep));

        taskResource = ResourceTestUtils.getReservedCpus(5, "matching-resource");
        otherResource = ResourceTestUtils.getReservedCpus(5, "other-resource");
        offer = OfferTestUtils.getOffer(Arrays.asList(taskResource, otherResource));

        taskA = TaskTestUtils.getTaskInfo(taskResource);
        taskB = TaskTestUtils.getTaskInfo(taskResource);
        emptyTaskA = taskA.toBuilder().clearResources().build();
        emptyTaskB = taskB.toBuilder().clearResources().build();

        emptyTask = TaskTestUtils.getTaskInfo(Collections.emptyList());

        when(mockStateStore.fetchTasks()).thenReturn(Arrays.asList(taskA, taskB, emptyTask));
    }

    @Test
    public void testDestroyNotFound() throws Exception {
        recorder.recordRecommendations(Collections.singletonList(new DestroyOfferRecommendation(offer, otherResource)));
        verify(mockStateStore, times(0)).storeTasks(any());
        // Step(s) still notified, even if no StateStore tasks had it:
        verify(mockStep).updateResourceStatus(Collections.singleton(ResourceUtils.getResourceId(otherResource).get()));
    }

    @Test
    public void testUnreserveNotFound() throws Exception {
        recorder.recordRecommendations(Collections.singletonList(new UnreserveOfferRecommendation(offer, otherResource)));
        verify(mockStateStore, times(0)).storeTasks(any());
        // Step(s) still notified, even if no StateStore tasks had it:
        verify(mockStep).updateResourceStatus(Collections.singleton(ResourceUtils.getResourceId(otherResource).get()));
    }

    @Test
    public void testDestroy() throws Exception {
        recorder.recordRecommendations(Collections.singletonList(new DestroyOfferRecommendation(offer, taskResource)));
        verify(mockStateStore).storeTasks(Arrays.asList(emptyTaskA, emptyTaskB));
        verify(mockStep).updateResourceStatus(Collections.singleton(ResourceUtils.getResourceId(taskResource).get()));
    }

    @Test
    public void testUnreserve() throws Exception {
        recorder.recordRecommendations(Collections.singletonList(new UnreserveOfferRecommendation(offer, taskResource)));
        verify(mockStateStore).storeTasks(Arrays.asList(emptyTaskA, emptyTaskB));
        verify(mockStep).updateResourceStatus(Collections.singleton(ResourceUtils.getResourceId(taskResource).get()));
    }

    @Test
    public void testHandlingOfUnexpectedOfferRecommendation() throws Exception {
        OfferRecommendation unsupportedOfferRecommendation =
                new CreateOfferRecommendation(offer, ResourceTestUtils.getUnreservedCpus(1.0));
        // should just return without error
        recorder.recordRecommendations(Collections.singletonList(unsupportedOfferRecommendation));
        verifyZeroInteractions(mockStateStore);
        verifyZeroInteractions(mockStep);
    }
}
