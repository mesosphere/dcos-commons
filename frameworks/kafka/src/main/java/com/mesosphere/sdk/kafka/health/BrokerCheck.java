package com.mesosphere.sdk.kafka.health;

import com.codahale.metrics.health.HealthCheck;
import com.mesosphere.dcos.kafka.plan.KafkaUpdatePhase;
import com.mesosphere.dcos.kafka.scheduler.KafkaScheduler;
import com.mesosphere.sdk.kafka.api.InterruptProceed;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.scheduler.plan.Phase;
import org.apache.mesos.scheduler.plan.Plan;
import org.apache.mesos.scheduler.plan.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrokerCheck extends HealthCheck {
  public static final String NAME = "broker_count";
  protected static final Logger LOGGER = LoggerFactory.getLogger(InterruptProceed.class);

  private final KafkaScheduler kafkaScheduler;

  public BrokerCheck(KafkaScheduler kafkaScheduler) {
    this.kafkaScheduler = kafkaScheduler;
  }

  @Override
  protected Result check() throws Exception {
    String errMsg = "";

    try {
      Phase updatePhase = getUpdatePhase();

      if (updatePhase == null) {
        errMsg = "Health check failed because of failure to find an update phase.";
        log.error(errMsg);
        return Result.unhealthy(errMsg);
      }

      int runningBrokerCount = kafkaScheduler.getFrameworkState().getRunningBrokersCount();
      int completedBrokerStepCount = getCompleteBrokerStepCount(updatePhase);

      if (runningBrokerCount < completedBrokerStepCount) {
        errMsg = "Health check failed because running Broker count is less than completed Broker Steps: running = " + runningBrokerCount + " completed blocks = " + completedBrokerStepCount;
        log.warn(errMsg);
        return Result.unhealthy(errMsg);
      }

      return Result.healthy("All expected Brokers running");
    } catch (Exception ex) {
      errMsg = "Failed to determine Broker counts with exception: " + ex;
      log.error(errMsg);
      return Result.unhealthy(errMsg);
    }
  }

  private Phase getUpdatePhase() {
    Plan plan = kafkaScheduler.getPlanManager().getPlan();

    for (Phase phase : plan.getChildren()) {
      if (phase instanceof KafkaUpdatePhase) {
        return phase;
      }
    }

    return null;
  }

  private int getCompleteBrokerStepCount(Phase phase) {
    int completeCount = 0;

    for (Step step : phase.getChildren()) {
      if (step.isComplete()) {
        completeCount++;
      }
    }

    return completeCount;
  }
}
