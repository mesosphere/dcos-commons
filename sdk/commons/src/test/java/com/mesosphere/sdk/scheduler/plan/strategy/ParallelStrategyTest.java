package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Step;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;

/**
 * This class tests the {@link ParallelStrategy}.
 */
@SuppressWarnings("unchecked")
public class ParallelStrategyTest {
    @Mock Phase parentElement;
    @Mock Step el0;
    @Mock Step el1;
    @Mock Step el2;

    private ParallelStrategy strategy;
    private List<Step> elements;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        strategy = new ParallelStrategy();

        when(el0.getStrategy()).thenReturn(new SerialStrategy<>());
        when(el1.getStrategy()).thenReturn(new SerialStrategy<>());
        when(el2.getStrategy()).thenReturn(new SerialStrategy<>());

        when(el0.getName()).thenReturn("step0");
        when(el1.getName()).thenReturn("step1");
        when(el2.getName()).thenReturn("step2");

        when(el0.getAsset()).thenReturn(Optional.of("step0"));
        when(el1.getAsset()).thenReturn(Optional.of("step1"));
        when(el2.getAsset()).thenReturn(Optional.of("step2"));

        when(el0.isPending()).thenReturn(true);
        when(el1.isPending()).thenReturn(true);
        when(el2.isPending()).thenReturn(true);

        elements = Arrays.asList(el0, el1, el2);
        when(parentElement.getChildren()).thenReturn(elements);
    }

    @Test
    public void testParallelExecution() {
        Assert.assertEquals(3, strategy.getCandidates(parentElement, Collections.emptyList()).size());

        when(el0.isComplete()).thenReturn(true);
        when(el0.isPending()).thenReturn(false);
        Assert.assertEquals(2, strategy.getCandidates(parentElement, Collections.emptyList()).size());

        when(el1.isComplete()).thenReturn(true);
        when(el1.isPending()).thenReturn(false);
        Assert.assertEquals(1, strategy.getCandidates(parentElement, Collections.emptyList()).size());
        Assert.assertEquals(el2, strategy.getCandidates(parentElement, Collections.emptyList()).iterator().next());

        when(el2.isComplete()).thenReturn(true);
        when(el2.isPending()).thenReturn(false);
        Assert.assertTrue(strategy.getCandidates(parentElement, Collections.emptyList()).isEmpty());
    }

    @Test
    public void testDirtyAssetAvoidance() {
        // Can't launch because all assets are dirty
        Assert.assertTrue(strategy.getCandidates(
                parentElement,
                Arrays.asList(el0.getName(), el1.getName(), el2.getName())).isEmpty());

        // Can launch all now
        Assert.assertEquals(3, strategy.getCandidates(parentElement, Collections.emptyList()).size());

        when(el0.isComplete()).thenReturn(true);
        when(el0.isPending()).thenReturn(false);
        // Can launch two because element 0 is dirty, but it's complete now.
        Assert.assertEquals(2, strategy.getCandidates(parentElement, Arrays.asList(el0.getName())).size());
        // Can launch el2 because el1 is dirty and el0 is complete
        Assert.assertEquals(1, strategy.getCandidates(parentElement, Arrays.asList(el1.getName())).size());
        Assert.assertEquals(el2, strategy.getCandidates(parentElement, Arrays.asList(el1.getName())).iterator().next());

        when(el1.isComplete()).thenReturn(true);
        when(el1.isPending()).thenReturn(false);
        // Can launch el2 because it's the last pending step.
        Assert.assertEquals(1, strategy.getCandidates(parentElement, Collections.emptyList()).size());
        Assert.assertEquals(el2, strategy.getCandidates(parentElement, Collections.emptyList()).iterator().next());

        when(el2.isComplete()).thenReturn(true);
        when(el2.isPending()).thenReturn(false);
        Assert.assertTrue(strategy.getCandidates(parentElement, Collections.emptyList()).isEmpty());
    }
}
