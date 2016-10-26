package org.apache.mesos.specification;

import java.util.List;

/**
 * This class is a default implementation of the ServiceSpecification interface.
 */
public class DefaultServiceSpecification implements ServiceSpecification {
    private final String name;
    private final List<TaskSet> taskSets;

    public DefaultServiceSpecification(String name, List<TaskSet> taskSets) {
        this.name = name;
        this.taskSets = taskSets;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<TaskSet> getTaskSets() {
        return taskSets;
    }

    @Override
    @SuppressWarnings("PMD.IfStmtsMustUseBraces")
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultServiceSpecification that = (DefaultServiceSpecification) o;

        if (!name.equals(that.name)) return false;
        return taskSets.equals(that.taskSets);

    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + taskSets.hashCode();
        return result;
    }
}
