package com.mesosphere.sdk.specification;

/**
 * A Service, given a ServiceSpecification registers with Mesos and deploys, recovers,
 * and maintains the specified Service.
 */
public interface Service extends Runnable {
    /**
     * Runs the service. Exits only when the service should exit.
     */
    void register() throws Exception;
}
