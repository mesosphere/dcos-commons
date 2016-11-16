package org.apache.mesos.specification;

import org.apache.mesos.Protos;
import org.apache.mesos.util.ValidationUtils;

import javax.validation.constraints.NotNull;

/**
 * Default implementation of {@link ContainerSpec}.
 */
public class DefaultContainerSpec implements ContainerSpec {
    @NotNull
    private String imageName;

    public DefaultContainerSpec(String imageName) {
        this.imageName = imageName;
    }

    @Override
    public String getImageName() {
        return imageName;
    }
}
