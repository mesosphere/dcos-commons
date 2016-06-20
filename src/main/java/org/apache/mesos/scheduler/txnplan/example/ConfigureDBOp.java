package org.apache.mesos.scheduler.txnplan.example;

import org.apache.mesos.scheduler.registry.TaskRegistry;
import org.apache.mesos.scheduler.txnplan.OperationDriver;
import org.apache.mesos.scheduler.txnplan.ops.UnconditionalOp;

import java.util.Arrays;

/**
 * Created by dgrnbrg on 6/20/16.
 */
public class ConfigureDBOp extends UnconditionalOp {
    /**
     * Configures the DB using the given node
     * @param node
     */
    public ConfigureDBOp(String node) {
        //the parent constructor takes the exclusion list as the argument
        super(Arrays.asList(node));
    }

    @Override
    public void doAction(TaskRegistry registry, OperationDriver driver) throws Exception {
        System.out.println("Finally doing DB configuration");
    }
}
