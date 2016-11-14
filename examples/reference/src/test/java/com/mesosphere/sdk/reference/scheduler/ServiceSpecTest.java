package com.mesosphere.sdk.reference.scheduler;

import org.apache.mesos.specification.DefaultService;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

/**
 * Created by gabriel on 11/13/16.
 */
public class ServiceSpecTest {
    @Test
    public void testServiceSpecDeserialization() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("svc.yml").getFile());
        DefaultService defaultService = new DefaultService(file);
        Assert.assertNotNull(defaultService);
    }
}
