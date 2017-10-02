package com.mesosphere.sdk.testing;

import java.util.Collection;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;

import static org.mockito.Mockito.mock;

public class ServiceTestRunner {

    public void run(ServiceTestBuilder builder, Collection<SimulationTick> ticks) throws Exception {
        ServiceTestResult result = builder.render();

        // TODO: start by reconciling any existing tasks, and then doing passive reconciliation (see AbstractScheduler)
        ticks.add(Send.offerForPod(result.getServiceSpec().getPods().get(0)));
        ticks.add(Expect.reconciledAny());
        ticks.add(Expect.declinedLastOffer());

        // TODO: then start accepting offers (launch is inside operations passed to acceptOffers())
        ticks.add(Send.offerForPod(result.getServiceSpec().getPods().get(0)));
        ticks.add(Expect.acceptedLastOffer());

        // TODO: trigger status updates and check scheduler does things, such as killing tasks or accepting offers in
        // following cycles
        ticks.add(Send.taskStatus("pod-0-task", Protos.TaskState.TASK_RUNNING));
        ticks.add(Send.taskStatus("pod-0-task", Protos.TaskState.TASK_FAILED));
        ticks.add(Expect.killedTask("pod-0-task"));

        ClusterState state = new ClusterState();
        SchedulerDriver mockDriver = mock(SchedulerDriver.class);
        for (SimulationTick tick : ticks) {
            if (tick instanceof Expect) {
                ((Expect) tick).expect(state, mockDriver);
            } else if (tick instanceof Send) {
                ((Send) tick).run(state, mockDriver, result.getMesosScheduler());
            } else {
                throw new IllegalArgumentException(String.format("Unrecognized tick type: %s", tick));
            }
        }
    }
}
