package org.apache.mesos.scheduler.txnplan.example;

import org.apache.mesos.scheduler.txnplan.Plan;
import org.apache.mesos.scheduler.txnplan.Step;
import org.apache.mesos.scheduler.txnplan.ops.CreateTaskOp;

/**
 * Created by dgrnbrg on 6/20/16.
 */
public class Example {
    public static void main(String[] args) {
        Plan plan = new Plan();
        /**
        Step node1 = plan.startWith(CreateTaskOp.make("node-1", null));
        Step node2 = plan.startWith(CreateTaskOp.make("node-2", null));
        Step ready = plan.afterAll(
                new StatusCheckOp(),
                node1,
                node2
        );
        Step configure = plan.afterAll(
                new ConfigureDBOp("node-1"),
                ready
        );
        scheduler.submit(plan);

        Plan plan = scheduler.createPlan();
         */
        Step node1 = plan.step(CreateTaskOp.make("node-1", null));
        Step node2 = plan.step(CreateTaskOp.make("node-2", null));
        Step ready = plan.step(new StatusCheckOp());
        ready.requires(node1);
        ready.requires(node2);
        Step configure = plan.step(new ConfigureDBOp("node-1"));
        configure.requires(ready);
        //plan.submit();
    }
}
