package com.mesosphere.sdk.offer.evaluate.placement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This rule enforces that a task be placed on the specified hostname, or enforces that the task
 * avoid that hostname.
 */
public class HostnameRule implements PlacementRule {

    /**
     * Requires that a task be placed on the provided hostname.
     *
     * @param hostname hostname of the mesos agent to require
     */
    public static PlacementRule requireExact(String hostname) {
        return require(toExactMatchers(hostname));
    }

    /**
     * Requires that a task be placed on one of the provided hostnames.
     *
     * @param hostnames hostnames of the mesos agents to require
     */
    public static PlacementRule requireExact(Collection<String> hostnames) {
        return require(toExactMatchers(hostnames));
    }

    /**
     * Requires that a task be placed on one of the provided hostnames.
     *
     * @param hostnames for hostnames of the mesos agents to require
     */
    public static PlacementRule requireExact(String... hostnames) {
        return require(toExactMatchers(hostnames));
    }

    /**
     * Requires that a task be placed on the provided hostname.
     *
     * @param matcher matcher for hostname of the mesos agent to require
     */
    public static PlacementRule require(StringMatcher matcher) {
        return new HostnameRule(matcher);
    }

    /**
     * Requires that a task be placed on one of the provided hostnames.
     *
     * @param matchers matchers for hostnames of the mesos agents to require
     */
    public static PlacementRule require(Collection<StringMatcher> matchers) {
        if (matchers.size() == 1) {
            return require(matchers.iterator().next());
        }
        return new OrRule(toHostnameRules(matchers));
    }

    /**
     * Requires that a task be placed on one of the provided hostnames.
     *
     * @param matchers matchers for hostnames of the mesos agents to require
     */
    public static PlacementRule require(StringMatcher... matchers) {
        return require(Arrays.asList(matchers));
    }

    /**
     * Requires that a task NOT be placed on the provided hostname.
     *
     * @param hostname hostname of the mesos agent to avoid
     */
    public static PlacementRule avoidExact(String hostname) {
        return avoid(toExactMatchers(hostname));
    }

    /**
     * Requires that a task NOT be placed on any of the provided hostnames.
     *
     * @param hostnames hostnames of the mesos agents to avoid
     */
    public static PlacementRule avoidExact(Collection<String> hostnames) {
        return avoid(toExactMatchers(hostnames));
    }

    /**
     * Requires that a task NOT be placed on any of the provided hostnames.
     *
     * @param hostnames hostnames of the mesos agents to avoid
     */
    public static PlacementRule avoidExact(String... hostnames) {
        return avoid(toExactMatchers(hostnames));
    }

    /**
     * Requires that a task NOT be placed on the provided hostname.
     *
     * @param matcher matcher for hostname of the mesos agent to avoid
     */
    public static PlacementRule avoid(StringMatcher matcher) {
        return new NotRule(require(matcher));
    }

    /**
     * Requires that a task NOT be placed on any of the provided hostnames.
     *
     * @param matchers matchers for hostnames of the mesos agents to avoid
     */
    public static PlacementRule avoid(Collection<StringMatcher> matchers) {
        if (matchers.size() == 1) {
            return avoid(matchers.iterator().next());
        }
        return new NotRule(require(matchers));
    }

    /**
     * Requires that a task NOT be placed on any of the provided hostnames.
     *
     * @param matchers matchers for hostnames of the mesos agents to avoid
     */
    public static PlacementRule avoid(StringMatcher... matchers) {
        return avoid(Arrays.asList(matchers));
    }

    private final StringMatcher matcher;

    @JsonCreator
    private HostnameRule(@JsonProperty("matcher") StringMatcher matcher) {
        this.matcher = matcher;
    }

    @Override
    public EvaluationOutcome filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks) {
        if (matcher.matches(offer.getHostname())) {
            return EvaluationOutcome.pass(this, "Offer hostname matches pattern: '%s'", matcher.toString());
        } else {
            return EvaluationOutcome.fail(this, "Offer hostname didn't match pattern: '%s'", matcher.toString());
        }
    }

    @JsonProperty("matcher")
    private StringMatcher getMatcher() {
        return matcher;
    }

    @Override
    public String toString() {
        return String.format("HostnameRule{matcher=%s}", matcher);
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
     * Converts the provided hostnames into {@link ExactMatcher}s.
     */
    private static Collection<StringMatcher> toExactMatchers(Collection<String> hostnames) {
        List<StringMatcher> matchers = new ArrayList<>();
        for (String hostname : hostnames) {
            matchers.add(ExactMatcher.create(hostname));
        }
        return matchers;
    }

    /**
     * Converts the provided hostnames into {@link ExactMatcher}s.
     */
    private static Collection<StringMatcher> toExactMatchers(String... hostnames) {
        return toExactMatchers(Arrays.asList(hostnames));
    }

    /**
     * Converts the provided matchers into {@link HostnameRule}s.
     */
    private static Collection<PlacementRule> toHostnameRules(Collection<StringMatcher> matchers) {
        List<PlacementRule> rules = new ArrayList<>();
        for (StringMatcher matcher : matchers) {
            rules.add(require(matcher));
        }
        return rules;
    }
}
