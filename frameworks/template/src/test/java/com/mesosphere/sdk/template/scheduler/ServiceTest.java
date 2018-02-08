package com.mesosphere.sdk.template.scheduler;

import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.testing.*;
import org.apache.mesos.Protos;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;

public class ServiceTest {

    private static final String VALID_HOSTNAME_CONSTRAINT = "[[\"hostname\", \"UNIQUE\"]]";
    private static final String INVALID_HOSTNAME_CONSTRAINT = "[[\\\"hostname\\\", \"UNIQUE\"]]";

    @Test
    public void testSpec() throws Exception {
        new ServiceTestRunner().run(getDeploymentTicks());
    }


    @Test
    public void testValidPlacementConstraint() throws Exception {
        ServiceTestRunner serviceTestRunner = new ServiceTestRunner().setSchedulerEnv("NODE_PLACEMENT", VALID_HOSTNAME_CONSTRAINT);
        serviceTestRunner.run(getDeploymentTicks());
    }

    @Test(expected = IllegalStateException.class)
    public void testInvalidPlacementConstraint() throws Exception {
        new ServiceTestRunner().setSchedulerEnv("NODE_PLACEMENT", INVALID_HOSTNAME_CONSTRAINT).run(getDeploymentTicks());
    }

    @Test
    public void testSwitchToInvalidPlacementConstraint() throws Exception {
        ServiceTestResult initial = new ServiceTestRunner().setSchedulerEnv("NODE_PLACEMENT", VALID_HOSTNAME_CONSTRAINT).run(getDeploymentTicks());


        Collection<SimulationTick> ticks = new ArrayList<>();
        ticks.add(Send.register());
        ticks.add(Expect.planStatus("deploy", Status.ERROR));

        new ServiceTestRunner().setState(initial).setSchedulerEnv("NODE_PLACEMENT", INVALID_HOSTNAME_CONSTRAINT).run(ticks);

    }

    private static Collection<SimulationTick> getDeploymentTicks() {
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

        return ticks;
    }


}
