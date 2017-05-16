package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;
import com.mesosphere.sdk.offer.evaluate.ResourceEvaluationStage;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Value;

import java.util.Optional;

/**
 * A {@link ResourceRequirement} encapsulates a needed Mesos Resource.
 *
 * A {@link MesosResource} object indicates whether it is expected to exist already or
 * to be created by the presence or absence of IDs respectively.  A {@link MesosResource}
 * indicates that it is expected to exist by having a Label with the key
 * {@code resource_id} attached to it.  A Volume simliarly indicates the same need
 * for creation or expected existence by having a persistence ID of an empty string
 * or an already determined value.
 */
public class ResourceRequirement {
    private final String role;
    private final String name;
    private final Value value;
    private final String resourceId;
    private final boolean reservesResource;

    public ResourceRequirement(Builder builder) {
        this.role = builder.role;
        this.name = builder.name;
        this.value = builder.value;
        this.resourceId = builder.resourceId;
        this.reservesResource = builder.reservesResource;
    }

    public String getRole() {
        return role;
    }

    public String getName() {
        return name;
    }

    public Value getValue() {
        return value;
    }

    public Optional<String> getResourceId() {
        return Optional.ofNullable(resourceId);
    }

    public boolean reservesResource() {
        return reservesResource;
    }

    public boolean expectsResource() {
        return !reservesResource();
    }

    public OfferEvaluationStage getEvaluationStage(String taskName) {
        return new ResourceEvaluationStage(this, taskName);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    public static class Builder {
        private String role;
        private String name;
        private Value value;
        private String resourceId;
        private boolean reservesResource;

        public Builder(String role, String name, Value value) {
            this.role = role;
            this.name = name;
            this.value = value;
            this.reservesResource = true;
        }

        public Builder(Resource resource) {
            this.role = resource.getRole();
            this.name = resource.getName();
            this.value = ValueUtils.getValue(resource);
            this.resourceId = new MesosResource(resource).getResourceId();

            boolean resourceIdIsPresent = true;
            if (resourceId.isEmpty()) {
                resourceId = null;
                resourceIdIsPresent = false;
            }

            reservesResource = resourceIdIsPresent ? false : true;
        }

        public Builder resourceId(String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public Builder reservesResource(boolean reservesResource) {
            this.reservesResource = reservesResource;
            return this;
        }

        public ResourceRequirement build() {
            return new ResourceRequirement(this);
        }
    }
}
