package com.mesosphere.sdk.portworx.scheduler;

import org.junit.Test;

import com.mesosphere.sdk.testing.ServiceTestRunner;

public class ServiceTest {

    @Test
    public void testSpec() throws Exception {
        new ServiceTestRunner().run();
    }
}
