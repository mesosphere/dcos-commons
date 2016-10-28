package org.apache.mesos.offer.constrain;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;

/**
 * A {@link PlacementRule} which performs no filtering. Just returns the offers it's given as-is.
 */
public class PassthroughRule implements PlacementRule {

    private final String label;

    /**
     * Creates a new PassthroughRule, which accepts everything it's provided.
     *
     * @param label a label describing the source of this passthrough to include in toString()
     */
    public PassthroughRule(String label) {
        this.label = label;
    }

    @Override
    public Offer filter(Offer offer) {
        return offer;
    }

    @Override
    public String toString() {
        return String.format("PassthroughRule{label=%s}", label);
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
