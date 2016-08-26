package org.apache.mesos.specification;

import java.util.List;

/**
 * Created by gabriel on 8/25/16.
 */
public interface ServiceSpecification {
    String getName();
    List<TaskTypeSpecification> getTaskSpecifications();
}
