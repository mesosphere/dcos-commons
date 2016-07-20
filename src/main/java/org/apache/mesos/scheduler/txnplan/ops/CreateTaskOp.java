package org.apache.mesos.scheduler.txnplan.ops;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.scheduler.registry.Task;
import org.apache.mesos.scheduler.registry.TaskRegistry;
import org.apache.mesos.scheduler.txnplan.Operation;
import org.apache.mesos.scheduler.txnplan.OperationDriver;

import java.util.Collection;
import java.util.Collections;

import static org.apache.mesos.Protos.*;

/**
 * Created by dgrnbrg on 6/20/16.
 */
public class CreateTaskOp implements Operation{
    private String name;
    private OfferRequirement offerRequirement;
    private boolean cleanupOnRollback;

    private CreateTaskOp() {
        name = null;
        offerRequirement = null;
        cleanupOnRollback = false;
    }

    private CreateTaskOp(String name, OfferRequirement offerRequirement, boolean cleanupOnRollback) {
        this.name = name;
        this.offerRequirement = offerRequirement;
        this.cleanupOnRollback = cleanupOnRollback;
    }

    @Override
    public void doAction(TaskRegistry registry, OperationDriver driver) throws Exception {
        driver.info("Creating new task: " + name);
        TaskID newID = registry.createTask(name, offerRequirement);
        driver.save(newID);
        driver.info("Waiting for the task named " + name + " with id " + newID + " to start");
        registry.getTask(name).waitForStatus(s -> s.getState().equals(TaskState.TASK_RUNNING));
        driver.info("Task " + name + " started successfully");
    }

    @Override
    public void unravel(TaskRegistry registry, OperationDriver driver) throws Exception {
        if (cleanupOnRollback) {
            byte[] data = (byte[]) driver.load();
            if (data != null) {
                try {
                    TaskID id = TaskID.parseFrom(data);
                    Task task = registry.getTask(name);
                    while (!TaskUtils.isTerminated(task.getLatestTaskStatus())) {
                        registry.getSchedulerDriver().killTask(id);
                        task.wait();
                    }
                } catch (InvalidProtocolBufferException e) {
                    driver.error("Couldn't parse TaskID from CreateTaskOp("
                            + name
                            + ") state...skipping");
                }
            }
            registry.destroyTask(name);
        }
    }

    @Override
    public Collection<String> lockedTasks() {
        return Collections.singletonList(name);
    }

    /**
     * Factory method to create an operation which launches a task as described below.
     *
     * @param name The name of the task
     * @param requirement The specification of the container in which to launch the task
     * @param cleanupOnRollback During a plan failure, if true, deallocates the container to clean up. If false, it will never be deallocated.
     * @return The operation to create the task
     */
    public static CreateTaskOp make(String name, OfferRequirement requirement, boolean cleanupOnRollback) {
        return new CreateTaskOp(name, requirement, cleanupOnRollback);
    }
}
