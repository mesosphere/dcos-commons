package org.apache.mesos.specification;

/**
 * Created by gabriel on 11/9/16.
 */
public interface PodInstance {
    PodSpec getPod();
    Integer getIndex();

    default String getName() {
        return getName(getPod(), getIndex());
    }

    static String getName(PodSpec podSpec, int index) {
        return podSpec.getType() + "-" + index;
    }
}
