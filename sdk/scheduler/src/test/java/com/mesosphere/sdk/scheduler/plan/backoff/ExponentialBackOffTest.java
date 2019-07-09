package com.mesosphere.sdk.scheduler.plan.backoff;

import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.Optional;

public class ExponentialBackOffTest {

  private ExponentialBackOff backOff = new ExponentialBackOff(1.15, 60, 300);

  @Test
  public void testChangingDelay() {
    String testTask = "test-0-add-delay";
    backOff.addDelay(testTask);
    Optional<Duration> firstDelay = backOff.getDelay(testTask);
    Assert.assertTrue(firstDelay.isPresent());
    backOff.addDelay(testTask);
    Optional<Duration> secondDelay = backOff.getDelay(testTask);
    Assert.assertTrue(secondDelay.isPresent());
    Assert.assertTrue(secondDelay.get().getSeconds() > firstDelay.get().getSeconds());
    for (int i = 0 ; i < 50; i++) {
      backOff.addDelay(testTask); // max out on launch delay.
    }
    firstDelay = backOff.getDelay(testTask);
    backOff.addDelay(testTask);
    try {
      Thread.sleep(100);
    } catch (InterruptedException ie) {
      ie.printStackTrace();
    }
    secondDelay = backOff.getDelay(testTask);
    Assert.assertTrue(firstDelay.isPresent());
    Assert.assertTrue(secondDelay.isPresent());
    Assert.assertTrue(secondDelay.get().toMillis() < firstDelay.get().toMillis());
  }

  @Test
  public void testClearDelay() {
    String testTask = "test-0-clear-delay";
    backOff.addDelay(testTask);
    Assert.assertTrue(backOff.getDelay(testTask).isPresent());
    Assert.assertTrue(backOff.clearDelay(testTask));
    Assert.assertFalse(backOff.getDelay(testTask).isPresent());
  }
}
