package com.mesosphere.sdk.scheduler.plan.backoff;

import org.junit.Test;

public class ExponentialBackOffTest {

  @Test
  public void addDelay() {
    BackOff instance = BackOff.getInstance();

    String taskName = "some-task-name";
    instance.addDelay(taskName);
    instance.isReady(taskName);
    instance.clearDelay(taskName);
  }
}
