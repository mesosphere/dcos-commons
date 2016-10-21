package org.apache.mesos.config;

import org.apache.mesos.config.validate.ConfigurationValidationError;
import org.apache.mesos.config.validate.ConfigurationValidator;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
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
            return null;
        }
    }

    ConfigurationValidator<TestConfig> test = (oConfig, nConfig) -> {
        if (oConfig != null && oConfig.getA() != nConfig.getA()) {
            return Arrays.asList(ConfigurationValidationError.valueError("a", "" + nConfig.getA(), "not equal"));
        }
        return Collections.emptyList();
    };

    @Test
    public void testZeroValidations() {
        final TestConfig oldConfig = new TestConfig(1);
        final TestConfig newConfig = new TestConfig(2);

        final ConfigurationUpdater<TestConfig> configurationValidator = new ConfigurationUpdater<>(Arrays.asList());
        final Collection<ConfigurationValidationError> validate = configurationValidator.validate(oldConfig, newConfig);

        Assert.assertNotNull(validate);
        Assert.assertTrue(validate.size() == 0);
    }

    @Test
    public void testNonZeroValidationsNoError() {
        final TestConfig oldConfig = new TestConfig(1);
        final TestConfig newConfig = new TestConfig(1);

        final ConfigurationUpdater<TestConfig> configurationValidator = new ConfigurationUpdater<>(Arrays.asList(test));
        final Collection<ConfigurationValidationError> validate = configurationValidator.validate(oldConfig, newConfig);

        Assert.assertNotNull(validate);
        Assert.assertTrue(validate.size() == 0);
    }

    @Test
    public void testNonZeroValidationsSingleError() {
        final TestConfig oldConfig = new TestConfig(1);
        final TestConfig newConfig = new TestConfig(2);

        final ConfigurationUpdater<TestConfig> configurationValidator = new ConfigurationUpdater<>(Arrays.asList(test));
        final Collection<ConfigurationValidationError> validate = configurationValidator.validate(oldConfig, newConfig);

        Assert.assertNotNull(validate);
        Assert.assertTrue(validate.size() == 1);
    }
}
