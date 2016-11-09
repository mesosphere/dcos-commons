package org.apache.mesos.specification;

import org.apache.mesos.config.Configuration;

import java.util.List;

/**
 * Defines a {@link Service}.
 */
public interface ServiceSpec extends Configuration {
    String getName();
    String getRole();
    String getPrincipal();
    List<PodSpec> getPods();
    int getApiPort();
    String getZookeeperConnection();
}
