package org.apache.mesos.specification;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * A {@code TaskSet} groups a set of Tasks that should be deployed using the same deployment
 * strategy. In particular a TaskSet is deployed as part of a
 * {@link org.apache.mesos.scheduler.plan.Phase}, with each Task represented as a
 * {@link org.apache.mesos.scheduler.plan.Block} within that Phase.
 */
@JsonDeserialize(as=DefaultTaskSet.class)
public interface TaskSet {
    /**
     * Returns the name of this task type. Eg "index" for an index node type or "data" for a data
     * node type.
     */
    @JsonProperty("name")
    String getName();

    /**
     * Returns the Tasks included in this grouping.
     */
    @JsonProperty("tasks")
    List<TaskSpecification> getTaskSpecifications();
}
