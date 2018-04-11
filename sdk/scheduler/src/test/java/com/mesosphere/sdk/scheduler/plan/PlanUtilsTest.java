package com.mesosphere.sdk.scheduler.plan;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.specification.DefaultPodSpec;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.TestConstants;
import com.mesosphere.sdk.testutils.TestPodFactory;

public class PlanUtilsTest {
    private static final String TEST_STEP_NAME = "test-step";
    private static final String TASK_NAME_0 = TestConstants.TASK_NAME + 0;
    private static final String TASK_NAME_1 = TestConstants.TASK_NAME + 1;

    private static final PodSpec POD_SPEC = DefaultPodSpec.newBuilder(
            "",
            TestConstants.POD_TYPE,
            1,
            Arrays.asList(
                    TestPodFactory.getTaskSpec(
                            TASK_NAME_0, TestConstants.RESOURCE_SET_ID + 0, TestConstants.TASK_DNS_PREFIX),
                    TestPodFactory.getTaskSpec(
                            TASK_NAME_1, TestConstants.RESOURCE_SET_ID + 1, TestConstants.TASK_DNS_PREFIX)))
            .build();

    @Mock private StateStore mockStateStore;

    private DeploymentStep step;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        PodInstance podInstance = new DefaultPodInstance(POD_SPEC, 0);
        step = new DeploymentStep(
                TEST_STEP_NAME,
                PodInstanceRequirement.newBuilder(podInstance, TaskUtils.getTaskNames(podInstance)).build(),
                mockStateStore,
                Optional.empty());
    }

    @Test
    public void testAggregateStatusError() {
        Assert.assertEquals(Status.ERROR, PlanUtils.getAggregateStatus("foo",
                Collections.emptyList(), Collections.emptyList(), Arrays.asList("err"), false));
        Assert.assertEquals(Status.ERROR, PlanUtils.getAggregateStatus("foo",
                Arrays.asList(Status.ERROR), Collections.emptyList(), Collections.emptyList(), false));
    }

    @Test
    public void testAggregateStatusComplete() {
        Assert.assertEquals(Status.COMPLETE, PlanUtils.getAggregateStatus("foo",
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false));
        Assert.assertEquals(Status.COMPLETE, PlanUtils.getAggregateStatus("foo",
                Arrays.asList(Status.COMPLETE, Status.COMPLETE), Collections.emptyList(), Collections.emptyList(), false));
    }

    @Test
    public void testAggregateStatusWaiting() {
        Assert.assertEquals(Status.WAITING, PlanUtils.getAggregateStatus("foo",
                Arrays.asList(Status.COMPLETE, Status.PENDING), Collections.emptyList(), Collections.emptyList(), true));
        Assert.assertEquals(Status.WAITING, PlanUtils.getAggregateStatus("foo",
                Arrays.asList(Status.COMPLETE, Status.WAITING), Collections.emptyList(), Collections.emptyList(), false));
        Assert.assertEquals(Status.WAITING, PlanUtils.getAggregateStatus("foo",
                Arrays.asList(Status.COMPLETE, Status.WAITING), Arrays.asList(Status.WAITING), Collections.emptyList(), false));
    }

    @Test
    public void testAggregateStatusInProgress() {
        Assert.assertEquals(Status.IN_PROGRESS, PlanUtils.getAggregateStatus("foo",
                Arrays.asList(Status.PREPARED, Status.WAITING), Collections.emptyList(), Collections.emptyList(), false));
        Assert.assertEquals(Status.IN_PROGRESS, PlanUtils.getAggregateStatus("foo",
                Arrays.asList(Status.COMPLETE, Status.IN_PROGRESS), Arrays.asList(Status.IN_PROGRESS), Collections.emptyList(), false));
        Assert.assertEquals(Status.IN_PROGRESS, PlanUtils.getAggregateStatus("foo",
                Arrays.asList(Status.COMPLETE, Status.PENDING), Arrays.asList(Status.PENDING), Collections.emptyList(), false));
        Assert.assertEquals(Status.IN_PROGRESS, PlanUtils.getAggregateStatus("foo",
                Arrays.asList(Status.COMPLETE, Status.STARTING), Arrays.asList(Status.STARTING), Collections.emptyList(), false));
        Assert.assertEquals(Status.IN_PROGRESS, PlanUtils.getAggregateStatus("foo",
                Arrays.asList(Status.COMPLETE, Status.STARTED), Arrays.asList(Status.STARTED), Collections.emptyList(), false));
    }

    @Test
    public void testAggregateStatusPending() {
        Assert.assertEquals(Status.PENDING, PlanUtils.getAggregateStatus("foo",
                Arrays.asList(Status.PENDING, Status.PENDING), Arrays.asList(Status.PENDING), Collections.emptyList(), false));
    }

    @Test
    public void testAggregateStatusStarting() {
        Assert.assertEquals(Status.STARTING, PlanUtils.getAggregateStatus("foo",
                Arrays.asList(Status.STARTING, Status.PENDING), Arrays.asList(Status.STARTING), Collections.emptyList(), false));
    }

    @Test
    public void testAggregateStatusStarted() {
        Assert.assertEquals(Status.STARTED, PlanUtils.getAggregateStatus("foo",
                Arrays.asList(Status.STARTED, Status.PENDING), Arrays.asList(Status.STARTED), Collections.emptyList(), false));
    }

    @Test
    public void testStepNoDirtyAssets() {
        Assert.assertTrue(PlanUtils.isEligible(step, Collections.emptyList()));
    }

    @Test
    public void testStepMatchingDirtyAsset() {
        Collection<PodInstanceRequirement> dirtyAssets = Arrays.asList(step.getPodInstanceRequirement().get());
        Assert.assertFalse(PlanUtils.isEligible(step, dirtyAssets));
    }

    @Test
    public void testInterruptedStep() {
        step.interrupt();
        Assert.assertFalse(PlanUtils.isEligible(step, Collections.emptyList()));
        step.proceed();
        Assert.assertTrue(PlanUtils.isEligible(step, Collections.emptyList()));
    }

    @Test
    public void testCompletedStep() {
        step.forceComplete();
        Assert.assertFalse(PlanUtils.isEligible(step, Collections.emptyList()));
        step.restart();
        Assert.assertTrue(PlanUtils.isEligible(step, Collections.emptyList()));
    }
}
