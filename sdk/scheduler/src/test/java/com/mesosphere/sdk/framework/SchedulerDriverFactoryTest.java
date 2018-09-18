package com.mesosphere.sdk.framework;

import com.mesosphere.mesos.HTTPAdapter.MesosToSchedulerDriverAdapter;
import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.mesos.MesosSchedulerDriver;
import org.apache.mesos.Protos.*;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.v1.scheduler.Mesos;
import org.apache.mesos.v1.scheduler.V0Mesos;
import org.apache.mesos.v1.scheduler.V1Mesos;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
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
    public void testSuccessNoAuth() {
        CustomSchedulerDriverFactory factory = new CustomSchedulerDriverFactory();
        assertNull(factory.create(
                new NoOpScheduler(), FRAMEWORK_WITH_PRINCIPAL, MASTER_URL, mockSchedulerConfig));
        assertEquals(1, factory.createCalls);
        assertFalse(factory.lastCallHadCredential);
        assertFalse(factory.lastCallHadSecret);
    }

    @Test
    public void testSuccessSidechannel() {
        when(mockSchedulerConfig.isSideChannelActive()).thenReturn(true);
        CustomSchedulerDriverFactory factory = new CustomSchedulerDriverFactory();
        assertNull(factory.create(
                new NoOpScheduler(), FRAMEWORK_WITH_PRINCIPAL, MASTER_URL, mockSchedulerConfig));
        assertEquals(1, factory.createCalls);
        assertTrue(factory.lastCallHadCredential);
        assertFalse(factory.lastCallHadSecret);
    }

    @Test
    public void testSuccessWithSecret() {
        CustomSchedulerDriverFactory factory = new CustomSchedulerDriverFactory();
        assertNull(factory.create(
                new NoOpScheduler(), FRAMEWORK_WITH_PRINCIPAL, MASTER_URL, mockSchedulerConfig, SECRET));
        assertEquals(1, factory.createCalls);
        assertTrue(factory.lastCallHadCredential);
        assertTrue(factory.lastCallHadSecret);
    }

    @Test
    public void testSuccessWithSecretAndSidechannel() {
        when(mockSchedulerConfig.isSideChannelActive()).thenReturn(true);
        CustomSchedulerDriverFactory factory = new CustomSchedulerDriverFactory();
        assertNull(factory.create(
                new NoOpScheduler(), FRAMEWORK_WITH_PRINCIPAL, MASTER_URL, mockSchedulerConfig, SECRET));
        assertEquals(1, factory.createCalls);
        assertTrue(factory.lastCallHadCredential);
        assertTrue(factory.lastCallHadSecret); // secret takes priority over sidechannel
    }

    @Test
    public void testEmptyPrincipalNoAuth() {
        CustomSchedulerDriverFactory factory = new CustomSchedulerDriverFactory();
        assertNull(factory.create(
                new NoOpScheduler(), FRAMEWORK_EMPTY_PRINCIPAL, MASTER_URL, mockSchedulerConfig));
        assertEquals(1, factory.createCalls);
        assertFalse(factory.lastCallHadCredential);
        assertFalse(factory.lastCallHadSecret);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testEmptyPrincipalSidechannel() {
        when(mockSchedulerConfig.isSideChannelActive()).thenReturn(true);
        new CustomSchedulerDriverFactory().create(
                new NoOpScheduler(), FRAMEWORK_EMPTY_PRINCIPAL, MASTER_URL, mockSchedulerConfig);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testEmptyPrincipalWithSecret() {
        new CustomSchedulerDriverFactory().create(
                new NoOpScheduler(), FRAMEWORK_EMPTY_PRINCIPAL, MASTER_URL, mockSchedulerConfig, SECRET);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testEmptyPrincipalWithSecretAndSidechannel() {
        when(mockSchedulerConfig.isSideChannelActive()).thenReturn(true);
        new CustomSchedulerDriverFactory().create(
                new NoOpScheduler(), FRAMEWORK_EMPTY_PRINCIPAL, MASTER_URL, mockSchedulerConfig, SECRET);
    }

    @Test
    public void testMissingPrincipalNoAuth() {
        CustomSchedulerDriverFactory factory = new CustomSchedulerDriverFactory();
        assertNull(factory.create(
                new NoOpScheduler(), FRAMEWORK_WITHOUT_PRINCIPAL, MASTER_URL, mockSchedulerConfig));
        assertEquals(1, factory.createCalls);
        assertFalse(factory.lastCallHadCredential);
        assertFalse(factory.lastCallHadSecret);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testMissingPrincipalSidechannel() {
        when(mockSchedulerConfig.isSideChannelActive()).thenReturn(true);
        new CustomSchedulerDriverFactory().create(
                new NoOpScheduler(), FRAMEWORK_WITHOUT_PRINCIPAL, MASTER_URL, mockSchedulerConfig);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testMissingPrincipalWithSecret() {
        new CustomSchedulerDriverFactory().create(
                new NoOpScheduler(), FRAMEWORK_WITHOUT_PRINCIPAL, MASTER_URL, mockSchedulerConfig, SECRET);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testMissingPrincipalWithSecretAndSidechannel() {
        when(mockSchedulerConfig.isSideChannelActive()).thenReturn(true);
        new CustomSchedulerDriverFactory().create(
                new NoOpScheduler(), FRAMEWORK_WITHOUT_PRINCIPAL, MASTER_URL, mockSchedulerConfig, SECRET);
    }

    @Test
    public void createInternalTest() {
        // This should have been an integration test but upstream has no version information (and it shouldn't have ?)
        for (ImmutableTriple<Boolean, String, ? extends Class<? extends Mesos>> data : Arrays.asList(
                // Triple of (Boolean V1 Capability Supported | Requested Version | Created Version)
                ImmutableTriple.of(true, SchedulerConfig.MESOS_API_VERSION_V1, V1Mesos.class),
                ImmutableTriple.of(true, "V0", V0Mesos.class),
                ImmutableTriple.of(false, SchedulerConfig.MESOS_API_VERSION_V1, V0Mesos.class),
                ImmutableTriple.of(false, "V0", V0Mesos.class)
        )) {
            Capabilities capabilities = mock(Capabilities.class);
            when(capabilities.supportsV1APIByDefault()).thenReturn(data.left);
            try {
                // This assert will fail if the system doesn't have the required system library.
                assertTrue(
                        new SchedulerDriverFactory().startInternalCustom(
                                mock(MesosToSchedulerDriverAdapter.class),
                                capabilities,
                                mock(FrameworkInfo.class),
                                "masterUrl",
                                mock(Credential.class),
                                data.middle
                        )
                        .getClass()
                        .getCanonicalName()
                        .equals(data.right.getCanonicalName())
                );
            } catch (UnsatisfiedLinkError e) {
                // We expect this to happen if there is no system library; test for the specific class name in trace.
                assertTrue(Stream
                        .of(e.getStackTrace())
                        .anyMatch(x -> x.getClassName().equals(data.right.getCanonicalName()))
                );
            } catch (NoClassDefFoundError e) {
                // JDK throws this error if we try to load up the system library more than once.
                assertTrue(e.getMessage().endsWith(data.right.getCanonicalName()));
            }
        }
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
