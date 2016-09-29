package org.apache.mesos.specification;

import java.util.List;

/**
 * A ServiceSpecification defines the name of a Service and the types of Tasks which constitute the service.
 */
public interface ServiceSpecification {
    /**
     * Gets the name of the Service.
     * @return the name of the Service.
     */
    String getName();


    /**
     * Gets the list of {@link TaskSet}s defining the types of Tasks of which the Service is made.
     * @return
     */
    List<TaskSet> getTaskSets();
}
