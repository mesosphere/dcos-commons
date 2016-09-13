package org.apache.mesos.specification;

import java.util.ArrayList;
import java.util.List;

/**
 * This class provides a default implementation of the PhaseSpecification interface.
 */
public class DefaultPhaseSpecification implements PhaseSpecification {
    private final String name;
    private final List<TaskSpecification> taskSpecifications;

    public DefaultPhaseSpecification(TaskTypeSpecification taskTypeSpecification) {
        this.name = taskTypeSpecification.getTypeName();
        this.taskSpecifications = new ArrayList<>();
        for (int i = 0; i < taskTypeSpecification.getCount(); i++) {
            taskSpecifications.add(getTaskSpecification(i, taskTypeSpecification));
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<TaskSpecification> getTaskSpecifications() {
        return taskSpecifications;
    }

    private static TaskSpecification getTaskSpecification(int id, TaskTypeSpecification taskTypeSpecification) {
        return DefaultTaskSpecification.create(
                taskTypeSpecification.getTaskName(id),
                taskTypeSpecification.getCommand(id),
                taskTypeSpecification.getResources(),
                taskTypeSpecification.getVolumes());
    }
}
