package org.apache.mesos.specification;

import org.apache.mesos.Protos;
import org.apache.mesos.util.ValidationUtils;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Default implementation of {@link ContainerSpec}.
 */
public class DefaultContainerSpec implements ContainerSpec {
    @NotNull
    @Size(min = 1)
    private String imageName;

    public DefaultContainerSpec(String imageName) {
        this.imageName = imageName;
    }

    @Override
    public String getImageName() {
        return imageName;
    }
}
