package org.apache.mesos.specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gabriel on 8/25/16.
 */
public class DefaultServiceSpecification implements ServiceSpecification {
    private final String name;
    private final List<TaskTypeSpecification> taskTypeSpecifications;

    public DefaultServiceSpecification(String name, List<TaskTypeSpecificationFactory> taskTypeSpecificationFactories) {
        this.name = name;
        this.taskTypeSpecifications = new ArrayList<>();

        for (TaskTypeSpecificationFactory taskTypeSpecificationFactory : taskTypeSpecificationFactories) {
            taskTypeSpecifications.add(taskTypeSpecificationFactory.getTaskTypeSpecification());
        }
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public List<TaskTypeSpecification> getTaskSpecifications() {
        return taskTypeSpecifications;
    }
}
