package com.mesosphere.sdk.config;

import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConfigurationUpdaterTest {
    private static final UUID TARGET_ID = UUID.randomUUID();
    private static final UUID NEW_ID = UUID.randomUUID();
    private static final UUID UNKNOWN_ID = UUID.randomUUID();

    private static final String SERVICE_NAME = "test-service";
    private static final int TASK_A_COUNT = 1;
    private static final String TASK_A_POD_NAME = "POD-A";
    private static final String TASK_A_NAME = "A";
    private static final double TASK_A_CPU = 1.0;
    private static final double UPDATED_TASK_A_CPU = TASK_A_CPU + 1.0;
    private static final double TASK_A_MEM = 1000.0;
    private static final double TASK_A_DISK = 1500.0;
    private static final String TASK_A_CMD = "echo " + TASK_A_NAME;

    private static final int TASK_B_COUNT = 2;
    private static final String TASK_B_POD_NAME = "POD-B";
    private static final String TASK_B_NAME = "B";
    private static final double TASK_B_CPU = 2.0;
    private static final double TASK_B_MEM = 2000.0;
    private static final double TASK_B_DISK = 2500.0;
    private static final String TASK_B_CMD = "echo " + TASK_B_NAME;

    private static final PodSpec podA = TestPodFactory.getPodSpec(
            TASK_A_POD_NAME,
            TestConstants.RESOURCE_SET_ID + "-A",
            TASK_A_NAME,
            TASK_A_CMD,
            TestConstants.SERVICE_USER,
            TASK_A_COUNT,
            TASK_A_CPU,
            TASK_A_MEM,
            TASK_A_DISK);

    private static final PodSpec podB = TestPodFactory.getPodSpec(
            TASK_B_POD_NAME,
            TestConstants.RESOURCE_SET_ID + "-B",
            TASK_B_NAME,
            TASK_B_CMD,
            TestConstants.SERVICE_USER,
            TASK_B_COUNT,
            TASK_B_CPU,
            TASK_B_MEM,
            TASK_B_DISK);

    private static final PodSpec updatedPodA = TestPodFactory.getPodSpec(
            TASK_A_POD_NAME,
            TestConstants.RESOURCE_SET_ID + "-A",
            TASK_A_NAME,
            TASK_A_CMD,
            TestConstants.SERVICE_USER,
            TASK_A_COUNT,
            UPDATED_TASK_A_CPU,
            TASK_A_MEM,
            TASK_A_DISK);

    private static final PodSpec badPodB = TestPodFactory.getPodSpec(
            TASK_B_POD_NAME,
            TestConstants.RESOURCE_SET_ID + "-B",
            TASK_B_NAME,
            TASK_B_CMD,
            TestConstants.SERVICE_USER,
            TASK_B_COUNT - 1,
            TASK_B_CPU,
            TASK_B_MEM,
            TASK_B_DISK);

    private static final ServiceSpec getServiceSpec(PodSpec podA, PodSpec podB) {
        return DefaultServiceSpec.newBuilder()
                .name(SERVICE_NAME)
                .role(TestConstants.ROLE)
                .user(DcosConstants.DEFAULT_SERVICE_USER)
                .principal(TestConstants.PRINCIPAL)
                .zookeeperConnection("foo.bar.com")
                .pods(Arrays.asList(podA, podB))
                .build();
    }

    private static final ServiceSpec ORIGINAL_SERVICE_SPECIFICATION = getServiceSpec(podA, podB);
    private static final ServiceSpec UPDATED_SERVICE_SPECIFICATION = getServiceSpec(updatedPodA, podB);
    private static final ServiceSpec BAD_UPDATED_SERVICE_SPECIFICATION = getServiceSpec(podA, badPodB);
    private static final ServiceSpec ORIGINAL_SERVICE_SPECIFICATION_WITH_USER =
            DefaultServiceSpec.newBuilder(ORIGINAL_SERVICE_SPECIFICATION)
                    .user(DcosConstants.DEFAULT_SERVICE_USER).build();

    @Mock private StateStore mockStateStore;
    @Mock private ConfigStore<ServiceSpec> mockConfigStore;
    @Mock private ServiceSpec mockUnknownConfig;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testZeroValidations() throws ConfigStoreException {
        final ConfigurationUpdater<ServiceSpec> configurationUpdater =
                new DefaultConfigurationUpdater(
                        mockStateStore,
                        mockConfigStore,
                        DefaultServiceSpec.getComparatorInstance(),
                        Collections.emptyList());
        when(mockConfigStore.getTargetConfig()).thenReturn(TARGET_ID);
        when(mockConfigStore.fetch(TARGET_ID)).thenReturn(ORIGINAL_SERVICE_SPECIFICATION);
        when(mockConfigStore.store(UPDATED_SERVICE_SPECIFICATION)).thenReturn(NEW_ID);
        ConfigurationUpdater.UpdateResult result = configurationUpdater.updateConfiguration(UPDATED_SERVICE_SPECIFICATION);
        verify(mockConfigStore).setTargetConfig(NEW_ID);
        Assert.assertEquals(NEW_ID, result.getTargetId());
        Assert.assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testHandleInvalidConfig() throws Exception {
        final ConfigurationUpdater<ServiceSpec> configurationUpdater =
                new DefaultConfigurationUpdater(
                        mockStateStore,
                        mockConfigStore,
                        DefaultServiceSpec.getComparatorInstance(),
                        DefaultScheduler.defaultConfigValidators(OfferRequirementTestUtils.getTestSchedulerFlags()));
        when(mockConfigStore.getTargetConfig()).thenReturn(TARGET_ID);
        when(mockConfigStore.fetch(TARGET_ID)).thenReturn(ORIGINAL_SERVICE_SPECIFICATION);
        when(mockConfigStore.store(UPDATED_SERVICE_SPECIFICATION)).thenReturn(NEW_ID);
        when(mockConfigStore.list()).thenReturn(Arrays.asList(TARGET_ID, NEW_ID, UNKNOWN_ID));
        when(mockConfigStore.fetch(UNKNOWN_ID)).thenReturn(mockUnknownConfig);
        ConfigurationUpdater.UpdateResult result = configurationUpdater.updateConfiguration(UPDATED_SERVICE_SPECIFICATION);
        verify(mockConfigStore).setTargetConfig(NEW_ID);
        Assert.assertEquals(NEW_ID, result.getTargetId());
        Assert.assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testValidationDifferentConfigs() throws ConfigStoreException {
        final ConfigurationUpdater<ServiceSpec> configurationUpdater = new DefaultConfigurationUpdater(
                mockStateStore,
                mockConfigStore,
                DefaultServiceSpec.getComparatorInstance(),
                DefaultScheduler.defaultConfigValidators(OfferRequirementTestUtils.getTestSchedulerFlags()));
        when(mockConfigStore.getTargetConfig()).thenReturn(TARGET_ID);
        when(mockConfigStore.fetch(TARGET_ID)).thenReturn(ORIGINAL_SERVICE_SPECIFICATION);
        when(mockConfigStore.store(UPDATED_SERVICE_SPECIFICATION)).thenReturn(NEW_ID);
        ConfigurationUpdater.UpdateResult result = configurationUpdater.updateConfiguration(UPDATED_SERVICE_SPECIFICATION);
        verify(mockConfigStore).setTargetConfig(NEW_ID);
        Assert.assertEquals(NEW_ID, result.getTargetId());
        Assert.assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testHandleInvalidConfigSameConfig() throws Exception {
        final ConfigurationUpdater<ServiceSpec> configurationUpdater =
                new DefaultConfigurationUpdater(
                        mockStateStore,
                        mockConfigStore,
                        DefaultServiceSpec.getComparatorInstance(),
                        DefaultScheduler.defaultConfigValidators(OfferRequirementTestUtils.getTestSchedulerFlags()));
        when(mockConfigStore.getTargetConfig()).thenReturn(TARGET_ID);
        when(mockConfigStore.fetch(TARGET_ID)).thenReturn(ORIGINAL_SERVICE_SPECIFICATION);
        when(mockConfigStore.list()).thenReturn(Arrays.asList(TARGET_ID, NEW_ID, UNKNOWN_ID));
        when(mockConfigStore.fetch(UNKNOWN_ID)).thenReturn(mockUnknownConfig);
        ConfigurationUpdater.UpdateResult result = configurationUpdater.updateConfiguration(ORIGINAL_SERVICE_SPECIFICATION);
        Assert.assertEquals(TARGET_ID, result.getTargetId());
        Assert.assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testValidationSameConfig() throws ConfigStoreException {
        // strings are equal, so validation of ints is skipped:
        final ConfigurationUpdater<ServiceSpec> configurationUpdater = new DefaultConfigurationUpdater(
                mockStateStore,
                mockConfigStore,
                DefaultServiceSpec.getComparatorInstance(),
                DefaultScheduler.defaultConfigValidators(OfferRequirementTestUtils.getTestSchedulerFlags()));
        when(mockConfigStore.getTargetConfig()).thenReturn(TARGET_ID);
        when(mockConfigStore.fetch(TARGET_ID)).thenReturn(ORIGINAL_SERVICE_SPECIFICATION);
        ConfigurationUpdater.UpdateResult result = configurationUpdater.updateConfiguration(ORIGINAL_SERVICE_SPECIFICATION);
        Assert.assertEquals(TARGET_ID, result.getTargetId());
        Assert.assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testValidationSingleError() throws ConfigStoreException {
        final ConfigurationUpdater<ServiceSpec> configurationUpdater = new DefaultConfigurationUpdater(
                mockStateStore,
                mockConfigStore,
                DefaultServiceSpec.getComparatorInstance(),
                DefaultScheduler.defaultConfigValidators(OfferRequirementTestUtils.getTestSchedulerFlags()));
        when(mockConfigStore.getTargetConfig()).thenReturn(TARGET_ID);
        when(mockConfigStore.fetch(TARGET_ID)).thenReturn(ORIGINAL_SERVICE_SPECIFICATION);
        ConfigurationUpdater.UpdateResult result = configurationUpdater.updateConfiguration(BAD_UPDATED_SERVICE_SPECIFICATION);
        Assert.assertEquals(TARGET_ID, result.getTargetId());
        Assert.assertEquals(1, result.getErrors().size());
    }


    @Test
    public void testUserSetAtPodLevelButNotAtServiceLevel() throws ConfigStoreException {
        final ConfigurationUpdater<ServiceSpec> configurationUpdater = new DefaultConfigurationUpdater(
                mockStateStore,
                mockConfigStore,
                DefaultServiceSpec.getComparatorInstance(),
                DefaultScheduler.defaultConfigValidators(OfferRequirementTestUtils.getTestSchedulerFlags()));
        when(mockConfigStore.getTargetConfig()).thenReturn(TARGET_ID);
        // ORIGINAL_SERVICE_SPECIFICATION doesn't set service user
        when(mockConfigStore.fetch(TARGET_ID)).thenReturn(ORIGINAL_SERVICE_SPECIFICATION);
        ConfigurationUpdater.UpdateResult result =
                configurationUpdater.updateConfiguration(ORIGINAL_SERVICE_SPECIFICATION_WITH_USER);
        Assert.assertEquals(TARGET_ID, result.getTargetId());
        Assert.assertEquals(0, result.getErrors().size());
    }


    @Test
    public void testUserSetAtServiceLevelButNotPodLevel() throws ConfigStoreException {
        final ConfigurationUpdater<ServiceSpec> configurationUpdater = new DefaultConfigurationUpdater(
                mockStateStore,
                mockConfigStore,
                DefaultServiceSpec.getComparatorInstance(),
                DefaultScheduler.defaultConfigValidators(OfferRequirementTestUtils.getTestSchedulerFlags()));
        when(mockConfigStore.getTargetConfig()).thenReturn(TARGET_ID);
        DefaultServiceSpec.Builder serviceSpecWithUser = DefaultServiceSpec.newBuilder(ORIGINAL_SERVICE_SPECIFICATION)
                .user(DcosConstants.DEFAULT_SERVICE_USER);
        List<PodSpec> podsWithoutUsers = new ArrayList<>();
        for (PodSpec podSpec : ORIGINAL_SERVICE_SPECIFICATION.getPods()) {
            podsWithoutUsers.add(
                    DefaultPodSpec.newBuilder(podSpec).user(null).build()
            );
        }
        serviceSpecWithUser.pods(podsWithoutUsers);

        when(mockConfigStore.fetch(TARGET_ID)).thenReturn(serviceSpecWithUser.build());
        // Note: the new service spec sets pod users with a non-root user
        ConfigurationUpdater.UpdateResult result =
                configurationUpdater.updateConfiguration(ORIGINAL_SERVICE_SPECIFICATION_WITH_USER);
        Assert.assertEquals(TARGET_ID, result.getTargetId());
        // since the 2 pods don't set the user their user defaults to "root" which conflicts with the user set as noted above
        Assert.assertEquals(2, result.getErrors().size());
    }


    @Test
    public void testPodsUsersUnsetInPreviousServiceSpecButSetToRootInNewServiceSpec() throws ConfigStoreException {
        final ConfigurationUpdater<ServiceSpec> configurationUpdater = new DefaultConfigurationUpdater(
                mockStateStore,
                mockConfigStore,
                DefaultServiceSpec.getComparatorInstance(),
                DefaultScheduler.defaultConfigValidators(OfferRequirementTestUtils.getTestSchedulerFlags()));
        when(mockConfigStore.getTargetConfig()).thenReturn(TARGET_ID);
        DefaultServiceSpec.Builder serviceSpecWithUser = DefaultServiceSpec.newBuilder(ORIGINAL_SERVICE_SPECIFICATION)
                .user(DcosConstants.DEFAULT_SERVICE_USER);
        List<PodSpec> podsWithoutUsers = new ArrayList<>();
        for (PodSpec podSpec : ORIGINAL_SERVICE_SPECIFICATION.getPods()) {
            podsWithoutUsers.add(
                    DefaultPodSpec.newBuilder(podSpec).user(null).build()
            );
        }
        serviceSpecWithUser.pods(podsWithoutUsers);

        when(mockConfigStore.fetch(TARGET_ID)).thenReturn(serviceSpecWithUser.build());

        // set the pod users for the new service spec to "root" to match the default of the non-set user from
        // previous service spec
        List<PodSpec> podsWithUsers = new ArrayList<>();
        for (PodSpec podSpec : ORIGINAL_SERVICE_SPECIFICATION_WITH_USER.getPods()) {
            podsWithUsers.add(
                DefaultPodSpec.newBuilder(podSpec).user(DcosConstants.DEFAULT_SERVICE_USER).build()
            );
        }
        ServiceSpec SERVICE_SPECIFICATION_WITH_USER = DefaultServiceSpec
                .newBuilder(ORIGINAL_SERVICE_SPECIFICATION_WITH_USER)
                .pods(podsWithUsers)
                .build();

        ConfigurationUpdater.UpdateResult result =
                configurationUpdater.updateConfiguration(SERVICE_SPECIFICATION_WITH_USER);
        Assert.assertEquals(TARGET_ID, result.getTargetId());
        Assert.assertEquals(0, result.getErrors().size());
    }
}
