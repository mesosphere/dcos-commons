package com.mesosphere.sdk.helloworld.scheduler;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.mesos.Protos;
import org.junit.Test;

import com.mesosphere.sdk.testing.Expect;
import com.mesosphere.sdk.testing.Send;
import com.mesosphere.sdk.testing.ServiceTestRunner;
import com.mesosphere.sdk.testing.SimulationTick;

/**
 * Checks that custom step definitions in a custom plan all behave as expected.
 *
 * Checks permutations of serial/parallel strategy against serial/parallel/mixed steps
 */
public class CustomStepsTest {

    @Test
    public void testSerialStrategySerialSteps() throws Exception {
        Collection<SimulationTick> ticks = new ArrayList<>();

        ticks.add(Send.register());
        ticks.add(Expect.reconciledImplicitly());

        // Service should only launch hello-0-first, while creating footprint for hello-0-second.
        ticks.add(Send.offerBuilder("hello").setCount(3).build());
        ticks.add(Expect.launchedTasks("hello-0-first"));

        // Send another random offer, check that it isn't accepted (should be looking for hello-0-second's footprint)
        ticks.add(Send.offerBuilder("hello").setCount(3).build());
        ticks.add(Expect.declinedLastOffer());

        // Similarly, sending hello-0's footprint results in a decline because hello-0 hasn't started running:
        ticks.add(Send.offerBuilder("hello").setCount(3).setPodIndexToReoffer(0).build());
        ticks.add(Expect.declinedLastOffer());

        ticks.add(Send.taskStatus("hello-0-first", Protos.TaskState.TASK_RUNNING).build());

        // Now that hello-0-first is running, sending hello-0's footprint results in launching hello-0-second:
        ticks.add(Send.offerBuilder("hello").setCount(3).setPodIndexToReoffer(0).build());
        ticks.add(Expect.launchedTasks("hello-0-second"));

        ticks.add(Send.taskStatus("hello-0-second", Protos.TaskState.TASK_RUNNING).build());

        ticks.add(Expect.samePod("hello-0-first", "hello-0-second"));

        // Now hello-1 may be deployed:

        ticks.add(Send.offerBuilder("hello").setCount(3).build());
        ticks.add(Expect.launchedTasks("hello-1-first"));

        // No more hellos to launch:
        ticks.add(Send.offerBuilder("hello").setCount(3).build());
        ticks.add(Expect.declinedLastOffer());

        // Offer declined until hello-1-first is running:
        ticks.add(Send.offerBuilder("hello").setCount(3).setPodIndexToReoffer(1).build());
        ticks.add(Expect.declinedLastOffer());

        ticks.add(Send.taskStatus("hello-1-first", Protos.TaskState.TASK_RUNNING).build());

        // Now that hello-1-first is running, sending hello-1's footprint results in launching hello-1-second:
        ticks.add(Send.offerBuilder("hello").setCount(3).setPodIndexToReoffer(1).build());
        ticks.add(Expect.launchedTasks("hello-1-second"));

        ticks.add(Send.taskStatus("hello-1-second", Protos.TaskState.TASK_RUNNING).build());

        ticks.add(Expect.samePod("hello-1-first", "hello-1-second"));

        ticks.add(Expect.allPlansComplete());

        new ServiceTestRunner("custom_steps.yml")
                .setSchedulerEnv(
                        "DEPLOY_STRATEGY", "serial",
                        "DEPLOY_STEPS", "[[first], [second]]")
                .run(ticks);
    }

    @Test
    public void testSerialStrategyParallelSteps() throws Exception {
        Collection<SimulationTick> ticks = new ArrayList<>();

        ticks.add(Send.register());
        ticks.add(Expect.reconciledImplicitly());

        // Service should launch hello-0-first and hello-0-second at the same time.
        ticks.add(Send.offerBuilder("hello").setCount(3).build());
        ticks.add(Expect.launchedTasks("hello-0-first", "hello-0-second"));

        ticks.add(Send.taskStatus("hello-0-first", Protos.TaskState.TASK_RUNNING).build());

        // New offer declined since hello-0-second isn't RUNNING yet:
        ticks.add(Send.offerBuilder("hello").setCount(3).build());
        ticks.add(Expect.declinedLastOffer());

        ticks.add(Send.taskStatus("hello-0-second", Protos.TaskState.TASK_RUNNING).build());

        ticks.add(Expect.samePod("hello-0-first", "hello-0-second"));

        // Now hello-1 may be deployed:

        ticks.add(Send.offerBuilder("hello").setCount(3).build());
        ticks.add(Expect.launchedTasks("hello-1-first", "hello-1-second"));

        ticks.add(Send.taskStatus("hello-1-first", Protos.TaskState.TASK_RUNNING).build());
        ticks.add(Send.taskStatus("hello-1-second", Protos.TaskState.TASK_RUNNING).build());

        ticks.add(Expect.samePod("hello-1-first", "hello-1-second"));

        // No more hellos to launch:
        ticks.add(Send.offerBuilder("hello").setCount(3).build());
        ticks.add(Expect.declinedLastOffer());

        ticks.add(Expect.allPlansComplete());

        new ServiceTestRunner("custom_steps.yml")
                .setSchedulerEnv(
                        "DEPLOY_STRATEGY", "serial",
                        "DEPLOY_STEPS", "[[first, second]]")
                .run(ticks);
    }

    @Test(expected = IllegalStateException.class)
    public void testInvalidStep() throws Exception {
        Collection<SimulationTick> ticks = new ArrayList<>();
        new ServiceTestRunner("custom_steps.yml")
                .setSchedulerEnv(
                        "DEPLOY_STRATEGY", "serial",
                        "DEPLOY_STEPS", "[[foo, bar]]")
                .run(ticks);
    }

    @Test
    public void testSerialStrategyMixedSteps() throws Exception {
        Collection<SimulationTick> ticks = new ArrayList<>();

        ticks.add(Send.register());
        ticks.add(Expect.reconciledImplicitly());

        // Service should launch hello-0-first up-front, then wait before launching hello-0-second/third.
        ticks.add(Send.offerBuilder("hello").setCount(3).build());
        ticks.add(Expect.launchedTasks("hello-0-first"));

        // New offer declined since hello-0-first isn't RUNNING yet:
        ticks.add(Send.offerBuilder("hello").setCount(3).setPodIndexToReoffer(0).build());
        ticks.add(Expect.declinedLastOffer()); // hello-0-first not running yet

        ticks.add(Send.taskStatus("hello-0-first", Protos.TaskState.TASK_RUNNING).build());

        // Now hello-0-second/third are deployable, but only if the original pod is reoffered:
        ticks.add(Send.offerBuilder("hello").setCount(3).build());
        ticks.add(Expect.declinedLastOffer()); // need to reoffer hello-0's resources

        ticks.add(Send.offerBuilder("hello").setCount(3).setPodIndexToReoffer(0).build());
        ticks.add(Expect.launchedTasks("hello-0-second", "hello-0-third"));

        // Once their statuses have come back, we can deploy hello-1 the same way:
        ticks.add(Send.taskStatus("hello-0-second", Protos.TaskState.TASK_RUNNING).build());
        ticks.add(Send.taskStatus("hello-0-third", Protos.TaskState.TASK_RUNNING).build());

        ticks.add(Expect.samePod("hello-0-first", "hello-0-second", "hello-0-third"));

        ticks.add(Send.offerBuilder("hello").setCount(3).build());
        ticks.add(Expect.launchedTasks("hello-1-first")); // hello-0 complete

        ticks.add(Send.offerBuilder("hello").setCount(3).setPodIndexToReoffer(1).build());
        ticks.add(Expect.declinedLastOffer()); // hello-1-first not running yet

        ticks.add(Send.taskStatus("hello-1-first", Protos.TaskState.TASK_RUNNING).build());

        ticks.add(Send.offerBuilder("hello").setCount(3).build());
        ticks.add(Expect.declinedLastOffer()); // need to reoffer hello-1's resources

        ticks.add(Send.offerBuilder("hello").setCount(3).setPodIndexToReoffer(1).build());
        ticks.add(Expect.launchedTasks("hello-1-second", "hello-1-third"));

        ticks.add(Send.taskStatus("hello-1-second", Protos.TaskState.TASK_RUNNING).build());
        ticks.add(Send.taskStatus("hello-1-third", Protos.TaskState.TASK_RUNNING).build());

        ticks.add(Expect.samePod("hello-1-first", "hello-1-second", "hello-1-third"));

        // No more hellos to launch:
        ticks.add(Send.offerBuilder("hello").setCount(3).build());
        ticks.add(Expect.declinedLastOffer());

        ticks.add(Expect.allPlansComplete());

        new ServiceTestRunner("custom_steps.yml")
                .setSchedulerEnv(
                        "DEPLOY_STRATEGY", "serial",
                        "DEPLOY_STEPS", "[[first], [second, third]]")
                .run(ticks);
    }

    @Test
    public void testParallelStrategySerialSteps() throws Exception {
        Collection<SimulationTick> ticks = new ArrayList<>();

        ticks.add(Send.register());
        ticks.add(Expect.reconciledImplicitly());

        // Service should launch both hello-0/1-first using the provided offers:
        ticks.add(Send.offerBuilder("hello").setCount(3).build());
        // Launches will end up being split across two offers/agents:
        ticks.add(Expect.launchedTasks(2, "hello-0-first", "hello-1-first"));

        // New offer declined since hello-1/2-first aren't RUNNING yet:
        ticks.add(Send.offerBuilder("hello").setCount(3).setPodIndexToReoffer(0).build());
        ticks.add(Expect.declinedLastOffer()); // hello-0-first not running yet
        ticks.add(Send.offerBuilder("hello").setCount(3).setPodIndexToReoffer(1).build());
        ticks.add(Expect.declinedLastOffer()); // hello-1-first not running yet

        ticks.add(Send.taskStatus("hello-0-first", Protos.TaskState.TASK_RUNNING).build());
        ticks.add(Send.taskStatus("hello-1-first", Protos.TaskState.TASK_RUNNING).build());

        // Now hello-1/2-second are deployable, but only if the original pod is reoffered:
        ticks.add(Send.offerBuilder("hello").setCount(3).build());
        ticks.add(Expect.declinedLastOffer()); // need to reoffer hello-0 or hello-1's resources

        ticks.add(Send.offerBuilder("hello").setCount(3).setPodIndexToReoffer(0).build());
        ticks.add(Expect.launchedTasks("hello-0-second"));
        ticks.add(Send.offerBuilder("hello").setCount(3).setPodIndexToReoffer(1).build());
        ticks.add(Expect.launchedTasks("hello-1-second"));

        ticks.add(Expect.samePod("hello-0-first", "hello-0-second"));
        ticks.add(Expect.samePod("hello-1-first", "hello-1-second"));

        ticks.add(Send.taskStatus("hello-0-second", Protos.TaskState.TASK_RUNNING).build());
        ticks.add(Send.taskStatus("hello-1-second", Protos.TaskState.TASK_RUNNING).build());

        // No more hellos to launch:
        ticks.add(Send.offerBuilder("hello").setCount(3).build());
        ticks.add(Expect.declinedLastOffer());

        ticks.add(Expect.allPlansComplete());

        new ServiceTestRunner("custom_steps.yml")
                .setSchedulerEnv(
                        "DEPLOY_STRATEGY", "parallel",
                        "DEPLOY_STEPS", "[[first], [second]]")
                .run(ticks);
    }

    @Test
    public void testParallelStrategyParallelSteps() throws Exception {
        Collection<SimulationTick> ticks = new ArrayList<>();

        ticks.add(Send.register());
        ticks.add(Expect.reconciledImplicitly());

        // Service should launch all of hello-0/1-first/second using the provided offers:
        ticks.add(Send.offerBuilder("hello").setCount(3).build());
        // Launches will end up being split across two offers/agents:
        ticks.add(Expect.launchedTasks(2, "hello-0-first", "hello-0-second", "hello-1-first", "hello-1-second"));

        ticks.add(Send.taskStatus("hello-0-first", Protos.TaskState.TASK_RUNNING).build());
        ticks.add(Send.taskStatus("hello-0-second", Protos.TaskState.TASK_RUNNING).build());
        ticks.add(Send.taskStatus("hello-1-first", Protos.TaskState.TASK_RUNNING).build());
        ticks.add(Send.taskStatus("hello-1-second", Protos.TaskState.TASK_RUNNING).build());

        ticks.add(Expect.samePod("hello-0-first", "hello-0-second"));
        ticks.add(Expect.samePod("hello-1-first", "hello-1-second"));

        // No more hellos to launch:
        ticks.add(Send.offerBuilder("hello").setCount(3).build());
        ticks.add(Expect.declinedLastOffer());

        ticks.add(Expect.allPlansComplete());

        new ServiceTestRunner("custom_steps.yml")
                .setSchedulerEnv(
                        "DEPLOY_STRATEGY", "parallel",
                        "DEPLOY_STEPS", "[[first, second]]")
                .run(ticks);
    }

    @Test
    public void testParallelStrategyMixedSteps() throws Exception {
        Collection<SimulationTick> ticks = new ArrayList<>();

        ticks.add(Send.register());
        ticks.add(Expect.reconciledImplicitly());

        // Service should launch both hello-0/1-first using the provided offers:
        ticks.add(Send.offerBuilder("hello").setCount(3).build());
        // Launches will end up being split across two offers/agents:
        ticks.add(Expect.launchedTasks(2, "hello-0-first", "hello-1-first"));

        // New offer declined since hello-1/2-first aren't RUNNING yet:
        ticks.add(Send.offerBuilder("hello").setCount(3).setPodIndexToReoffer(0).build());
        ticks.add(Expect.declinedLastOffer()); // hello-0-first not running yet
        ticks.add(Send.offerBuilder("hello").setCount(3).setPodIndexToReoffer(1).build());
        ticks.add(Expect.declinedLastOffer()); // hello-1-first not running yet

        ticks.add(Send.taskStatus("hello-0-first", Protos.TaskState.TASK_RUNNING).build());
        ticks.add(Send.taskStatus("hello-1-first", Protos.TaskState.TASK_RUNNING).build());

        // Now hello-1/2-second/third are deployable, but only if the original pod is reoffered:
        ticks.add(Send.offerBuilder("hello").setCount(3).build());
        ticks.add(Expect.declinedLastOffer()); // need to reoffer hello-0 or hello-1's resources

        ticks.add(Send.offerBuilder("hello").setCount(3).setPodIndexToReoffer(0).build());
        ticks.add(Expect.launchedTasks("hello-0-second", "hello-0-third"));
        ticks.add(Send.offerBuilder("hello").setCount(3).setPodIndexToReoffer(1).build());
        ticks.add(Expect.launchedTasks("hello-1-second", "hello-1-third"));

        ticks.add(Expect.samePod("hello-0-first", "hello-0-second", "hello-0-third"));
        ticks.add(Expect.samePod("hello-1-first", "hello-1-second", "hello-1-third"));

        ticks.add(Send.taskStatus("hello-0-second", Protos.TaskState.TASK_RUNNING).build());
        ticks.add(Send.taskStatus("hello-0-third", Protos.TaskState.TASK_RUNNING).build());
        ticks.add(Send.taskStatus("hello-1-second", Protos.TaskState.TASK_RUNNING).build());
        ticks.add(Send.taskStatus("hello-1-third", Protos.TaskState.TASK_RUNNING).build());

        // No more hellos to launch:
        ticks.add(Send.offerBuilder("hello").setCount(3).build());
        ticks.add(Expect.declinedLastOffer());

        ticks.add(Expect.allPlansComplete());

        new ServiceTestRunner("custom_steps.yml")
                .setSchedulerEnv(
                        "DEPLOY_STRATEGY", "parallel",
                        "DEPLOY_STEPS", "[[first], [second, third]]")
                .run(ticks);
    }
}
