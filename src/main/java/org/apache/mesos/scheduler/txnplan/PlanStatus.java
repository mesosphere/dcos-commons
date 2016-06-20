package org.apache.mesos.scheduler.txnplan;

import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * This is the serializable state tracking for a plan
 * TODO This should be immutable for thread safety
 * Created by dgrnbrg on 6/22/16.
 */
class PlanStatus {
    private Collection<UUID> pending;
    private Collection<UUID> running;
    private List<UUID> completed;
    private List<UUID> rolledBack;
    private UUID planUUID;
    private boolean rollingBack;
    private boolean crashed;

    public PlanStatus() {
        pending = new HashSet<>();
        running = new HashSet<>();
        completed = new ArrayList<>();
        rolledBack = new ArrayList<>();
        rollingBack = false;
        crashed = false;
    }

    public PlanStatus(UUID uuid, Collection<UUID> pending) {
        this();
        this.planUUID = uuid;
        this.pending.addAll(pending);
    }

    public Collection<UUID> getPending() {
        return Collections.unmodifiableCollection(pending);
    }

    public Collection<UUID> getRunning() {
        return Collections.unmodifiableCollection(running);
    }

    public List<UUID> getCompleted() {
        return Collections.unmodifiableList(completed);
    }

    public boolean isRollingBack() {
        return rollingBack;
    }

    public PlanStatus rollback() {
        PlanStatus status = SerializationUtil.kryos.get().copy(this);
        status.rollingBack = true;
        return status;
    }

    public boolean isCrashed() {
        return crashed;
    }

    public PlanStatus crash() {
        PlanStatus status = SerializationUtil.kryos.get().copy(this);
        status.crashed = true;
        return status;
    }

    public PlanStatus startStep(UUID uuid) {
        PlanStatus status = SerializationUtil.kryos.get().copy(this);
        if (!status.pending.contains(uuid)) {
            throw new RuntimeException("Cannot start " + uuid + "because it isn't pending");
        }
        status.pending.remove(uuid);
        status.running.add(uuid);
        return status;
    }

    public PlanStatus finishStep(UUID uuid) {
        PlanStatus status = SerializationUtil.kryos.get().copy(this);
        if (!status.running.contains(uuid)) {
            throw new RuntimeException("Cannot finish " + uuid + "because it isn't running");
        }
        status.running.remove(uuid);
        status.completed.add(uuid);
        return status;
    }

    public PlanStatus rolledBackStep(UUID uuid) {
        PlanStatus status = SerializationUtil.kryos.get().copy(this);
        if (status.completed.contains(uuid)) {
            status.completed.remove(uuid);
        } else if (status.running.contains(uuid)) {
            status.running.remove(uuid);
        } else {
            throw new RuntimeException("Cannot roll back " + uuid + "because it was never started");
        }
        status.rolledBack.add(uuid);
        return status;
    }

    public boolean isComplete() {
        return (pending.isEmpty() && running.isEmpty()) || crashed || (rollingBack && completed.isEmpty());
    }
}
