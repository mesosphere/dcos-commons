package com.mesosphere.sdk.offer.evaluate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mesosphere.sdk.specification.ResourceSpec;
import org.apache.mesos.Protos;

/**
 * A ResourceCreator can create a {@link org.apache.mesos.Protos.Resource.Builder} protobuf.
 */
public interface ResourceCreator {

    @JsonIgnore
    ResourceSpec getResourceSpec();

    @JsonIgnore
    default Protos.Resource.Builder getResource() {
        ResourceSpec resourceSpec = getResourceSpec();
        Protos.Resource.Builder builder = Protos.Resource.newBuilder();

        Protos.Value resourceValue = resourceSpec.getValue();
        builder.setName(resourceSpec.getName()).setType(resourceValue.getType());

        switch (resourceValue.getType()) {
            case SCALAR:
                builder.setScalar(resourceValue.getScalar());
                break;
            case RANGES:
                builder.setRanges(resourceValue.getRanges());
                break;
            case SET:
                builder.setSet(resourceValue.getSet());
                break;
            default:
                throw new IllegalArgumentException(
                        String.format("Unsupported spec value type: %s", resourceValue.getType()));
        }

        return builder;
    }
}
