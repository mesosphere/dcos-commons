package org.apache.mesos.config;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.scheduler.DefaultScheduler;
import org.apache.mesos.specification.DefaultServiceSpecification;
import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.specification.TestTaskSetFactory;
import org.apache.mesos.state.StateStore;
import org.apache.mesos.testutils.TaskTestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConfigurationUpdaterTest {
    private static final UUID TARGET_ID = UUID.randomUUID();
    private static final UUID NEW_ID = UUID.randomUUID();

    private static final String SERVICE_NAME = "test-service";
    private static final int TASK_A_COUNT = 1;
    private static final String TASK_A_NAME = "A";
    private static final double TASK_A_CPU = 1.0;
    private static final double UPDATED_TASK_A_CPU = TASK_A_CPU + 1.0;
    private static final double TASK_A_MEM = 1000.0;
    private static final double TASK_A_DISK = 1500.0;
    private static final String TASK_A_CMD = "echo " + TASK_A_NAME;

    private static final int TASK_B_COUNT = 2;
    private static final String TASK_B_NAME = "B";
    private static final double TASK_B_CPU = 2.0;
    private static final double TASK_B_MEM = 2000.0;
    private static final double TASK_B_DISK = 2500.0;
    private static final String TASK_B_CMD = "echo " + TASK_B_NAME;

    private static final ServiceSpecification ORIGINAL_SERVICE_SPECIFICATION = new DefaultServiceSpecification(
            SERVICE_NAME,
            Arrays.asList(
                    TestTaskSetFactory.getTaskSet(
                            TASK_A_NAME,
                            TASK_A_COUNT,
                            TASK_A_CMD,
                            TASK_A_CPU,
                            TASK_A_MEM,
                            TASK_A_DISK),
                    TestTaskSetFactory.getTaskSet(
                            TASK_B_NAME,
                            TASK_B_COUNT,
                            TASK_B_CMD,
                            TASK_B_CPU,
                            TASK_B_MEM,
                            TASK_B_DISK)));

    private static final ServiceSpecification UPDATED_SERVICE_SPECIFICATION = new DefaultServiceSpecification(
            SERVICE_NAME,
            Arrays.asList(
                    TestTaskSetFactory.getTaskSet(
                            TASK_A_NAME,
                            TASK_A_COUNT,
                            TASK_A_CMD,
                            UPDATED_TASK_A_CPU,
                            TASK_A_MEM,
                            TASK_A_DISK),
                    TestTaskSetFactory.getTaskSet(
                            TASK_B_NAME,
                            TASK_B_COUNT,
                            TASK_B_CMD,
                            TASK_B_CPU,
                            TASK_B_MEM,
                            TASK_B_DISK)));

    private static final ServiceSpecification BAD_UPDATED_SERVICE_SPECIFICATION = new DefaultServiceSpecification(
            SERVICE_NAME,
            Arrays.asList(
                    TestTaskSetFactory.getTaskSet(
                            TASK_A_NAME,
                            TASK_A_COUNT,
                            TASK_A_CMD,
                            UPDATED_TASK_A_CPU,
                            TASK_A_MEM,
                            TASK_A_DISK),
                    TestTaskSetFactory.getTaskSet(
                            TASK_B_NAME,
                            TASK_B_COUNT - 1,
                            TASK_B_CMD,
                            TASK_B_CPU,
                            TASK_B_MEM,
                            TASK_B_DISK)));

    @Mock private StateStore mockStateStore;
    @Mock private ConfigStore<ServiceSpecification> mockConfigStore;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testZeroValidations() throws ConfigStoreException {
        final ConfigurationUpdater<ServiceSpecification> configurationUpdater =
                new DefaultConfigurationUpdater(
                        mockStateStore,
                        mockConfigStore,
                        DefaultServiceSpecification.getComparatorInstance(),
                        Collections.emptyList());
        when(mockConfigStore.getTargetConfig()).thenReturn(TARGET_ID);
        when(mockConfigStore.fetch(TARGET_ID)).thenReturn(ORIGINAL_SERVICE_SPECIFICATION);
        when(mockConfigStore.store(UPDATED_SERVICE_SPECIFICATION)).thenReturn(NEW_ID);
        ConfigurationUpdater.UpdateResult result = configurationUpdater.updateConfiguration(UPDATED_SERVICE_SPECIFICATION);
        verify(mockConfigStore).setTargetConfig(NEW_ID);
        Assert.assertEquals(NEW_ID, result.targetId);
        Assert.assertTrue(result.errors.isEmpty());
    }

    @Test
    public void testValidationDifferentConfigs() throws ConfigStoreException {
        final ConfigurationUpdater<ServiceSpecification> configurationUpdater = new DefaultConfigurationUpdater(
                mockStateStore, mockConfigStore, DefaultServiceSpecification.getComparatorInstance(), DefaultScheduler.defaultConfigValidators());
        when(mockConfigStore.getTargetConfig()).thenReturn(TARGET_ID);
        when(mockConfigStore.fetch(TARGET_ID)).thenReturn(ORIGINAL_SERVICE_SPECIFICATION);
        when(mockConfigStore.store(UPDATED_SERVICE_SPECIFICATION)).thenReturn(NEW_ID);
        ConfigurationUpdater.UpdateResult result = configurationUpdater.updateConfiguration(UPDATED_SERVICE_SPECIFICATION);
        verify(mockConfigStore).setTargetConfig(NEW_ID);
        Assert.assertEquals(NEW_ID, result.targetId);
        Assert.assertTrue(result.errors.isEmpty());
    }

    @Test
    public void testValidationSameConfig() throws ConfigStoreException {
        // strings are equal, so validation of ints is skipped:
        final ConfigurationUpdater<ServiceSpecification> configurationUpdater = new DefaultConfigurationUpdater(
                mockStateStore, mockConfigStore, DefaultServiceSpecification.getComparatorInstance(), DefaultScheduler.defaultConfigValidators());
        when(mockConfigStore.getTargetConfig()).thenReturn(TARGET_ID);
        when(mockConfigStore.fetch(TARGET_ID)).thenReturn(ORIGINAL_SERVICE_SPECIFICATION);
        ConfigurationUpdater.UpdateResult result = configurationUpdater.updateConfiguration(ORIGINAL_SERVICE_SPECIFICATION);
        Assert.assertEquals(TARGET_ID, result.targetId);
        Assert.assertTrue(result.errors.isEmpty());
    }

    @Test
    public void testValidationSingleError() throws ConfigStoreException {
        final ConfigurationUpdater<ServiceSpecification> configurationUpdater = new DefaultConfigurationUpdater(
                mockStateStore, mockConfigStore, DefaultServiceSpecification.getComparatorInstance(), DefaultScheduler.defaultConfigValidators());
        when(mockConfigStore.getTargetConfig()).thenReturn(TARGET_ID);
        when(mockConfigStore.fetch(TARGET_ID)).thenReturn(ORIGINAL_SERVICE_SPECIFICATION);
        ConfigurationUpdater.UpdateResult result = configurationUpdater.updateConfiguration(BAD_UPDATED_SERVICE_SPECIFICATION);
        Assert.assertEquals(TARGET_ID, result.targetId);
        Assert.assertEquals(1, result.errors.size());
    }

    private static final Protos.TaskInfo taskInfo(UUID configId) {
        return TaskUtils.setTargetConfiguration(
                TaskTestUtils.getTaskInfo(Collections.emptyList()).toBuilder(), configId)
                .build();
    }
}
