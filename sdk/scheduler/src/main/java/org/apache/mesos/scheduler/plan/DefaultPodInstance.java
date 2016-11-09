package org.apache.mesos.scheduler.plan;

import org.apache.mesos.specification.PodInstance;
import org.apache.mesos.specification.PodSpec;

/**
 * Created by gabriel on 11/9/16.
 */
public class DefaultPodInstance implements PodInstance {
    private final PodSpec podSpec;
    private final Integer index;

    public DefaultPodInstance(PodSpec podSpec, Integer index) {
        this.podSpec = podSpec;
        this.index = index;
    }

    @Override
    public PodSpec getPod() {
        return podSpec;
    }

    @Override
    public Integer getIndex() {
        return index;
    }
}
