package org.apache.mesos.specification;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * A {@code PodSetSpecification} groups a set of {@link PodSpecification}s that should be deployed using the same
 * deployment strategy. In particular a PodSetSpecification is deployed as part of a
 * {@link org.apache.mesos.scheduler.plan.Phase}, with each Task in the {@link PodSpecification} represented as a
 * {@link org.apache.mesos.scheduler.plan.Block} within that Phase.
 */
@JsonDeserialize(as = DefaultPodSetSpecification.class)
public interface PodSetSpecification {
    /**
     * Returns the name of this pod type. Eg "index" for an index pod type or "data" for a data pod type.
     */
    @JsonProperty("name")
    String getName();

    /**
     * Returns the Pods included in this grouping.
     */
    @JsonProperty("pods")
    List<PodSpecification> getPodSpecifications();
}
