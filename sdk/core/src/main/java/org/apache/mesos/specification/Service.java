package org.apache.mesos.specification;

/**
 * A Service, given a ServiceSpecification registers with Mesos and deploys, recovers,
 * and maintains the specified Service.
 */
public interface Service {
    void register(ServiceSpecification serviceSpecification);
}
