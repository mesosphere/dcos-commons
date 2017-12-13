package com.mesosphere.sdk.couchdb.scheduler;

import com.mesosphere.sdk.testing.ServiceTestRunner;
import org.junit.Test;

public class ServiceTest {

    @Test
    public void testSpec() throws Exception {
        new ServiceTestRunner().run();
    }
}
