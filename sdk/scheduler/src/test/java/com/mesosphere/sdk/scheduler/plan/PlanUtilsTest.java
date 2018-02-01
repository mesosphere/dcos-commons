package com.mesosphere.sdk.scheduler.plan;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

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

    private static final PodSpec POD_SPEC = DefaultPodSpec.newBuilder("")
            .type(TestConstants.POD_TYPE)
            .count(1)
            .tasks(Arrays.asList(
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
                mockStateStore);
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
