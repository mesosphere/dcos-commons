package com.mesosphere.sdk.elastic.scheduler;

import org.apache.mesos.dcos.DcosConstants;
import org.apache.mesos.specification.Service;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ElasticServiceTest {
    private Service service;

    @Before
    public void setUp() throws Exception {
        service = new ElasticService(10000, DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING);
    }

    @Test
    public void testConstruction() {
        Assert.assertNotNull(service);
    }

}