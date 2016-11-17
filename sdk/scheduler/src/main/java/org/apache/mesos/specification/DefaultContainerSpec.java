package org.apache.mesos.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Default implementation of {@link ContainerSpec}.
 */
public class DefaultContainerSpec implements ContainerSpec {
    @NotNull
    @Size(min = 1)
    private String imageName;

    @JsonCreator
    public DefaultContainerSpec(
            @JsonProperty("image-name")
            String imageName) {
        this.imageName = imageName;
    }

    @Override
    public String getImageName() {
        return imageName;
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
