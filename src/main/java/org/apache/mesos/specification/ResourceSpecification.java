package org.apache.mesos.specification;

import org.apache.mesos.Protos;

/**
 * Created by gabriel on 8/27/16.
 */
public interface ResourceSpecification extends Named {
    Protos.Value getValue();
    String getRole();
    String getPrincipal();
}
