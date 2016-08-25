package org.apache.mesos.specification;

import org.apache.mesos.Protos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by gabriel on 8/25/16.
 */
public class DefaultPhaseSpecification implements PhaseSpecification {
    private final List<TaskSpecification> taskSpecifications;

    public DefaultPhaseSpecification(TaskTypeSpecification taskTypeSpecification) {
        this.taskSpecifications = new ArrayList<>();
        for (int i = 0; i < taskTypeSpecification.getCount(); i++) {
            taskSpecifications.add(getTaskSpecification(i, taskTypeSpecification));
        }
    }

    @Override
    public List<TaskSpecification> getTaskSpecification() {
        return taskSpecifications;
    }

    private TaskSpecification getTaskSpecification(int id, TaskTypeSpecification taskTypeSpecification) {
        return new TaskSpecification() {
            @Override
            public String getName() {
                return taskTypeSpecification.getName() + "-" + id;
            }

            @Override
            public Protos.CommandInfo getCommand() {
                return taskTypeSpecification.getCommand();
            }

            @Override
            public Collection<Protos.Resource> getResources() {
                return taskTypeSpecification.getResources();
            }
        };
    }
}
