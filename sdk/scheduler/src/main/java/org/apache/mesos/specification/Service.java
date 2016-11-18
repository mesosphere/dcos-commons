package org.apache.mesos.specification;

import org.apache.mesos.scheduler.plan.Plan;

import java.util.Collection;

/**
 * A Service, given a ServiceSpecification registers with Mesos and deploys, recovers,
 * and maintains the specified Service.
 */
public interface Service {
    void register(ServiceSpec serviceSpecification, Collection<Plan> plans);
}
