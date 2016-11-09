package org.apache.mesos.specification;

import java.util.Collection;

/**
 * Defines a {@link Service}.
 */
public interface ServiceSpec {
    String getName();
    String getRole();
    String getPrincipal();
    int getApiPort();
    String getZookeeperConnection();
    Collection<PodSpec> getPods();
}
