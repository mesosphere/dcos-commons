package org.apache.mesos.config;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.config.validate.ConfigurationValidationError;
import org.apache.mesos.config.validate.ConfigurationValidator;
import org.apache.mesos.state.StateStore;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;

public class ConfigurationUpdaterTest {
    public static class TestConfig implements Configuration {
        private int a;

        public TestConfig(int a) {
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

        @Override
        public boolean equals(Object o) {
            return EqualsBuilder.reflectionEquals(this, o);
        }

        @Override
        public int hashCode() {
            return HashCodeBuilder.reflectionHashCode(this);
        }
    }

    @Mock private StateStore mockStateStore;
    @Mock private ConfigStore<TestConfig> mockConfigStore;

    ConfigurationValidator<TestConfig> test = (oConfig, nConfig) -> {
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
        //final TestConfig oldConfig = new TestConfig(1);
        final TestConfig newConfig = new TestConfig(2);

        final ConfigurationUpdater<TestConfig> configurationValidator = new ConfigurationUpdater<>(
                mockStateStore, mockConfigStore, Collections.emptyList());
        ConfigurationUpdater.UpdateResult result = configurationValidator.updateConfiguration(newConfig);
        Assert.assertTrue(result.errors.isEmpty());
    }

    @Test
    public void testNonZeroValidationsNoError() throws ConfigStoreException {
        //final TestConfig oldConfig = new TestConfig(1);
        final TestConfig newConfig = new TestConfig(1);

        final ConfigurationUpdater<TestConfig> configurationValidator = new ConfigurationUpdater<>(
                mockStateStore, mockConfigStore, Arrays.asList(test));
        ConfigurationUpdater.UpdateResult result = configurationValidator.updateConfiguration(newConfig);
        Assert.assertTrue(result.errors.isEmpty());
    }

    @Test
    public void testNonZeroValidationsSingleError() throws ConfigStoreException {
        //final TestConfig oldConfig = new TestConfig(1);
        final TestConfig newConfig = new TestConfig(2);

        final ConfigurationUpdater<TestConfig> configurationValidator = new ConfigurationUpdater<>(
                mockStateStore, mockConfigStore, Arrays.asList(test));
        ConfigurationUpdater.UpdateResult result = configurationValidator.updateConfiguration(newConfig);
        Assert.assertEquals(1, result.errors.size());
    }
}
