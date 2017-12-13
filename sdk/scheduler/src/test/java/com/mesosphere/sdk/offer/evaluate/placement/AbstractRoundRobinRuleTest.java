package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.PodTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

/**
 * This class tests the {@link AbstractRoundRobinRule} class.
 */
public class AbstractRoundRobinRuleTest {

    @Test
    public void offerContainsNoValue() {
        TestRoundRobinRule rule = new TestRoundRobinRule(
                AnyMatcher.create(),
                1,
                null,
                null);

        Assert.assertFalse(
                rule.filter(
                        OfferTestUtils.getEmptyOfferBuilder().build(),
                        PodTestUtils.getPodInstance(0),
                        Collections.emptyList())
                        .isPassing());
    }

    @Test
    public void noTasksMatch() {
        TestRoundRobinRule rule = new TestRoundRobinRule(
                ExactMatcher.create("banana"),
                1,
                "key0",
                null);

        Assert.assertTrue(
                rule.filter(
                        OfferTestUtils.getEmptyOfferBuilder().build(),
                        PodTestUtils.getPodInstance(0),
                        Arrays.asList(TestConstants.TASK_INFO))
                        .isPassing());
    }

    @Test
    public void allTasksMatch() {
        TestRoundRobinRule rule = new TestRoundRobinRule(
                AnyMatcher.create(),
                1,
                "key0",
                null);

        Assert.assertTrue(
                rule.filter(
                        OfferTestUtils.getEmptyOfferBuilder().build(),
                        PodTestUtils.getPodInstance(0),
                        Arrays.asList(TestConstants.TASK_INFO))
                        .isPassing());
    }

    /**
     * The minimum footprint is 2 agents.  1 Task has already been launched with key0.  The offer has key0, so the offer
     * should be rejected
     */
    @Test
    public void rejectDueToInsufficientSpread() {
        TestRoundRobinRule rule = new TestRoundRobinRule(
                AnyMatcher.create(),
                2,
                "key0",
                Arrays.asList("key0"));

        Assert.assertFalse(
                rule.filter(
                        OfferTestUtils.getEmptyOfferBuilder().build(),
                        PodTestUtils.getPodInstance(0),
                        getDummyTasks(1))
                        .isPassing());
    }

    /**
     * The minimum footprint is 2 agents.  1 Task has already been launched with key0.  The offer has key1, so the offer
     * should be accepted
     */
    @Test
    public void acceptSufficientSpread() {
        TestRoundRobinRule rule = new TestRoundRobinRule(
                AnyMatcher.create(),
                2,
                "key1",
                Arrays.asList("key0"));

        Assert.assertTrue(
                rule.filter(
                        OfferTestUtils.getEmptyOfferBuilder().build(),
                        PodTestUtils.getPodInstance(0),
                        getDummyTasks(1))
                        .isPassing());
    }

    /**
     * The minimum footprint is 2 agents.  2 Tasks have been launched with key0 and key1, respectively.
     * The offer has key0, so the offer should be accepted
     */
    @Test
    public void acceptSecondLayerOfRoundRobining() {
        TestRoundRobinRule rule = new TestRoundRobinRule(
                AnyMatcher.create(),
                2,
                "key0",
                Arrays.asList("key0", "key1"));

        Assert.assertTrue(
                rule.filter(
                        OfferTestUtils.getEmptyOfferBuilder().build(),
                        PodTestUtils.getPodInstance(0),
                        getDummyTasks(2))
                        .isPassing());
    }

    /**
     * The minimum footprint is 2 agents.  3 Tasks have been launched with key0, key1 and key0, respectively.
     * The offer has key0, so the offer should be rejected.
     */
    @Test
    public void rejectFullKey() {
        TestRoundRobinRule rule = new TestRoundRobinRule(
                AnyMatcher.create(),
                2,
                "key0",
                Arrays.asList("key0", "key1", "key0"));

        Assert.assertFalse(
                rule.filter(
                        OfferTestUtils.getEmptyOfferBuilder().build(),
                        PodTestUtils.getPodInstance(0),
                        getDummyTasks(3))
                        .isPassing());
    }

    private List<Protos.TaskInfo> getDummyTasks(int count) {
        List<Protos.TaskInfo> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tasks.add(TestConstants.TASK_INFO);
        }

        return tasks;
    }

    private static class TestRoundRobinRule extends AbstractRoundRobinRule {
        private final String offerKey;
        private final List<String> taskKeys;
        private int index = 0;

        public TestRoundRobinRule(
                StringMatcher taskFilter,
                Integer distinctValueCount,
                String offerKey,
                List<String> taskKeys) {
            super(taskFilter, Optional.ofNullable(distinctValueCount));
            this.offerKey = offerKey;
            this.taskKeys = taskKeys;
        }

        @Override
        protected String getKey(Protos.Offer offer) {
            return offerKey;
        }

        @Override
        protected String getKey(Protos.TaskInfo task) {
            if (taskKeys != null) {
                return taskKeys.get(index++);
            } else {
                return null;
            }
        }

        @Override
        public Collection<PlacementField> getPlacementFields() {
            return Collections.emptyList();
        }
    }
}
