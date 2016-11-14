package com.mesosphere.sdk.reference.scheduler;

import org.apache.mesos.specification.DefaultServiceSpec;
import org.apache.mesos.specification.yaml.YAMLServiceSpecFactory;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Collections;

import static org.apache.mesos.specification.yaml.YAMLServiceSpecFactory.generateRawSpecFromYAML;

public class ServiceSpecTest {
    @Test
    public void testServiceSpecDeserialization() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("svc.yml").getFile());
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory
                .generateSpecFromYAML(generateRawSpecFromYAML(file));
        Assert.assertNotNull(serviceSpec);
        DefaultServiceSpec.getFactory(serviceSpec, Collections.emptyList());
    }
}
