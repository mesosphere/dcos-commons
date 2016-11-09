package org.apache.mesos.specification;

import org.apache.mesos.config.Configuration;

import java.util.List;

/**
 * Created by gabriel on 11/7/16.
 */
public interface ServiceSpec extends Configuration {
    String getName();
    String getRole();
    String getPrincipal();
    List<PodSpec> getPods();
}
