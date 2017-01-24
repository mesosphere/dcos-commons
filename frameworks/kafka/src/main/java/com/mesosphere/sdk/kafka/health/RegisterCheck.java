package com.mesosphere.sdk.kafka.health;

import com.codahale.metrics.health.HealthCheck;
import com.mesosphere.dcos.kafka.scheduler.KafkaScheduler;

/**
 * This health-check fails when the Scheduler fails to register with Mesos, succeeds otherwise.
 */
public class RegisterCheck extends HealthCheck {
    public static final String NAME = "registered";
    private final KafkaScheduler kafkaScheduler;

    public RegisterCheck(KafkaScheduler kafkaScheduler) {
        this.kafkaScheduler = kafkaScheduler;
    }

    @Override
    protected Result check() throws Exception {
        if (kafkaScheduler.isRegistered()) {
            return Result.healthy("Scheduler has registered.");
        } else {
            return Result.unhealthy("Scheduler has failed to register.");
        }
    }
}
