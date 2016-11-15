package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.specification.CommandSpec;
import org.apache.mesos.specification.HealthCheckSpec;
import org.apache.mesos.specification.TaskSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilities for HealthCheck.
 */
public class HealthCheckUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckUtils.class);

    public static Protos.HealthCheck getHealthCheck(TaskSpec taskSpec) {
        if (!taskSpec.getHealthCheck().isPresent()) {
            LOGGER.info("No health checks defined for taskSpec: {}", taskSpec.getName());
            return null;
        }

        Protos.HealthCheck.Builder builder = Protos.HealthCheck.newBuilder();
        Protos.CommandInfo.Builder commandBuilder = Protos.CommandInfo.newBuilder();

        if (taskSpec.getCommand().isPresent()) {
            CommandSpec commandSpec = taskSpec.getCommand().get();
            Protos.Environment environment = TaskUtils.fromMapToEnvironment(commandSpec.getEnvironment());
            commandBuilder.setEnvironment(environment);
        }

        HealthCheckSpec healthCheckSpec = taskSpec.getHealthCheck().get();
        return builder
                .setDelaySeconds(healthCheckSpec.getDelay())
                .setIntervalSeconds(healthCheckSpec.getInterval())
                .setTimeoutSeconds(healthCheckSpec.getTimeout())
                .setConsecutiveFailures(healthCheckSpec.getMaxConsecutiveFailures())
                .setGracePeriodSeconds(healthCheckSpec.getGracePeriod())
                .setCommand(commandBuilder.setValue(healthCheckSpec.getCommand()))
                .build();
    }
}
