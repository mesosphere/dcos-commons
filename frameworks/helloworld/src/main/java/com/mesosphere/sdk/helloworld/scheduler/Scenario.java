package com.mesosphere.sdk.helloworld.scheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import com.google.common.base.Splitter;
import com.mesosphere.sdk.framework.EnvStore;
import com.mesosphere.sdk.scheduler.SchedulerBuilder;

/**
 * Handling of different customization scenarios that may be enabled by the user (or by the test).
 */
public class Scenario {

    private static final String SCENARIOS_ENV_KEY = "SCENARIOS";

    /**
     * The names of scenarios as they may be entered by the user.
     */
    public enum Type {
        YAML,
        JAVA,
        CUSTOM_PLAN,
        CUSTOM_DECOMMISSION,
        MULTI_REGION
    }

    private Scenario() {
        // do not instantiate
    }

    static Collection<Scenario.Type> getScenarios(EnvStore envStore) {
        Collection<String> rawVals = Splitter.on(',').trimResults().splitToList(
                envStore.getOptional(SCENARIOS_ENV_KEY, Scenario.Type.YAML.toString()));
        Collection<Scenario.Type> scenarios = new ArrayList<>();
        for (String rawVal : rawVals) {
            try {
                scenarios.add(Scenario.Type.valueOf(rawVal.toUpperCase()));
            } catch (Exception e) {
                throw new IllegalArgumentException(String.format(
                        "Unable to parse %s value '%s'. Expected one of: %s",
                        SCENARIOS_ENV_KEY, rawVal, Arrays.asList(Scenario.Type.values())));
            }
        }
        return scenarios;
    }

    static SchedulerBuilder customize(SchedulerBuilder builder, Collection<Type> types) {
        for (Type type : types) {
            switch (type) {
                case MULTI_REGION:
                    builder.withSingleRegionConstraint();
                    break;
                case CUSTOM_PLAN:
                    builder.setPlanCustomizer(new ReversePhasesCustomizer());
                    break;
                case CUSTOM_DECOMMISSION:
                    builder.setPlanCustomizer(new DecomissionCustomizer());
                    break;
                case JAVA:
                case YAML:
                    // no extra customizations
                    break;
                default:
                    throw new IllegalStateException(String.format("Unexpected scenario '%s'", type.name()));
            }
        }
        return builder;
    }
}
