package org.apache.mesos.specification;

import java.util.Collection;

/**
 * Created by gabriel on 11/7/16.
 */
public interface ServiceSpec {
    String getName();
    String getRole();
    String getPrincipal();
    Collection<PodSpec> getPods();
}
