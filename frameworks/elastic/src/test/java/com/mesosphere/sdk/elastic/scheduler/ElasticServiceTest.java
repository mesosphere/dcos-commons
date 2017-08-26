package com.mesosphere.sdk.elastic.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;

import org.junit.Test;

public class ElasticServiceTest extends BaseServiceSpecTest {

    public ElasticServiceTest() {
        super();
    }

    @Test
    public void testYaml() throws Exception {
        super.testYaml("svc.yml");
    }

}
