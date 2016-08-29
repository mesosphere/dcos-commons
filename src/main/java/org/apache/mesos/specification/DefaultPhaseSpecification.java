package org.apache.mesos.specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gabriel on 8/25/16.
 */
public class DefaultPhaseSpecification implements PhaseSpecification {
    private final String name;
    private final List<TaskSpecification> taskSpecifications;

    public DefaultPhaseSpecification(String name, TaskTypeSpecification taskTypeSpecification) {
        this.name = name;
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

    private TaskSpecification getTaskSpecification(int id, TaskTypeSpecification taskTypeSpecification) {
        return DefaultTaskSpecification.create(
                taskTypeSpecification.getName() + "-" + id,
                taskTypeSpecification.getCommand(),
                taskTypeSpecification.getResources());
    }
}
