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
        ticks.add(Send.offerBuilder("template").build());
        ticks.add(Expect.launchedTasks("template-0-node"));
        ticks.add(Send.taskStatus("template-0-node", Protos.TaskState.TASK_ERROR).build());

        // Because the task has now been "pinned", a different offer which would fit the task is declined:
        ticks.add(Send.offerBuilder("template").build());
        ticks.add(Expect.declinedLastOffer());

        // It accepts the offer with the correct resource ids:
        ticks.add(Send.offerBuilder("template").setPodIndexToReoffer(0).build());
        ticks.add(Expect.launchedTasks("template-0-node"));
        ticks.add(Send.taskStatus("template-0-node", Protos.TaskState.TASK_RUNNING).build());

        // With the pod now running, the scheduler now ignores the same resources if they're reoffered:
        ticks.add(Send.offerBuilder("template").setPodIndexToReoffer(0).build());
        ticks.add(Expect.declinedLastOffer());

        ticks.add(Expect.allPlansComplete());

        new ServiceTestRunner().run(ticks);
    }
}
