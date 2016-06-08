package org.apache.mesos.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.mesos.Protos.Credential;
import org.apache.mesos.Protos.ExecutorID;
import org.apache.mesos.Protos.FrameworkID;
import org.apache.mesos.Protos.FrameworkInfo;
import org.apache.mesos.Protos.MasterInfo;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.MesosSchedulerDriver;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;

import org.junit.Test;

public class SchedulerDriverFactoryTest {

  private static final String MASTER_URL = "fake-master-url";
  private static final byte[] SECRET = new byte[]{'s','e','k','r','i','t'};
  private static final FrameworkInfo FRAMEWORK_WITHOUT_PRINCIPAL = FrameworkInfo
      .newBuilder()
      .setUser("Foo")
      .setName("Bar")
      .build();
  private static final FrameworkInfo FRAMEWORK_EMPTY_PRINCIPAL = FrameworkInfo
      .newBuilder(FRAMEWORK_WITHOUT_PRINCIPAL)
      .setPrincipal("")
      .build();
  private static final FrameworkInfo FRAMEWORK_WITH_PRINCIPAL = FrameworkInfo
      .newBuilder(FRAMEWORK_WITHOUT_PRINCIPAL)
      .setPrincipal("fake-principal")
      .build();

  @Test
  public void testSuccess_NoSecret() throws Exception {
    CustomSchedulerDriverFactory factory = new CustomSchedulerDriverFactory();
    assertNull(factory.create(
        new NoOpScheduler(), FRAMEWORK_WITH_PRINCIPAL, MASTER_URL));
    assertEquals(1, factory.createCalls);
    assertFalse(factory.lastCallHadSecret);
  }

  @Test
  public void testSuccess_WithSecret() throws Exception {
    CustomSchedulerDriverFactory factory = new CustomSchedulerDriverFactory();
    assertNull(factory.create(
        new NoOpScheduler(), FRAMEWORK_WITH_PRINCIPAL, MASTER_URL, SECRET));
    assertEquals(1, factory.createCalls);
    assertTrue(factory.lastCallHadSecret);
  }

  @Test(expected=IllegalArgumentException.class)
  public void testEmptyPrincipal_NoSecret() throws Exception {
    new SchedulerDriverFactory().create(
        new NoOpScheduler(), FRAMEWORK_EMPTY_PRINCIPAL, MASTER_URL);
  }

  @Test(expected=IllegalArgumentException.class)
  public void testEmptyPrincipal_WithSecret() throws Exception {
    new SchedulerDriverFactory().create(
        new NoOpScheduler(), FRAMEWORK_EMPTY_PRINCIPAL, MASTER_URL, SECRET);
  }

  @Test(expected=IllegalArgumentException.class)
  public void testMissingPrincipal_NoSecret() throws Exception {
    new SchedulerDriverFactory().create(
        new NoOpScheduler(), FRAMEWORK_WITHOUT_PRINCIPAL, MASTER_URL);
  }

  @Test(expected=IllegalArgumentException.class)
  public void testMissingPrincipal_WithSecret() throws Exception {
    new SchedulerDriverFactory().create(
        new NoOpScheduler(), FRAMEWORK_WITHOUT_PRINCIPAL, MASTER_URL, SECRET);
  }

  private static class CustomSchedulerDriverFactory extends SchedulerDriverFactory {

    public int createCalls = 0;
    public boolean lastCallHadSecret = false;

    /**
     * Avoid calls to the MesosSchedulerDriver constructor, which triggers errors about libmesos not
     * being present.
     */
    @Override
    protected MesosSchedulerDriver createInternal(
        final Scheduler scheduler,
        final FrameworkInfo frameworkInfo,
        final String masterZkUrl,
        final Credential credential) {
      createCalls++;
      lastCallHadSecret = credential.hasSecret();
      return null; // avoid requiring a NoOpSchedulerDriver
    }
  }

  private static class NoOpScheduler implements Scheduler {

    @Override
    public void registered(SchedulerDriver driver, FrameworkID frameworkId,
        MasterInfo masterInfo) { }

    @Override
    public void reregistered(SchedulerDriver driver, MasterInfo masterInfo) { }

    @Override
    public void resourceOffers(SchedulerDriver driver, List<Offer> offers) { }

    @Override
    public void offerRescinded(SchedulerDriver driver, OfferID offerId) { }

    @Override
    public void statusUpdate(SchedulerDriver driver, TaskStatus status) { }

    @Override
    public void frameworkMessage(SchedulerDriver driver, ExecutorID executorId,
        SlaveID slaveId, byte[] data) { }

    @Override
    public void disconnected(SchedulerDriver driver) { }

    @Override
    public void slaveLost(SchedulerDriver driver, SlaveID slaveId) { }

    @Override
    public void executorLost(SchedulerDriver driver, ExecutorID executorId,
        SlaveID slaveId, int status) { }

    @Override
    public void error(SchedulerDriver driver, String message) { }
  }
}
