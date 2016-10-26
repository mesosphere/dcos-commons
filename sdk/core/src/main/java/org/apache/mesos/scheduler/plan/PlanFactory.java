package org.apache.mesos.scheduler.plan;

import org.apache.mesos.specification.ServiceSpecification;

/**
 * Created by gabriel on 10/15/16.
 */
public interface PlanFactory {
    Plan getPlan(ServiceSpecification serviceSpecification);
}
