package com.mesosphere.sdk.template.scheduler;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.mesos.Protos;
import org.junit.Test;

import com.mesosphere.sdk.testing.Expect;
import com.mesosphere.sdk.testing.Send;
import com.mesosphere.sdk.testing.ServiceTestRunner;
import com.mesosphere.sdk.testing.SimulationTick;

public class ServiceTest {

    @Test
    public void testSpec() throws Exception {
        Collection<SimulationTick> ticks = new ArrayList<>();

        ticks.add(Send.register());

        ticks.add(Expect.reconciledImplicitly());

        // "node" task fails to launch on first attempt, without having entered RUNNING.
        // Scheduler should attempt to replace task automatically:
        ticks.add(Send.offerBuilder("template").build());
        ticks.add(Expect.launchedTasks("template-0-node"));
        ticks.add(Send.taskStatus("template-0-node", Protos.TaskState.TASK_LOST).build());
        ticks.add(Expect.killedTask("template-0-node"));
        // now that the task is dead to the scheduler, its resources should be unreserved the next time they're offered
        ticks.add(Send.offerBuilder("template").setResourcesFromPod(0).build());
        ticks.add(Expect.unreservedTasks("template-0-node"));

        // Send a fresh offer and check that the task is relaunched there:
        ticks.add(Send.offerBuilder("template").build());
        ticks.add(Expect.launchedTasks("template-0-node"));
        ticks.add(Send.taskStatus("template-0-node", Protos.TaskState.TASK_RUNNING).build());
        ticks.add(Send.taskStatus("template-0-node", Protos.TaskState.TASK_LOST).build());

        // Now, because the task had entered RUNNING, it should be "stuck" to the earlier offer:
        ticks.add(Send.offerBuilder("template").build());
        ticks.add(Expect.declinedLastOffer());

        // It accepts the offer with the correct resource ids:
        ticks.add(Send.offerBuilder("template").setResourcesFromPod(0).build());
        ticks.add(Expect.launchedTasks("template-0-node"));
        ticks.add(Send.taskStatus("template-0-node", Protos.TaskState.TASK_RUNNING).build());

        // With the pod launched again, the scheduler now ignores the same resources if they're reoffered:
        ticks.add(Send.offerBuilder("template").setResourcesFromPod(0).build());
        ticks.add(Expect.declinedLastOffer());

        ticks.add(Expect.allPlansComplete());

        new ServiceTestRunner().run(ticks);
    }
}
