package org.apache.mesos.offer.constrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.offer.OfferRequirement;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This rule enforces that a task be placed on the specified hostname, or enforces that the task
 * avoid that hostname.
 */
public class HostnameRule implements PlacementRule {

    private final String hostname;

    /**
     * Requires that a task be placed on the provided hostname.
     *
     * @param hostname hostname of the mesos agent to require
     */
    public static PlacementRule require(String hostname) {
        return new HostnameRule(hostname);
    }

    /**
     * Requires that a task be placed on one of the provided hostnames.
     *
     * @param hostnames hostnames of the mesos agents to require
     */
    public static PlacementRule require(Collection<String> hostnames) {
        return new OrRule(toHostnameRules(hostnames));
    }

    /**
     * Requires that a task be placed on one of the provided hostnames.
     *
     * @param hostnames hostnames of the mesos agents to require
     */
    public static PlacementRule require(String... hostnames) {
        return require(Arrays.asList(hostnames));
    }

    /**
     * Requires that a task NOT be placed on the provided hostname.
     *
     * @param hostname hostname of the mesos agent to avoid
     */
    public static PlacementRule avoid(String hostname) {
        return new NotRule(require(hostname));
    }

    /**
     * Requires that a task NOT be placed on any of the provided hostnames.
     *
     * @param hostnames hostnames of the mesos agents to avoid
     */
    public static PlacementRule avoid(Collection<String> hostnames) {
        return new NotRule(require(hostnames));
    }

    /**
     * Requires that a task NOT be placed on any of the provided hostnames.
     *
     * @param hostnames hostnames of the mesos agents to avoid
     */
    public static PlacementRule avoid(String... hostnames) {
        return avoid(Arrays.asList(hostnames));
    }

    @JsonCreator
    private HostnameRule(@JsonProperty("hostname") String hostname) {
        this.hostname = hostname;
    }

    @Override
    public Offer filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks) {
        if (hostname.equals(offer.getHostname())) {
            return offer;
        } else {
            // hostname mismatch: return empty offer
            return offer.toBuilder().clearResources().build();
        }
    }

    @JsonProperty("hostname")
    private String getHostname() {
        return hostname;
    }

    @Override
    public String toString() {
        return String.format("HostnameRule{hostname=%s}", hostname);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    /**
     * Converts the provided agent ids into {@link HostnameRule}s.
     */
    private static Collection<PlacementRule> toHostnameRules(Collection<String> hostnames) {
        List<PlacementRule> rules = new ArrayList<>();
        for (String hostname : hostnames) {
            rules.add(new HostnameRule(hostname));
        }
        return rules;
    }
}
