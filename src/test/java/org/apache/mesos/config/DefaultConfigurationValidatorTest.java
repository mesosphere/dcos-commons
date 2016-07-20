package org.apache.mesos.config;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DefaultConfigurationValidatorTest {
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

    ConfigurationValidation test = ((oldConfig, newConfig) -> {
        final TestConfig oConfig = (TestConfig) oldConfig;
        final TestConfig nConfig = (TestConfig) newConfig;

        if (oConfig.getA() != nConfig.getA()) {
            return Arrays.asList(new ConfigurationValidationError("a", "not equal"));
        }

        return Collections.emptyList();
    });

    @Test
    public void testZeroValidations() {
        final TestConfig oldConfig = new TestConfig(1);
        final TestConfig newConfig = new TestConfig(2);

        final DefaultConfigurationValidator configurationValidator = new DefaultConfigurationValidator(Collections.emptyList());
        final Collection<ConfigurationValidationError> validate = configurationValidator.validate(oldConfig, newConfig);

        Assert.assertNotNull(validate);
        Assert.assertTrue(validate.size() == 0);
    }

    @Test
    public void testNonZeroValidationsNoError() {
        final TestConfig oldConfig = new TestConfig(1);
        final TestConfig newConfig = new TestConfig(1);

        final DefaultConfigurationValidator configurationValidator
                = new DefaultConfigurationValidator(Arrays.asList(test));
        final Collection<ConfigurationValidationError> validate = configurationValidator.validate(oldConfig, newConfig);

        Assert.assertNotNull(validate);
        Assert.assertTrue(validate.size() == 0);
    }

    @Test
    public void testNonZeroValidationsSingleError() {
        final TestConfig oldConfig = new TestConfig(1);
        final TestConfig newConfig = new TestConfig(2);

        final DefaultConfigurationValidator configurationValidator
                = new DefaultConfigurationValidator(Arrays.asList(test));
        final Collection<ConfigurationValidationError> validate = configurationValidator.validate(oldConfig, newConfig);

        Assert.assertNotNull(validate);
        Assert.assertTrue(validate.size() == 1);
    }
}
