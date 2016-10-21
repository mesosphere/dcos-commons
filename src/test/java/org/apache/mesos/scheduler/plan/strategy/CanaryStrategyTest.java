package org.apache.mesos.scheduler.plan.strategy;

import org.apache.mesos.scheduler.plan.Element;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.powermock.api.mockito.PowerMockito.when;

/**
 * Created by gabriel on 10/20/16.
 */
public class CanaryStrategyTest {
    @Mock
    Element parentElement;
    @Mock
    Element el0;
    @Mock
    Element el1;
    @Mock
    Element el2;

    private CanaryStrategy strategy;
    private List<Element> Elements;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        strategy = new CanaryStrategy();

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
    }

    @Test
    public void testCanaryExecution() {
        // Initially no candidates should be returned
        Assert.assertTrue(strategy.getCandidates(parentElement, Collections.emptyList()).isEmpty());

        // Proceed the first time.
        strategy.proceed();
        Assert.assertEquals(1, strategy.getCandidates(parentElement, Collections.emptyList()).size());
        Assert.assertEquals(el0, strategy.getCandidates(parentElement, Collections.emptyList()).iterator().next());

        when(el0.isComplete()).thenReturn(true);
        when(el0.isPending()).thenReturn(false);
        Assert.assertTrue(strategy.getCandidates(parentElement, Collections.emptyList()).isEmpty());

        // Proceed the second time.
        strategy.proceed();
        Assert.assertEquals(1, strategy.getCandidates(parentElement, Collections.emptyList()).size());
        Assert.assertEquals(el1, strategy.getCandidates(parentElement, Collections.emptyList()).iterator().next());

        // After el1 completes the rest should roll out without intervention.
        when(el1.isComplete()).thenReturn(true);
        when(el1.isPending()).thenReturn(false);
        Assert.assertEquals(1, strategy.getCandidates(parentElement, Collections.emptyList()).size());
        Assert.assertEquals(el2, strategy.getCandidates(parentElement, Collections.emptyList()).iterator().next());

        when(el2.isComplete()).thenReturn(true);
        when(el2.isPending()).thenReturn(false);
        Assert.assertTrue(strategy.getCandidates(parentElement, Collections.emptyList()).isEmpty());
    }
}
