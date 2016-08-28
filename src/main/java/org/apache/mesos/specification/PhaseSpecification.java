package org.apache.mesos.specification;

import java.util.List;

/**
 * Created by gabriel on 8/25/16.
 */
public interface PhaseSpecification extends Named {
    List<TaskSpecification> getTaskSpecifications();
}
