package com.mesosphere.sdk.scheduler.plan.backoff;

import org.junit.Test;

import java.time.Duration;

public class ExponentialBackOffTest {

  @Test
  public void addDelay() {
    ExponentialBackOff instance = ExponentialBackOff.getInstance();

    String taskName = "some-task-name";
    instance.addDelay(taskName);
    Duration delay1 = instance.getDelay(taskName).getCurrentDelay();
    //instance.addDelay(taskName);
    Duration delay2 = instance.getDelay(taskName).getCurrentDelay();
    System.out.println(delay1.toString());
    System.out.println(delay2.toString());
  }
}
