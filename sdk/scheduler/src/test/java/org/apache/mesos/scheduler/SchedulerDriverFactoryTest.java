package org.apache.mesos.scheduler;

import org.apache.mesos.MesosSchedulerDriver;
import org.apache.mesos.Protos.*;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

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
  public void testSuccess_NoAuth() throws Exception {
    CustomSchedulerDriverFactory factory = new CustomSchedulerDriverFactory(false);
    assertNull(factory.create(
        new NoOpScheduler(), FRAMEWORK_WITH_PRINCIPAL, MASTER_URL));
    assertEquals(1, factory.createCalls);
    assertFalse(factory.lastCallHadCredential);
    assertFalse(factory.lastCallHadSecret);
  }

  @Test
  public void testSuccess_Sidechannel() throws Exception {
    CustomSchedulerDriverFactory factory = new CustomSchedulerDriverFactory(true);
    assertNull(factory.create(
        new NoOpScheduler(), FRAMEWORK_WITH_PRINCIPAL, MASTER_URL));
    assertEquals(1, factory.createCalls);
    assertTrue(factory.lastCallHadCredential);
    assertFalse(factory.lastCallHadSecret);
  }

  @Test
  public void testSuccess_WithSecret() throws Exception {
    CustomSchedulerDriverFactory factory = new CustomSchedulerDriverFactory(false);
    assertNull(factory.create(
        new NoOpScheduler(), FRAMEWORK_WITH_PRINCIPAL, MASTER_URL, SECRET));
    assertEquals(1, factory.createCalls);
    assertTrue(factory.lastCallHadCredential);
    assertTrue(factory.lastCallHadSecret);
  }

  @Test
  public void testSuccess_WithSecretAndSidechannel() throws Exception {
    CustomSchedulerDriverFactory factory = new CustomSchedulerDriverFactory(true);
    assertNull(factory.create(
        new NoOpScheduler(), FRAMEWORK_WITH_PRINCIPAL, MASTER_URL, SECRET));
    assertEquals(1, factory.createCalls);
    assertTrue(factory.lastCallHadCredential);
    assertTrue(factory.lastCallHadSecret); // secret takes priority over sidechannel
  }

  @Test
  public void testEmptyPrincipal_NoAuth() throws Exception {
    CustomSchedulerDriverFactory factory = new CustomSchedulerDriverFactory(false);
    assertNull(factory.create(
        new NoOpScheduler(), FRAMEWORK_EMPTY_PRINCIPAL, MASTER_URL));
    assertEquals(1, factory.createCalls);
    assertFalse(factory.lastCallHadCredential);
    assertFalse(factory.lastCallHadSecret);
  }

  @Test(expected=IllegalArgumentException.class)
  public void testEmptyPrincipal_Sidechannel() throws Exception {
    new CustomSchedulerDriverFactory(true).create(
        new NoOpScheduler(), FRAMEWORK_EMPTY_PRINCIPAL, MASTER_URL);
  }

  @Test(expected=IllegalArgumentException.class)
  public void testEmptyPrincipal_WithSecret() throws Exception {
    new CustomSchedulerDriverFactory(false).create(
        new NoOpScheduler(), FRAMEWORK_EMPTY_PRINCIPAL, MASTER_URL, SECRET);
  }

  @Test(expected=IllegalArgumentException.class)
  public void testEmptyPrincipal_WithSecretAndSidechannel() throws Exception {
    new CustomSchedulerDriverFactory(true).create(
        new NoOpScheduler(), FRAMEWORK_EMPTY_PRINCIPAL, MASTER_URL, SECRET);
  }

  @Test
  public void testMissingPrincipal_NoAuth() throws Exception {
    CustomSchedulerDriverFactory factory = new CustomSchedulerDriverFactory(false);
    assertNull(factory.create(
        new NoOpScheduler(), FRAMEWORK_WITHOUT_PRINCIPAL, MASTER_URL));
    assertEquals(1, factory.createCalls);
    assertFalse(factory.lastCallHadCredential);
    assertFalse(factory.lastCallHadSecret);
  }

  @Test(expected=IllegalArgumentException.class)
  public void testMissingPrincipal_Sidechannel() throws Exception {
    new CustomSchedulerDriverFactory(true).create(
        new NoOpScheduler(), FRAMEWORK_WITHOUT_PRINCIPAL, MASTER_URL);
  }

  @Test(expected=IllegalArgumentException.class)
  public void testMissingPrincipal_WithSecret() throws Exception {
    new CustomSchedulerDriverFactory(false).create(
        new NoOpScheduler(), FRAMEWORK_WITHOUT_PRINCIPAL, MASTER_URL, SECRET);
  }

  @Test(expected=IllegalArgumentException.class)
  public void testMissingPrincipal_WithSecretAndSidechannel() throws Exception {
    new CustomSchedulerDriverFactory(true).create(
        new NoOpScheduler(), FRAMEWORK_WITHOUT_PRINCIPAL, MASTER_URL, SECRET);
  }

  private static class CustomSchedulerDriverFactory extends SchedulerDriverFactory {

    public int createCalls = 0;
    public boolean lastCallHadCredential = false;
    public boolean lastCallHadSecret = false;

    private final boolean sideChannelActive;

    private CustomSchedulerDriverFactory(boolean sideChannelActive) {
      this.sideChannelActive = sideChannelActive;
    }

    /**
     * Avoid calls to the MesosSchedulerDriver constructor, which triggers errors about libmesos not
     * being present.
     */
    @Override
    protected MesosSchedulerDriver createInternal(
        final Scheduler scheduler,
        final FrameworkInfo frameworkInfo,
        final String masterUrl,
        final Credential credential) {
      createCalls++;
      if (credential != null) {
        lastCallHadCredential = true;
        lastCallHadSecret = credential.hasSecret();
      } else {
        lastCallHadCredential = false;
        lastCallHadSecret = false;
      }
      return null; // avoid requiring a NoOpSchedulerDriver
    }

    /**
     * Avoid checking actual env.
     */
    @Override
    protected boolean isSideChannelActive() {
      return sideChannelActive;
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
