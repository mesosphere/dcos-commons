package org.apache.mesos.scheduler.registry;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.TaskRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A task is a monotonic object, which contains the TaskInfo, TaskStatus, name,
 * and how to launch it. Everything except for the status is immutable; the
 * status is actually monotonic, in that we always append new statuses, so that
 * it's possible to review earlier statuses as well.
 *
 * TODO the implicit periodic reconciler means that we'll grow and grow this task without limit
 * one day this will OOM
 * we need to have a way to garbage collect duplicate reconciliation-originated statuses
 */
public class Task {
    private static final Logger logger = LoggerFactory.getLogger(Task.class);
    private Protos.TaskInfo taskInfo;
    //all accesses to taskStatuses should be synchronized on it
    private List<Protos.TaskStatus> taskStatuses;
    private OfferRequirement requirement;
    private String name;

    protected static Task createTask(String name, OfferRequirement requirement) {
        if (requirement.getTaskRequirements().size() != 1) {
            throw new RuntimeException("Must be exactly one task requirement!");
        }
        TaskRequirement taskReq = requirement.getTaskRequirements().iterator().next();
        Protos.TaskInfo info = requirement.getTaskRequirements().iterator().next().getTaskInfo();
        return new Task(name, info, requirement);
    }

    /**
     * For kryo
     */
    private Task() {}

    private Task(String name, Protos.TaskInfo info, OfferRequirement requirement) {
        this.name = name;
        this.requirement = requirement;
        this.taskInfo = info;
        this.taskStatuses = new ArrayList<>();
    }

    public Protos.TaskInfo getTaskInfo() {
        return taskInfo;
    }

    public void launch() {
        synchronized (taskStatuses) {
            Protos.TaskStatus staging = Protos.TaskStatus.newBuilder()
                    .setTaskId(taskInfo.getTaskId())
                    .setState(Protos.TaskState.TASK_STAGING)
                    .build();
            taskStatuses.add(staging);
        }
    }

    public void updateStatus(Protos.TaskStatus newStatus) {
        synchronized (taskStatuses) {
            taskStatuses.add(newStatus);
        }
    }

    public boolean hasStatus() {
        synchronized (taskStatuses) {
            return !taskStatuses.isEmpty();
        }
    }

    public List<Protos.TaskStatus> getTaskStatuses() {
        synchronized (taskStatuses) {
            return new ArrayList<>(taskStatuses);
        }
    }

    public OfferRequirement getRequirement() {
        return requirement;
    }

    public String getName() {
        return name;
    }

    public Protos.TaskStatus getLatestTaskStatus() {
        synchronized (taskStatuses) {
            if (!hasStatus()) {
                return null;
            } else {
                return taskStatuses.get(taskStatuses.size() - 1);
            }
        }
    }

    //TODO should this have a timeout version?
    public void waitForStatus(Predicate<Protos.TaskStatus> pred)
            throws InterruptedException {
        while (!hasStatus() || !pred.test(getLatestTaskStatus())) {
            logger.info("Waiting for status for " + name);
            synchronized (this) {
                this.wait();
            }
            logger.info("Got a new status: " + getLatestTaskStatus());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Task task = (Task) o;

        if (!taskInfo.equals(task.taskInfo)) return false;
        if (!taskStatuses.equals(task.taskStatuses)) return false;
        if (!requirement.equals(task.requirement)) return false;
        return name.equals(task.name);

    }

    @Override
    public int hashCode() {
        int result = taskInfo.hashCode();
        result = 31 * result + taskStatuses.hashCode();
        result = 31 * result + requirement.hashCode();
        result = 31 * result + name.hashCode();
        return result;
    }
}
