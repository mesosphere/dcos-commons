package org.apache.mesos.offer.constrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.offer.OfferRequirement;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This rule enforces that a task be placed on the specified hostname, or enforces that the task
 * avoid that hostname.
 */
public class HostnameRule implements PlacementRule {

    private final String hostname;

    public HostnameRule(String hostname) {
        this.hostname = hostname;
    }

    @Override
    public Offer filter(Offer offer, OfferRequirement offerRequirement) {
        if (hostname.equals(offer.getHostname())) {
            return offer;
        } else {
            // hostname mismatch: return empty offer
            return offer.toBuilder().clearResources().build();
        }
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
     * Ensures that the given Offer is located at the provided hostname.
     */
    @JsonIgnoreProperties(PassthroughGenerator.RULE_NAME) // don't include in serialization
    public static class RequireHostnameGenerator extends PassthroughGenerator {

        private final String hostname;

        @JsonCreator
        public RequireHostnameGenerator(@JsonProperty("hostname") String hostname) {
            super(new HostnameRule(hostname));
            this.hostname = hostname;
        }

        @JsonProperty("hostname")
        private String getHostname() {
            return hostname;
        }

        @Override
        public String toString() {
            return String.format("RequireHostnameGenerator{hostname=%s}", hostname);
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

    /**
     * Ensures that the given Offer is NOT located on the specified hostname.
     */
    @JsonIgnoreProperties(PassthroughGenerator.RULE_NAME) // don't include in serialization
    public static class AvoidHostnameGenerator extends PassthroughGenerator {

        private final String hostname;

        @JsonCreator
        public AvoidHostnameGenerator(@JsonProperty("hostname") String hostname) {
            super(new NotRule(new HostnameRule(hostname)));
            this.hostname = hostname;
        }

        @JsonProperty("hostname")
        private String getHostname() {
            return hostname;
        }

        @Override
        public String toString() {
            return String.format("AvoidHostnameGenerator{hostname=%s}", hostname);
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

    /**
     * Ensures that the given Offer is located at one of the specified hostnames.
     */
    @JsonIgnoreProperties(PassthroughGenerator.RULE_NAME) // don't include in serialization
    public static class RequireHostnamesGenerator extends PassthroughGenerator {

        private final Collection<String> hostnames;

        @JsonCreator
        public RequireHostnamesGenerator(@JsonProperty("hostnames") Collection<String> hostnames) {
            super(new OrRule(toHostnameRules(hostnames)));
            this.hostnames = hostnames;
        }

        public RequireHostnamesGenerator(String... hostnames) {
            this(Arrays.asList(hostnames));
        }

        @JsonProperty("hostnames")
        private Collection<String> getHostnames() {
            return hostnames;
        }

        @Override
        public String toString() {
            return String.format("RequireHostnamesGenerator{hostname=%s}", hostnames);
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

    /**
     * Ensures that the given Offer is NOT located on any of the specified hostnames.
     */
    @JsonIgnoreProperties(PassthroughGenerator.RULE_NAME) // don't include in serialization
    public static class AvoidHostnamesGenerator extends PassthroughGenerator {

        private final Collection<String> hostnames;

        @JsonCreator
        public AvoidHostnamesGenerator(@JsonProperty("hostnames") Collection<String> hostnames) {
            super(new NotRule(new OrRule(toHostnameRules(hostnames))));
            this.hostnames = hostnames;
        }

        public AvoidHostnamesGenerator(String... hostnames) {
            this(Arrays.asList(hostnames));
        }

        @JsonProperty("hostnames")
        private Collection<String> getHostnames() {
            return hostnames;
        }

        @Override
        public String toString() {
            return String.format("AvoidHostnamesGenerator{hostname=%s}", hostnames);
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
