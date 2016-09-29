package org.apache.mesos.specification;

import java.util.List;

/**
 * A TaskSet groups a set of Tasks that should be deployed using the same deployment strategy.
 */
public interface TaskSet {
    String getName();
    List<TaskSpecification> getTaskSpecifications();
}
