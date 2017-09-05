package com.mesosphere.sdk.kafka.scheduler;

import org.junit.Test;

import com.mesosphere.sdk.testing.ServiceTestBuilder;

public class ServiceTest {

    @Test
    public void testSpec() throws Exception {
        new ServiceTestBuilder().render();
    }
}
