package org.apache.mesos.specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gabriel on 8/25/16.
 */
public class DefaultServiceSpecification implements ServiceSpecification {
    private final List<TaskTypeSpecification> taskTypeSpecifications;

    public DefaultServiceSpecification(List<TaskTypeSpecificationFactory> taskTypeSpecificationFactories) {
        this.taskTypeSpecifications = new ArrayList<>();

        for (TaskTypeSpecificationFactory taskTypeSpecificationFactory : taskTypeSpecificationFactories) {
            taskTypeSpecifications.add(taskTypeSpecificationFactory.getTaskTypeSpecification());
        }
    }

    @Override
    public List<TaskTypeSpecification> getTaskSpecifications() {
        return taskTypeSpecifications;
    }
}
