package com.mesosphere.sdk.scheduler;

import org.apache.mesos.MesosSchedulerDriver;
import org.apache.mesos.Protos.*;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

public class SchedulerDriverFactoryTest {

    @Mock
    private SchedulerConfig mockSchedulerConfig;

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

    @Before
    public void beforeAll() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSuccessNoAuth() throws Exception {
        CustomSchedulerDriverFactory factory = new CustomSchedulerDriverFactory();
        assertNull(factory.create(
                new NoOpScheduler(), FRAMEWORK_WITH_PRINCIPAL, MASTER_URL, mockSchedulerConfig));
        assertEquals(1, factory.createCalls);
        assertFalse(factory.lastCallHadCredential);
        assertFalse(factory.lastCallHadSecret);
    }

    @Test
    public void testSuccessSidechannel() throws Exception {
        when(mockSchedulerConfig.isSideChannelActive()).thenReturn(true);
        CustomSchedulerDriverFactory factory = new CustomSchedulerDriverFactory();
        assertNull(factory.create(
                new NoOpScheduler(), FRAMEWORK_WITH_PRINCIPAL, MASTER_URL, mockSchedulerConfig));
        assertEquals(1, factory.createCalls);
        assertTrue(factory.lastCallHadCredential);
        assertFalse(factory.lastCallHadSecret);
    }

    @Test
    public void testSuccessWithSecret() throws Exception {
        CustomSchedulerDriverFactory factory = new CustomSchedulerDriverFactory();
        assertNull(factory.create(
                new NoOpScheduler(), FRAMEWORK_WITH_PRINCIPAL, MASTER_URL, mockSchedulerConfig, SECRET));
        assertEquals(1, factory.createCalls);
        assertTrue(factory.lastCallHadCredential);
        assertTrue(factory.lastCallHadSecret);
    }

    @Test
    public void testSuccessWithSecretAndSidechannel() throws Exception {
        when(mockSchedulerConfig.isSideChannelActive()).thenReturn(true);
        CustomSchedulerDriverFactory factory = new CustomSchedulerDriverFactory();
        assertNull(factory.create(
                new NoOpScheduler(), FRAMEWORK_WITH_PRINCIPAL, MASTER_URL, mockSchedulerConfig, SECRET));
        assertEquals(1, factory.createCalls);
        assertTrue(factory.lastCallHadCredential);
        assertTrue(factory.lastCallHadSecret); // secret takes priority over sidechannel
    }

    @Test
    public void testEmptyPrincipalNoAuth() throws Exception {
        CustomSchedulerDriverFactory factory = new CustomSchedulerDriverFactory();
        assertNull(factory.create(
                new NoOpScheduler(), FRAMEWORK_EMPTY_PRINCIPAL, MASTER_URL, mockSchedulerConfig));
        assertEquals(1, factory.createCalls);
        assertFalse(factory.lastCallHadCredential);
        assertFalse(factory.lastCallHadSecret);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testEmptyPrincipalSidechannel() throws Exception {
        when(mockSchedulerConfig.isSideChannelActive()).thenReturn(true);
        new CustomSchedulerDriverFactory().create(
                new NoOpScheduler(), FRAMEWORK_EMPTY_PRINCIPAL, MASTER_URL, mockSchedulerConfig);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testEmptyPrincipalWithSecret() throws Exception {
        new CustomSchedulerDriverFactory().create(
                new NoOpScheduler(), FRAMEWORK_EMPTY_PRINCIPAL, MASTER_URL, mockSchedulerConfig, SECRET);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testEmptyPrincipalWithSecretAndSidechannel() throws Exception {
        when(mockSchedulerConfig.isSideChannelActive()).thenReturn(true);
        new CustomSchedulerDriverFactory().create(
                new NoOpScheduler(), FRAMEWORK_EMPTY_PRINCIPAL, MASTER_URL, mockSchedulerConfig, SECRET);
    }

    @Test
    public void testMissingPrincipalNoAuth() throws Exception {
        CustomSchedulerDriverFactory factory = new CustomSchedulerDriverFactory();
        assertNull(factory.create(
                new NoOpScheduler(), FRAMEWORK_WITHOUT_PRINCIPAL, MASTER_URL, mockSchedulerConfig));
        assertEquals(1, factory.createCalls);
        assertFalse(factory.lastCallHadCredential);
        assertFalse(factory.lastCallHadSecret);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testMissingPrincipalSidechannel() throws Exception {
        when(mockSchedulerConfig.isSideChannelActive()).thenReturn(true);
        new CustomSchedulerDriverFactory().create(
                new NoOpScheduler(), FRAMEWORK_WITHOUT_PRINCIPAL, MASTER_URL, mockSchedulerConfig);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testMissingPrincipalWithSecret() throws Exception {
        new CustomSchedulerDriverFactory().create(
                new NoOpScheduler(), FRAMEWORK_WITHOUT_PRINCIPAL, MASTER_URL, mockSchedulerConfig, SECRET);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testMissingPrincipalWithSecretAndSidechannel() throws Exception {
        when(mockSchedulerConfig.isSideChannelActive()).thenReturn(true);
        new CustomSchedulerDriverFactory().create(
                new NoOpScheduler(), FRAMEWORK_WITHOUT_PRINCIPAL, MASTER_URL, mockSchedulerConfig, SECRET);
    }

    private static class CustomSchedulerDriverFactory extends SchedulerDriverFactory {

        public int createCalls = 0;
        public boolean lastCallHadCredential = false;
        public boolean lastCallHadSecret = false;

        private CustomSchedulerDriverFactory() { }

        /**
         * Avoid calls to the MesosSchedulerDriver constructor, which triggers errors about libmesos not
         * being present.
         */
        @Override
        protected MesosSchedulerDriver createInternal(
                final Scheduler scheduler,
                final FrameworkInfo frameworkInfo,
                final String masterUrl,
                final Credential credential,
                final String mesosAPIVersion) {
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
    }

    private static class NoOpScheduler implements Scheduler {

        @Override
        public void registered(SchedulerDriver driver, FrameworkID frameworkId, MasterInfo masterInfo) { }

        @Override
        public void reregistered(SchedulerDriver driver, MasterInfo masterInfo) { }

        @Override
        public void resourceOffers(SchedulerDriver driver, List<Offer> offers) { }

        @Override
        public void offerRescinded(SchedulerDriver driver, OfferID offerId) { }

        @Override
        public void statusUpdate(SchedulerDriver driver, TaskStatus status) { }

        @Override
        public void frameworkMessage(SchedulerDriver driver, ExecutorID executorId, SlaveID slaveId, byte[] data) { }

        @Override
        public void disconnected(SchedulerDriver driver) { }

        @Override
        public void slaveLost(SchedulerDriver driver, SlaveID slaveId) { }

        @Override
        public void executorLost(SchedulerDriver driver, ExecutorID executorId, SlaveID slaveId, int status) { }

        @Override
        public void error(SchedulerDriver driver, String message) { }
    }
}
