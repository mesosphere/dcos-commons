package com.mesosphere.sdk.testing;

import com.mesosphere.sdk.config.validate.ConfigValidator;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.specification.ServiceSpec;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by gabriel on 1/30/18.
 */
public class ConfigValidatorUtils {
    private static final String HOST_RULE = "[[\"@hostname\", \"UNIQUE\"]]";
    private static final String MAX_PER_ZONE_RULE = "[[\"@zone\", \"MAX_PER\", \"3\"]]";
    private static final String GROUP_BY_ZONE_RULE = "[[\"@zone\", \"GROUP_BY\", \"3\"]]";

    public static void validateZoneTransition(
            ConfigValidator<ServiceSpec> configValidator,
            ServiceTestRunner testRunner,
            String placementEnvKey,
            String originalPlacement,
            String newPlacement,
            Status expectedStatus) throws Exception {

        ServiceTestResult result = testRunner
                .setSchedulerEnv(placementEnvKey, originalPlacement)
                .run();

        Collection<SimulationTick> ticks = new ArrayList<>();
        ticks.add(Send.register());
        ticks.add(Expect.planStatus(Constants.DEPLOY_PLAN_NAME, expectedStatus));

        testRunner
                .setSchedulerEnv(placementEnvKey, newPlacement)
                .setCustomValidators(configValidator)
                .setState(result)
                .run(ticks);
    }

    public static void allowRackChanges(
            ConfigValidator<ServiceSpec> configValidator,
            ServiceTestRunner testRunner,
            String placementEnvKey) throws Exception {
        validateZoneTransition(
                configValidator,
                testRunner,
                placementEnvKey,
                MAX_PER_ZONE_RULE,
                GROUP_BY_ZONE_RULE,
                Status.IN_PROGRESS);
    }

    public static void rejectRackEnablement(
            ConfigValidator<ServiceSpec> configValidator,
            ServiceTestRunner testRunner,
            String placementEnvKey) throws Exception {
        validateZoneTransition(
                configValidator,
                testRunner,
                placementEnvKey,
                HOST_RULE,
                GROUP_BY_ZONE_RULE,
                Status.ERROR);
    }

    public static void rejectRackDisablement(
            ConfigValidator<ServiceSpec> configValidator,
            ServiceTestRunner testRunner,
            String placementEnvKey) throws Exception {
        validateZoneTransition(
                configValidator,
                testRunner,
                placementEnvKey,
                GROUP_BY_ZONE_RULE,
                HOST_RULE,
                Status.ERROR);
    }
}
