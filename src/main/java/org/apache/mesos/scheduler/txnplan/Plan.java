package org.apache.mesos.scheduler.txnplan;

import java.util.*;

/**
 * This should be serializable...
 * Created by dgrnbrg on 6/20/16.
 */
public class Plan {
    private boolean hasBeenSubmitted = false;

    // For each UUID, which UUIDs does it depend on
    private Map<UUID, List<UUID>> prereqsByStep;
    // Step data
    private Map<UUID, Step> steps;
    // This is a safety check--it doesn't matter once this has been serialized, since that occurs after submission
    private transient Thread startThread;
    // For persistent identity
    private UUID uuid;

    public Plan() {
        this.steps = new HashMap<>();
        this.prereqsByStep = new HashMap<>();
        this.startThread = Thread.currentThread();
        this.uuid = UUID.randomUUID();
    }

    public Step step(Operation operation) {
        ensureMutationSafe();
        Step step = new Step(operation, this);
        steps.put(step.getUuid(), step);
        return step;
    }

    public void freeze() {
        ensureMutationSafe();
        hasBeenSubmitted = true;
        steps = Collections.unmodifiableMap(steps);
        prereqsByStep = Collections.unmodifiableMap(prereqsByStep);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Plan plan = (Plan) o;

        if (hasBeenSubmitted != plan.hasBeenSubmitted) return false;
        if (!prereqsByStep.equals(plan.prereqsByStep)) return false;
        if (!steps.equals(plan.steps)) return false;
        return uuid.equals(plan.uuid);

    }

    @Override
    public int hashCode() {
        int result = (hasBeenSubmitted ? 1 : 0);
        result = 31 * result + prereqsByStep.hashCode();
        result = 31 * result + steps.hashCode();
        result = 31 * result + uuid.hashCode();
        return result;
    }

    private void ensureMutationSafe() {
        if (hasBeenSubmitted) {
            throw new RuntimeException("Plan has already been submitted!");
        }
        if (startThread != Thread.currentThread()) {
            throw new RuntimeException("Detected use of Plan on multiple threads. Plan is not threadsafe.");
        }
    }

    private void ensureFrozen() {
        if (!hasBeenSubmitted) {
            throw new RuntimeException("Plan hasn't been submitted yet--internals aren't safe");
        }
    }

    public void addDependency(UUID before, UUID after) {
        ensureMutationSafe();
        List<UUID> prereqs = prereqsByStep.get(after);
        if (prereqs == null) {
            prereqs = new ArrayList<>();
            prereqsByStep.put(after, prereqs);
        }
        prereqs.add(before);
    }

    public Set<String> getAffectedTaskNames() {
        Set<String> names = new HashSet<String>();
        for (Step step : steps.values()) {
            names.addAll(step.getOperation().lockedTasks());
        }
        return names;
        //TODO another direction would be to build the actual thread-based task scheduler w/ mutex functionality
        //the scheduler needs a concurrency overload detector, built-in logging functionality,
        //thread-safety checks on the OperationDriver, run each op on a cachedexecutorservice
    }

    public Map<UUID, List<UUID>> getPrereqsByStep() {
        ensureFrozen();
        return prereqsByStep;
    }

    public Map<UUID, Step> getSteps() {
        ensureFrozen();
        return steps;
    }

    public UUID getUuid() {
        return uuid;
    }
}
