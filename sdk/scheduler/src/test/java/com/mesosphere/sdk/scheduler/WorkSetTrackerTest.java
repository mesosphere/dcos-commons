package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.scheduler.plan.TestStep;
import com.mesosphere.sdk.testutils.PodTestUtils;

import org.junit.Assert;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tests for {@link WorkSetTracker}
 */
public class WorkSetTrackerTest {
    private final UUID testUUID = UUID.randomUUID();

    @Test
    public void testNewWork() {
        WorkSetTracker tracker = new WorkSetTracker(Optional.empty());
        tracker.updateWorkSet(getSteps(0));
        Assert.assertTrue(tracker.hasNewWork());
    }

    @Test
    public void testSameWork() {
        WorkSetTracker tracker = new WorkSetTracker(Optional.empty());
        tracker.updateWorkSet(getSteps(0));
        Assert.assertTrue(tracker.hasNewWork());
        tracker.updateWorkSet(getSteps(0));
        Assert.assertFalse(tracker.hasNewWork());
    }

    @Test
    public void testSameWorkShowsUpLater() {
        WorkSetTracker tracker = new WorkSetTracker(Optional.empty());
        tracker.updateWorkSet(getSteps(0));
        Assert.assertTrue(tracker.hasNewWork());
        tracker.updateWorkSet(Collections.emptyList());
        Assert.assertFalse(tracker.hasNewWork());
        tracker.updateWorkSet(getSteps(0));
        Assert.assertTrue(tracker.hasNewWork());
    }

    @Test
    public void testNewWorkSurvivesAcrossWorkSets() {
        WorkSetTracker tracker = new WorkSetTracker(Optional.empty());
        tracker.updateWorkSet(getSteps(0));
        tracker.updateWorkSet(Collections.emptyList());
        Assert.assertTrue(tracker.hasNewWork());
        Assert.assertFalse(tracker.hasNewWork());
        tracker.updateWorkSet(getSteps(0));
        Assert.assertTrue(tracker.hasNewWork());
        Assert.assertFalse(tracker.hasNewWork());
    }

    @Test
    public void testAdditionalNewWork() {
        WorkSetTracker tracker = new WorkSetTracker(Optional.empty());
        tracker.updateWorkSet(getSteps(0));
        Assert.assertTrue(tracker.hasNewWork());
        tracker.updateWorkSet(getSteps(1));
        Assert.assertTrue(tracker.hasNewWork());
    }

    @Test
    public void testEmptyWork() {
        WorkSetTracker tracker = new WorkSetTracker(Optional.empty());
        tracker.updateWorkSet(Collections.emptyList());
        Assert.assertFalse(tracker.hasNewWork());
    }

    private List<Step> getSteps(Integer index) {
        PodInstanceRequirement podInstanceRequirement = PodTestUtils.getPodInstanceRequirement(index);
        return Arrays.asList(
                new TestStep(
                        testUUID,
                        String.format("step-%d", podInstanceRequirement.getPodInstance().getIndex()),
                        podInstanceRequirement));
    }
}
