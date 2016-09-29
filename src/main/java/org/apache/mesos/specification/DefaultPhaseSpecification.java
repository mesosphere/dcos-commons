package org.apache.mesos.specification;

import java.util.List;

/**
 * This class provides a default implementation of the PhaseSpecification interface.
 */
public class DefaultPhaseSpecification implements PhaseSpecification {
    private final TaskSet taskSet;

    public DefaultPhaseSpecification(TaskSet taskSet) {
        this.taskSet = taskSet;
    }

    @Override
    public String getName() {
        return taskSet.getName();
    }

    @Override
    public List<TaskSpecification> getTaskSpecifications() {
        return taskSet.getTaskSpecifications();
    }
}
