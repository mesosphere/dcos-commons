package com.mesosphere.sdk.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Paths;

public class YAMLConfigurationLoaderTest {
    public static class TestConfig {
        private String name;
        private int count;

        public TestConfig(){

        }

        public int getCount() {
            return count;
        }

        @JsonProperty("count")
        public void setCount(int count) {
            this.count = count;
        }

        public String getName() {
            return name;
        }

        @JsonProperty("name")
        public void setName(String name) {
            this.name = name;
        }
    }

    @Test
    public void testLoader() throws Exception {
        final TestConfig testConfig = YAMLConfigurationLoader.loadConfigFromEnv(TestConfig.class
                , Paths.get(this.getClass().getClassLoader().getResource("test.yml").toURI()).toString());

        Assert.assertEquals("DCOS", testConfig.getName());
        Assert.assertEquals(1, testConfig.getCount());
    }
}
