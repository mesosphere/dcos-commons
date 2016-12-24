package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Element;
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
 * This class tests the {@link CanaryStrategy}.
 */
@SuppressWarnings("unchecked")
public class CanaryStrategyTest {
    @Mock Element parentElement;
    @Mock Element el0;
    @Mock Element el1;
    @Mock Element el2;

    private CanaryStrategy strategy;
    private List<Element> Elements;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        strategy = new CanaryStrategy();

        when(el0.getStrategy()).thenReturn(new SerialStrategy<>());
        when(el1.getStrategy()).thenReturn(new SerialStrategy<>());
        when(el2.getStrategy()).thenReturn(new SerialStrategy<>());

        when(el0.getName()).thenReturn("step0");
        when(el1.getName()).thenReturn("step1");
        when(el2.getName()).thenReturn("step2");

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

        parentElement.getChildren().forEach(element -> Assert.assertTrue(((Element) element).isComplete()));
    }

    @Test
    public void testSingleElementCanaryExecution() {
        when(parentElement.getChildren()).thenReturn(Arrays.asList(el0));

        // Initially no candidates should be returned
        Assert.assertTrue(strategy.getCandidates(parentElement, Collections.emptyList()).isEmpty());

        // Proceed the first time.
        strategy.proceed();
        Assert.assertEquals(1, strategy.getCandidates(parentElement, Collections.emptyList()).size());
        Assert.assertEquals(el0, strategy.getCandidates(parentElement, Collections.emptyList()).iterator().next());

        when(el0.isComplete()).thenReturn(true);
        when(el0.isPending()).thenReturn(false);
        Assert.assertTrue(strategy.getCandidates(parentElement, Collections.emptyList()).isEmpty());

        parentElement.getChildren().forEach(element -> Assert.assertTrue(((Element) element).isComplete()));
    }

    @Test
    public void testEmptyCanaryExecution() {
        when(parentElement.getChildren()).thenReturn(Collections.emptyList());

        // Initially no candidates should be returned
        Assert.assertTrue(strategy.getCandidates(parentElement, Collections.emptyList()).isEmpty());

        strategy.proceed();
        Assert.assertTrue(strategy.getCandidates(parentElement, Collections.emptyList()).isEmpty());
    }
}
