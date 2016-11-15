package com.mesosphere.sdk.hello_world.scheduler;

import org.apache.mesos.specification.DefaultServiceSpec;
import org.apache.mesos.specification.yaml.YAMLServiceSpecFactory;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.io.File;
import java.util.Collections;

import static org.apache.mesos.specification.yaml.YAMLServiceSpecFactory.generateRawSpecFromYAML;

public class ServiceSpecTest {
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void testServiceSpecDeserialization() throws Exception {
        environmentVariables.set("PORT0", "8080");
        environmentVariables.set("SLEEP_DURATION", "1000");
        environmentVariables.set("METADATA_COUNT", "2");
        environmentVariables.set("METADATA_CPUS", "0.1");
        environmentVariables.set("METADATA_MEM", "512");
        environmentVariables.set("METADATA_DISK", "5000");
        environmentVariables.set("DATA_COUNT", "3");
        environmentVariables.set("DATA_CPUS", "0.2");
        environmentVariables.set("DATA_MEM", "1024");
        environmentVariables.set("DATA_DISK", "5000");

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("svc.yml").getFile());

        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory
                .generateSpecFromYAML(generateRawSpecFromYAML(file));
        Assert.assertNotNull(serviceSpec);
        Assert.assertEquals(8080, serviceSpec.getApiPort());
        DefaultServiceSpec.getFactory(serviceSpec, Collections.emptyList());
    }
}
