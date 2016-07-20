package org.apache.mesos.scheduler.txnplan.ops;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.scheduler.registry.TaskRegistry;
import org.apache.mesos.scheduler.txnplan.Operation;
import org.apache.mesos.scheduler.txnplan.OperationDriver;

import java.util.Collection;
import java.util.Collections;

/**
 * Created by dgrnbrg on 7/18/16.
 */
public class ReplaceAttemptOp implements Operation{
    private String name;
    private OfferRequirement offerRequirement;

    private ReplaceAttemptOp() {}

    public ReplaceAttemptOp(String name, OfferRequirement offerRequirement) {
        this.name = name;
        this.offerRequirement = offerRequirement;
    }

    @Override
    public void doAction(TaskRegistry registry, OperationDriver driver) throws Exception {
        TaskID newID = (TaskID) driver.load();
        if (newID == null) {
            driver.info("Replacing task: " + name);
            newID = registry.replaceTask(name, offerRequirement);
            driver.save(newID);
        } else {
            driver.info("Task was already replaced");
        }
        driver.info("Task " + name + " has been registered to be replaced");
    }

    @Override
    public void unravel(TaskRegistry registry, OperationDriver driver) throws Exception {
        throw new RuntimeException("I can't decide if unreplacing makes sense");
    }

    @Override
    public Collection<String> lockedTasks() {
        return Collections.singletonList(name);
    }
}
