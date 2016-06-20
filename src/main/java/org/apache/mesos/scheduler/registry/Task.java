package org.apache.mesos.scheduler.registry;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.TaskRequirement;

import java.util.function.Predicate;

/**
 * A task is an almost immutable object, which contains the TaskInfo, TaskStatus, name,
 * and how to launch it. The TaskStatus is mutable, but the status itself is immutable,
 * and the field is volatile, to ensure consistent updates. A given Task will never reset
 * its status "backwards"--instead, a new Task will be created if the goal is to restart.
 */
public class Task {
    private final Protos.TaskInfo taskInfo;
    private volatile Protos.TaskStatus taskStatus = null;
    private final OfferRequirement requirement;
    private final String name;

    protected static Task createTask(String name, OfferRequirement requirement) {
        if (requirement.getTaskRequirements().size() != 1) {
            throw new RuntimeException("Must be exactly one task requirement!");
        }
        TaskRequirement taskReq = requirement.getTaskRequirements().iterator().next();
        Protos.TaskInfo info = requirement.getTaskRequirements().iterator().next().getTaskInfo();
        return new Task(name, info, requirement);
    }

    private Task(String name, Protos.TaskInfo info, OfferRequirement requirement) {
        this.name = name;
        this.requirement = requirement;
        this.taskInfo = info;
    }

    public Protos.TaskInfo getTaskInfo() {
        return taskInfo;
    }

    public void launch() {
        taskStatus = Protos.TaskStatus.newBuilder()
                .setTaskId(taskInfo.getTaskId())
                .setState(Protos.TaskState.TASK_STAGING)
                .build();
    }

    public void updateStatus(Protos.TaskStatus newStatus) {
        this.taskStatus = newStatus;
    }

    public boolean hasStatus() {
        return taskStatus != null;
    }

    public Protos.TaskStatus getTaskStatus() {
        return taskStatus;
    }

    public OfferRequirement getRequirement() {
        return requirement;
    }

    public String getName() {
        return name;
    }

    //TODO should this have a timeout version?
    public void waitForStatus(Predicate<Protos.TaskStatus> pred)
            throws InterruptedException {
        while (!pred.test(this.taskStatus)) {
            this.wait();
        }
    }
}
