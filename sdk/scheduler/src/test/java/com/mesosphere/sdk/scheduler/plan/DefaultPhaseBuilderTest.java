package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.scheduler.plan.strategy.DependencyStrategyHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.Mockito.when;

/**
 * This class tests the DefaultPhaseBuilder.
 */
public class DefaultPhaseBuilderTest {
    private static final String phaseName = "test-phase";
    private DefaultPhaseBuilder phaseBuilder;

    @Mock
    Step step0;
    @Mock
    Step step1;
    @Mock
    Step step2;
    @Mock
    Step step3;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);

        when(step0.getName()).thenReturn("step0");
        when(step1.getName()).thenReturn("step1");
        when(step2.getName()).thenReturn("step2");
        when(step3.getName()).thenReturn("step3");

        when(step0.getAsset()).thenReturn(Optional.of("step0"));
        when(step1.getAsset()).thenReturn(Optional.of("step1"));
        when(step2.getAsset()).thenReturn(Optional.of("step2"));
        when(step3.getAsset()).thenReturn(Optional.of("step3"));

        when(step0.isPending()).thenReturn(true);
        when(step1.isPending()).thenReturn(true);
        when(step2.isPending()).thenReturn(true);
        when(step3.isPending()).thenReturn(true);

        phaseBuilder = new DefaultPhaseBuilder(phaseName);
    }

    @Test
    public void testBuildSerialPlan() {
        phaseBuilder.addDependency(step2, step1);
        phaseBuilder.addDependency(step1, step0);
        DefaultPhase phase = phaseBuilder.build();

        Assert.assertEquals(step0, getCandidates(phase).iterator().next());

        when(step0.isComplete()).thenReturn(true);
        when(step0.isPending()).thenReturn(false);
        Assert.assertEquals(step1, getCandidates(phase).iterator().next());

        // Try again, shouldn't change.
        Assert.assertEquals(step1, getCandidates(phase).iterator().next());

        when(step1.isComplete()).thenReturn(true);
        when(step1.isPending()).thenReturn(false);
        Assert.assertEquals(step2, getCandidates(phase).iterator().next());

        when(step2.isComplete()).thenReturn(true);
        when(step2.isPending()).thenReturn(false);
        Assert.assertTrue(getCandidates(phase).isEmpty());
    }

    @Test
    public void testBuildParallelPlan() throws DependencyStrategyHelper.InvalidDependencyException {
        phaseBuilder.add(step0);
        phaseBuilder.add(step1);
        phaseBuilder.add(step2);
        DefaultPhase phase = phaseBuilder.build();

        Assert.assertEquals(3, getCandidates(phase).size());

        when(step0.isComplete()).thenReturn(true);
        when(step0.isPending()).thenReturn(false);
        Assert.assertEquals(2, getCandidates(phase).size());

        // Try again, shouldn't change.
        Assert.assertEquals(2, getCandidates(phase).size());

        when(step2.isComplete()).thenReturn(true);
        when(step2.isPending()).thenReturn(false);
        Assert.assertEquals(1, getCandidates(phase).size());
        Assert.assertEquals(step1, getCandidates(phase).iterator().next());

        when(step1.isComplete()).thenReturn(true);
        when(step1.isPending()).thenReturn(false);
        Assert.assertTrue(getCandidates(phase).isEmpty());
    }

    @Test
    public void testBuildDiamondPlan() {
        phaseBuilder.addDependency(step3, step1);
        phaseBuilder.addDependency(step3, step2);
        phaseBuilder.addDependency(step1, step0);
        phaseBuilder.addDependency(step2, step0);
        DefaultPhase phase = phaseBuilder.build();

        Assert.assertEquals(1, getCandidates(phase).size());
        Assert.assertEquals(step0, getCandidates(phase).iterator().next());

        when(step0.isComplete()).thenReturn(true);
        when(step0.isPending()).thenReturn(false);
        Assert.assertEquals(2, getCandidates(phase).size());

        when(step1.isComplete()).thenReturn(true);
        when(step1.isPending()).thenReturn(false);
        Assert.assertEquals(1, getCandidates(phase).size());
        Assert.assertEquals(step2, getCandidates(phase).iterator().next());

        // Try again, shouldn't change.
        Assert.assertEquals(1, getCandidates(phase).size());
        Assert.assertEquals(step2, getCandidates(phase).iterator().next());

        when(step2.isComplete()).thenReturn(true);
        when(step2.isPending()).thenReturn(false);
        Assert.assertEquals(1, getCandidates(phase).size());
        Assert.assertEquals(step3, getCandidates(phase).iterator().next());

        when(step3.isComplete()).thenReturn(true);
        when(step3.isPending()).thenReturn(false);
        Assert.assertTrue(getCandidates(phase).isEmpty());
    }

    private static Collection<Step> getCandidates(Phase phase) {
        return phase.getStrategy().getCandidates(phase.getChildren(), Collections.emptyList());
    }
}
