package org.apache.mesos.scheduler.plan.strategy;

import org.apache.mesos.scheduler.plan.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;

/**
 * This class tests the {@link SerialStrategy}.
 */
@SuppressWarnings("unchecked")
public class SerialStrategyTest {
    @Mock Element parentElement;
    @Mock Element el0;
    @Mock Element el1;
    @Mock Element el2;
    private Phase phase;

    private SerialStrategy strategy;
    private List<Element> Elements;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        strategy = new SerialStrategy();

        when(el0.getStrategy()).thenReturn(new SerialStrategy<>());
        when(el1.getStrategy()).thenReturn(new SerialStrategy<>());
        when(el2.getStrategy()).thenReturn(new SerialStrategy<>());

        when(el0.getName()).thenReturn("block0");
        when(el1.getName()).thenReturn("block1");
        when(el2.getName()).thenReturn("block2");

        when(el0.isPending()).thenReturn(true);
        when(el1.isPending()).thenReturn(true);
        when(el2.isPending()).thenReturn(true);

        Elements = Arrays.asList(el0, el1, el2);
        when(parentElement.getChildren()).thenReturn(Elements);

        phase = new DefaultPhase(
                "phase-0",
                Arrays.asList(new TestBlock(), new TestBlock()),
                strategy,
                Collections.emptyList());
    }

    @Test
    public void testSerialExecution() {
        Assert.assertEquals(1, strategy.getCandidates(parentElement, Collections.emptyList()).size());
        Assert.assertEquals(el0, strategy.getCandidates(parentElement, Collections.emptyList()).iterator().next());

        when(el0.isComplete()).thenReturn(true);
        when(el0.isPending()).thenReturn(false);
        Assert.assertEquals(1, strategy.getCandidates(parentElement, Collections.emptyList()).size());
        Assert.assertEquals(el1, strategy.getCandidates(parentElement, Collections.emptyList()).iterator().next());

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
        // Can't launch because asset is dirty
        Assert.assertEquals(0, strategy.getCandidates(parentElement, Arrays.asList(el0.getName())).size());
        // Can launch now
        Assert.assertEquals(1, strategy.getCandidates(parentElement, Collections.emptyList()).size());
        Assert.assertEquals(el0, strategy.getCandidates(parentElement, Collections.emptyList()).iterator().next());

        when(el0.isComplete()).thenReturn(true);
        when(el0.isPending()).thenReturn(false);
        // Can launch because element 0 is dirty, but it's complete now.
        Assert.assertEquals(1, strategy.getCandidates(parentElement, Arrays.asList(el0.getName())).size());
        // Can't launch because asset is dirty
        Assert.assertEquals(0, strategy.getCandidates(parentElement, Arrays.asList(el1.getName())).size());
        // Can't launch because asset is dirty
        Assert.assertEquals(1, strategy.getCandidates(parentElement, Collections.emptyList()).size());

        when(el1.isComplete()).thenReturn(true);
        when(el1.isPending()).thenReturn(false);
        Assert.assertEquals(1, strategy.getCandidates(parentElement, Collections.emptyList()).size());
        Assert.assertEquals(el2, strategy.getCandidates(parentElement, Collections.emptyList()).iterator().next());
    }

    @Test
    public void testProceedInterrupt() {
        TestBlock block0 = (TestBlock)phase.getChildren().get(0);
        TestBlock block1 = (TestBlock)phase.getChildren().get(1);

        phase.getStrategy().interrupt();
        Assert.assertTrue(strategy.getCandidates(phase, Collections.emptyList()).isEmpty());

        phase.getStrategy().proceed();
        Assert.assertEquals(block0, strategy.getCandidates(phase, Collections.emptyList()).iterator().next());

        phase.getStrategy().interrupt();
        Assert.assertTrue(strategy.getCandidates(phase, Collections.emptyList()).isEmpty());

        block0.setStatus(Status.COMPLETE);
        Assert.assertTrue(strategy.getCandidates(phase, Collections.emptyList()).isEmpty());

        phase.getStrategy().proceed();
        Assert.assertEquals(block1, strategy.getCandidates(phase, Collections.emptyList()).iterator().next());

        block1.setStatus(Status.COMPLETE);
        Assert.assertTrue(strategy.getCandidates(phase, Collections.emptyList()).isEmpty());
    }
}
