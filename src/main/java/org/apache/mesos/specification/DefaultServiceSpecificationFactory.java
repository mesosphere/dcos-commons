package org.apache.mesos.specification;

import java.util.List;

/**
 * Created by gabriel on 8/25/16.
 */
public class DefaultServiceSpecificationFactory implements ServiceSpecificationFactory {
    private final String name;
    private final List<TaskTypeSpecificationFactory> taskTypeSpecificationFactories;

    public DefaultServiceSpecificationFactory(String name, List<TaskTypeSpecificationFactory> taskTypeSpecificationFactories) {
        this.name = name;
        this.taskTypeSpecificationFactories = taskTypeSpecificationFactories;
    }

    @Override
    public ServiceSpecification getServiceSpecification() {
        return new DefaultServiceSpecification(name, taskTypeSpecificationFactories);
    }
}
