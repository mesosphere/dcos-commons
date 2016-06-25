package org.apache.mesos.scheduler.txnplan.ops;

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
public class KillTaskOp implements Operation {
    private final TaskID id;
    private final String name;

    private KillTaskOp() {
        id = null;
        name = null;
    }

    private KillTaskOp(TaskID id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public void doAction(TaskRegistry registry, OperationDriver driver) throws InterruptedException {
        if (!registry.getAllTasks().stream()
                .anyMatch(t ->
                        t.getName().equals(name)
                                && t.getTaskInfo().getTaskId().equals(id))) {
            throw new RuntimeException("Attempted to kill task "
                    + name
                    + "which doesn't exist, aborting");
        }
        Task task = registry.getTask(name);
        while (!TaskUtils.isTerminated(task.getLatestTaskStatus())) {
            registry.getSchedulerDriver().killTask(id);
            task.wait();
        }
        registry.destroyTask(name);
    }

    @Override
    public void rollback(TaskRegistry registry, OperationDriver driver) {
        throw new RuntimeException("Cannot roll back a kill operation!");
    }

    @Override
    public Collection<String> lockedTasks() {
        return Collections.singletonList(name);
    }

    public static KillTaskOp make(Task task) {
        return new KillTaskOp(task.getTaskInfo().getTaskId(), task.getName());
    }
}
