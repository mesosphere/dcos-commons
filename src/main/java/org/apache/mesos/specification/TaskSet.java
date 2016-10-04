package org.apache.mesos.specification;

import java.util.List;

/**
 * A TaskSet groups a set of Tasks that should be deployed using the same deployment strategy.
 * In particular a TaskSet is deployed as part of a Phase.
 */
public interface TaskSet {
    /**
     * Returns the name of this task type. Eg "index" for an index node type or "data" for a data
     * node type.
     */
    String getName();

    /**
     * Returns the Tasks included in this grouping.
     */
    List<TaskSpecification> getTaskSpecifications();
}
