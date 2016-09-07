package org.apache.mesos.specification;

/**
 * A PlanSpecificationFactory should generate a PlanSpecification when provided a ServiceSpecification.
 */
public interface PlanSpecificationFactory {
    PlanSpecification getPlanSpecification(ServiceSpecification serviceSpecification);
}
