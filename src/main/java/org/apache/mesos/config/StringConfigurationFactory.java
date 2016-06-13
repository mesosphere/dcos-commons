package org.apache.mesos.config;

import java.nio.charset.StandardCharsets;

/**
 * This class implements a method for conversion of a byte array back into a StringConfiguration object.
 */
public class StringConfigurationFactory implements ConfigurationFactory<StringConfiguration> {

    @Override
    public StringConfiguration parse(byte[] bytes) {
        return new StringConfiguration(new String(bytes, StandardCharsets.UTF_8));
    }
}
