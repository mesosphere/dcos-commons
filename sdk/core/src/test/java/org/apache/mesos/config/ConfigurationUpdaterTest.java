package org.apache.mesos.config;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.config.validate.ConfigurationValidationError;
import org.apache.mesos.config.validate.ConfigurationValidator;
import org.apache.mesos.offer.TaskUtils;
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
    private static class TestConfig implements Configuration {
        private String name;
        private int a;

        public TestConfig(String name, int a) {
            this.name = name;
            this.a = a;
        }

        public int getA() {
            return a;
        }

        @Override
        public byte[] getBytes() throws ConfigStoreException {
            return new byte[0];
        }

        @Override
        public String toJsonString() throws Exception {
            return String.format("{ \"int\": %d }", a);
        }

        // intentionally just checking against the 'name' value to detect differences:
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TestConfig)) {
                return false;
            }
            return name.equals(((TestConfig)o).name);
        }

        @Override
        public int hashCode() {
            return HashCodeBuilder.reflectionHashCode(this);
        }
    }

    private static final UUID TARGET_ID = UUID.randomUUID();
    private static final UUID NEW_ID = UUID.randomUUID();

    @Mock private StateStore mockStateStore;
    @Mock private ConfigStore<TestConfig> mockConfigStore;

    ConfigurationValidator<TestConfig> testIntEquals = (oConfig, nConfig) -> {
        if (oConfig != null && oConfig.getA() != nConfig.getA()) {
            return Arrays.asList(ConfigurationValidationError.valueError("a", "" + nConfig.getA(), "not equal"));
        }
        return Collections.emptyList();
    };

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testZeroValidations() throws ConfigStoreException {
        final TestConfig targetConfig = new TestConfig("a", 1);
        final TestConfig newConfig = new TestConfig("b", 2);

        final ConfigurationUpdater<TestConfig> configurationValidator = new ConfigurationUpdater<>(
                mockStateStore, mockConfigStore, Collections.emptyList());
        when(mockConfigStore.getTargetConfig()).thenReturn(TARGET_ID);
        when(mockConfigStore.fetch(TARGET_ID)).thenReturn(targetConfig);
        when(mockConfigStore.store(newConfig)).thenReturn(NEW_ID);
        ConfigurationUpdater.UpdateResult result = configurationValidator.updateConfiguration(newConfig);
        verify(mockConfigStore).setTargetConfig(NEW_ID);
        Assert.assertEquals(NEW_ID, result.targetId);
        Assert.assertTrue(result.errors.isEmpty());
    }

    @Test
    public void testValidationDifferentConfigs() throws ConfigStoreException {
        final TestConfig targetConfig = new TestConfig("a", 1);
        final TestConfig newConfig = new TestConfig("b", 1);

        final ConfigurationUpdater<TestConfig> configurationValidator = new ConfigurationUpdater<>(
                mockStateStore, mockConfigStore, Arrays.asList(testIntEquals));
        when(mockConfigStore.getTargetConfig()).thenReturn(TARGET_ID);
        when(mockConfigStore.fetch(TARGET_ID)).thenReturn(targetConfig);
        when(mockConfigStore.store(newConfig)).thenReturn(NEW_ID);
        ConfigurationUpdater.UpdateResult result = configurationValidator.updateConfiguration(newConfig);
        verify(mockConfigStore).setTargetConfig(NEW_ID);
        Assert.assertEquals(NEW_ID, result.targetId);
        Assert.assertTrue(result.errors.isEmpty());
    }

    @Test
    public void testValidationEqualConfigs() throws ConfigStoreException {
        // validation should be performed even if the configs appear equal (see TestConfig.equals()):
        final TestConfig targetConfig = new TestConfig("a", 1);
        final TestConfig newConfig = new TestConfig("a", 2);

        final ConfigurationUpdater<TestConfig> configurationValidator = new ConfigurationUpdater<>(
                mockStateStore, mockConfigStore, Arrays.asList(testIntEquals));
        when(mockConfigStore.getTargetConfig()).thenReturn(TARGET_ID);
        when(mockConfigStore.fetch(TARGET_ID)).thenReturn(targetConfig);
        ConfigurationUpdater.UpdateResult result = configurationValidator.updateConfiguration(newConfig);
        Assert.assertEquals(TARGET_ID, result.targetId);
        Assert.assertEquals(1, result.errors.size());
    }

    @Test
    public void testValidationSameConfig() throws ConfigStoreException {
        // strings are equal, so validation of ints is skipped:
        final TestConfig targetConfig = new TestConfig("a", 1);

        final ConfigurationUpdater<TestConfig> configurationValidator = new ConfigurationUpdater<>(
                mockStateStore, mockConfigStore, Arrays.asList(testIntEquals));
        when(mockConfigStore.getTargetConfig()).thenReturn(TARGET_ID);
        when(mockConfigStore.fetch(TARGET_ID)).thenReturn(targetConfig);
        ConfigurationUpdater.UpdateResult result = configurationValidator.updateConfiguration(targetConfig);
        Assert.assertEquals(TARGET_ID, result.targetId);
        Assert.assertTrue(result.errors.isEmpty());
    }

    @Test
    public void testValidationSingleError() throws ConfigStoreException {
        final TestConfig targetConfig = new TestConfig("a", 1);
        final TestConfig newConfig = new TestConfig("b", 2);

        final ConfigurationUpdater<TestConfig> configurationValidator = new ConfigurationUpdater<>(
                mockStateStore, mockConfigStore, Arrays.asList(testIntEquals));
        when(mockConfigStore.getTargetConfig()).thenReturn(TARGET_ID);
        when(mockConfigStore.fetch(TARGET_ID)).thenReturn(targetConfig);
        ConfigurationUpdater.UpdateResult result = configurationValidator.updateConfiguration(newConfig);
        Assert.assertEquals(TARGET_ID, result.targetId);
        Assert.assertEquals(1, result.errors.size());
    }

    @Test
    public void testConfigCleanup() throws ConfigStoreException {
        final TestConfig targetConfig = new TestConfig("a", 1);
        final TestConfig newConfig = new TestConfig("b", 2);

        final UUID configId1 = UUID.randomUUID(); // used by 0 tasks
        final UUID configId2 = UUID.randomUUID(); // used by 1 task, doesn't equal target
        final TestConfig config2 = new TestConfig("c", 3);
        final UUID configId3 = UUID.randomUUID(); // used by 2 tasks, equals target
        final TestConfig config3 = new TestConfig("a", 4);

        final ConfigurationUpdater<TestConfig> configurationValidator = new ConfigurationUpdater<>(
                mockStateStore, mockConfigStore, Arrays.asList(testIntEquals));
        when(mockConfigStore.getTargetConfig()).thenReturn(TARGET_ID);
        when(mockConfigStore.fetch(TARGET_ID)).thenReturn(targetConfig);

        when(mockStateStore.fetchTasks()).thenReturn(
                Arrays.asList(taskInfo(configId2), taskInfo(configId3), taskInfo(configId2)));
        when(mockConfigStore.fetch(configId2)).thenReturn(config2);
        when(mockConfigStore.fetch(configId3)).thenReturn(config3);
        when(mockConfigStore.list()).thenReturn(Arrays.asList(TARGET_ID, configId1, configId2, configId3));

        ConfigurationUpdater.UpdateResult result = configurationValidator.updateConfiguration(newConfig);

        verify(mockStateStore).storeTasks(Arrays.asList(taskInfo(TARGET_ID))); // replaces configId4
        verify(mockConfigStore).clear(configId1);
        verify(mockConfigStore).clear(configId3);

        Assert.assertEquals(TARGET_ID, result.targetId);
        Assert.assertEquals(1, result.errors.size());
    }

    private static final TaskInfo taskInfo(UUID configId) {
        return TaskUtils.setTargetConfiguration(
                TaskTestUtils.getTaskInfo(Collections.emptyList()).toBuilder(), configId)
                .build();
    }
}
