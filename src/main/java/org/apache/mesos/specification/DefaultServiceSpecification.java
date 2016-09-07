package org.apache.mesos.specification;

import java.util.List;

/**
 * This class is a default implementation of the ServiceSpecification interface.
 */
public class DefaultServiceSpecification implements ServiceSpecification {
    private final String name;
    private final List<TaskTypeSpecification> taskTypeSpecifications;

    public DefaultServiceSpecification(String name, List<TaskTypeSpecification> taskTypeSpecifications) {
        this.name = name;
        this.taskTypeSpecifications = taskTypeSpecifications;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<TaskTypeSpecification> getTaskSpecifications() {
        return taskTypeSpecifications;
    }

    @Override
    @SuppressWarnings("PMD.IfStmtsMustUseBraces")
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultServiceSpecification that = (DefaultServiceSpecification) o;

        if (!name.equals(that.name)) return false;
        return taskTypeSpecifications.equals(that.taskTypeSpecifications);

    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + taskTypeSpecifications.hashCode();
        return result;
    }
}
