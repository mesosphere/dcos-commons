package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.mesosphere.sdk.offer.evaluate.ResourceCreator;
import com.mesosphere.sdk.offer.evaluate.SpecVisitor;
import com.mesosphere.sdk.offer.evaluate.SpecVisitorException;
import org.apache.mesos.Protos;

import java.util.Optional;

/**
 * A ResourceSpec encapsulates a Mesos Resource that may be used by a Task and therefore specified in a
 * TaskSpecification.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
public interface ResourceSpec extends ResourceCreator, SpecVisitee {

    @JsonProperty("value")
    Protos.Value getValue();

    @JsonProperty("name")
    String getName();

    @JsonProperty("role")
    String getRole();

    @JsonProperty("pre-reserved-role")
    String getPreReservedRole();

    @JsonProperty("principal")
    String getPrincipal();

    default Optional<String> getEnvKey() {
        return Optional.of(getName());
    }

    default void accept(SpecVisitor specVisitor) throws SpecVisitorException {
        specVisitor.visit(this);
        specVisitor.finalizeVisit(this);
    }
}
