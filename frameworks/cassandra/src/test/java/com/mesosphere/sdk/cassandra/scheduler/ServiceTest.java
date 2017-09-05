package com.mesosphere.sdk.cassandra.scheduler;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.mesosphere.sdk.testing.ServiceTestBuilder;
import com.mesosphere.sdk.testing.ServiceTestResult;

public class ServiceTest {

    @Test
    public void testSpec() throws Exception {
        new ServiceTestBuilder().render();
    }

    @Test
    public void testSpecCustomUser() throws Exception {
        ServiceTestResult result = new ServiceTestBuilder()
            .setOption("service.user", "foo")
            .render();
        assertEquals("foo", result.getServiceSpec().getUser());
        assertEquals("foo", result.getServiceSpec().getPods().get(0).getUser().get());
    }
}
