package org.apache.mesos.specification;

import java.util.List;

import org.apache.mesos.config.Configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A {@code ServiceSpecification} defines the name of a Service and the types of Tasks in Pods which
 * constitute the service.
 *
 * ServiceSpecifications are implementations of the {@link Configuration} interface. This allows
 * specifications to be stored to persistent storage, so that they can be modified and reconfigured
 * throughout the lifetime of the service.
 */
public interface ServiceSpecification extends Configuration {
    /**
     * Returns the name of the Service.
     */
    @JsonProperty("name")
    String getName();

    /**
     * Returns the list of {@link PodSet}s defining the types of Tasks in Pods which compose the underlying
     * service.
     */
    @JsonProperty("task_sets")
    List<PodSet> getPodSets();
}
