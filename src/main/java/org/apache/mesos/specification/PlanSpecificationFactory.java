package org.apache.mesos.specification;

/**
 * Created by gabriel on 8/25/16.
 */
public interface PlanSpecificationFactory {
    PlanSpecification getPlanSpecification(ServiceSpecification serviceSpecification);
}
