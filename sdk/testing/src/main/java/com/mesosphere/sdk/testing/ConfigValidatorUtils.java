package com.mesosphere.sdk.testing;

import com.mesosphere.sdk.config.validate.ConfigValidator;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.specification.ServiceSpec;
import org.apache.mesos.SchedulerDriver;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.Collection;

/**
 * This class provides common utilities for testing zone validation with regard to placement rules.
 */
public class ConfigValidatorUtils {
    private static final String HOST_RULE = "[[\"@hostname\", \"UNIQUE\"]]";
    private static final String MAX_PER_ZONE_RULE = "[[\"@zone\", \"MAX_PER\", \"3\"]]";
    private static final String GROUP_BY_ZONE_RULE = "[[\"@zone\", \"GROUP_BY\", \"3\"]]";

    private static void validateZoneTransition(
            ConfigValidator<ServiceSpec> configValidator,
            ServiceTestRunner testRunner,
            String placementEnvKey,
            String originalPlacement,
            String newPlacement,
            boolean shouldSucceed) throws Exception {

        ServiceTestResult result = testRunner
                .setSchedulerEnv(placementEnvKey, originalPlacement)
                .run();

        Collection<SimulationTick> ticks = new ArrayList<>();
        ticks.add(Send.register());

        if (shouldSucceed) {
            ticks.add(new Expect() {
                @Override
                public void expect(ClusterState state, SchedulerDriver mockDriver) throws AssertionError {
                    Plan plan = state.getPlans().stream()
                            .filter(p -> p.getName().equals(Constants.DEPLOY_PLAN_NAME))
                            .findFirst().get();
                    Assert.assertNotEquals(Status.ERROR, plan.getStatus());
                }

                @Override
                public String getDescription() {
                    return "Deploy plan does not have ERROR as status.";
                }
            });
        } else {
            ticks.add(Expect.planStatus(Constants.DEPLOY_PLAN_NAME, Status.ERROR));
        }

        testRunner
                .setSchedulerEnv(placementEnvKey, newPlacement)
                .addCustomValidator(configValidator)
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
                true);
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
                false);
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
                false);
    }
}
