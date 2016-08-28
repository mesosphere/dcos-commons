package org.apache.mesos.specification;

import org.apache.mesos.Protos;

import java.util.Collection;

/**
 * Created by gabriel on 8/25/16.
 */
public interface TaskSpecification {
    String getName();
    Protos.CommandInfo getCommand();
    Collection<ResourceSpecification> getResources();
}
