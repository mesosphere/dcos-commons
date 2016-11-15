package com.mesosphere.sdk.reference.scheduler;

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
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("svc.yml").getFile());
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory
                .generateSpecFromYAML(generateRawSpecFromYAML(file));
        Assert.assertNotNull(serviceSpec);
        Assert.assertEquals(8080, serviceSpec.getApiPort());
        DefaultServiceSpec.getFactory(serviceSpec, Collections.emptyList());
    }
}
