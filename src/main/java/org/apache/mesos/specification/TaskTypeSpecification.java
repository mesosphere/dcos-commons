package org.apache.mesos.specification;

/**
 * A TaskTypeSpecification describes the count of a particular TaskSpecification which should be launched.
 */
public interface TaskTypeSpecification extends TaskSpecification {
    int getCount();
}
