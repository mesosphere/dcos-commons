package org.apache.mesos.specification;

/**
 * A Service is a service which runs on DC/OS and is defined by a ServiceSpecification.  The register method registers
 * the service with the Mesos master and begins operation.
 */
public interface Service {
    void register(ServiceSpecification serviceSpecification);
}
