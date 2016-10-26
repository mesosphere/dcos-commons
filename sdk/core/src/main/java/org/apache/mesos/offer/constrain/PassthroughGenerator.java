package org.apache.mesos.offer.constrain;

import java.util.Collection;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.TaskInfo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Directly returns the provided rule when invoked.
 */
public class PassthroughGenerator implements PlacementRuleGenerator {

    protected static final String RULE_NAME = "rule";

    private final PlacementRule ruleToReturn;

    @JsonCreator
    public PassthroughGenerator(@JsonProperty(RULE_NAME) PlacementRule ruleToReturn) {
        this.ruleToReturn = ruleToReturn;
    }

    @Override
    public PlacementRule generate(Collection<TaskInfo> tasks) {
        return ruleToReturn;
    }

    @Override
    public String toString() {
        return String.format("PassthroughGenerator{%s=%s}", RULE_NAME, ruleToReturn);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @JsonProperty(RULE_NAME)
    private PlacementRule getPlacementRule() {
        return ruleToReturn;
    }
}
