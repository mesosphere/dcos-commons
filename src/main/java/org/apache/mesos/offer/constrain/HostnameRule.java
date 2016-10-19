package org.apache.mesos.offer.constrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.mesos.Protos.Offer;

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
    public Offer filter(Offer offer) {
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

    /**
     * Ensures that the given Offer is located at the provided hostname.
     */
    public static class RequireHostnameGenerator extends PassthroughGenerator {

        public RequireHostnameGenerator(String hostname) {
            super(new HostnameRule(hostname));
        }
    }

    /**
     * Ensures that the given Offer is NOT located on the specified hostname.
     */
    public static class AvoidHostnameGenerator extends PassthroughGenerator {

        public AvoidHostnameGenerator(String hostname) {
            super(new NotRule(new HostnameRule(hostname)));
        }
    }

    /**
     * Ensures that the given Offer is located at one of the specified hostnames.
     */
    public static class RequireHostnamesGenerator extends PassthroughGenerator {

        public RequireHostnamesGenerator(Collection<String> hostnames) {
            super(new OrRule(toHostnameRules(hostnames)));
        }

        public RequireHostnamesGenerator(String... hostnames) {
            this(Arrays.asList(hostnames));
        }
    }

    /**
     * Ensures that the given Offer is NOT located on any of the specified hostnames.
     */
    public static class AvoidHostnamesGenerator extends PassthroughGenerator {

        public AvoidHostnamesGenerator(Collection<String> hostnames) {
            super(new NotRule(new OrRule(toHostnameRules(hostnames))));
        }

        public AvoidHostnamesGenerator(String... hostnames) {
            this(Arrays.asList(hostnames));
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
