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

        // TODO: start by reconciling any existing tasks, and then doing passive reconciliation (see AbstractScheduler)
        //ticks.add(Send.offerForPod("template"));
        ticks.add(Expect.reconciledImplicitly());
        //ticks.add(Expect.declinedLastOffer());

        // TODO: then start accepting offers (launch is inside operations passed to acceptOffers())
        ticks.add(Send.offerForPod("template"));
        ticks.add(Expect.acceptedLastOffer());

        // TODO: trigger status updates and check scheduler does things, such as killing tasks or accepting offers in
        // following cycles
        ticks.add(Send.taskStatus("template-0-node", Protos.TaskState.TASK_RUNNING));
        ticks.add(Send.taskStatus("template-0-node", Protos.TaskState.TASK_FAILED));
        ticks.add(Expect.killedTask("template-0-node"));

        new ServiceTestRunner().run(ticks);
    }
}
