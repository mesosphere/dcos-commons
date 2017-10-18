package com.mesosphere.sdk.elastic.scheduler;

import org.junit.Test;

import com.mesosphere.sdk.testing.ServiceTestRunner;

public class ServiceTest {

    @Test
    public void testSpec() throws Exception {
        // these values are templated out in the universe packaging.
        // they would normally be provided via elastic's build.sh/versions.sh.
        new ServiceTestRunner()
                // These are set in our Main.java:
                .setPodEnv("master", "CLUSTER_NAME", "cluster-foo", "CUSTOM_YAML_BLOCK", "some.thing=true")
                .setPodEnv("data", "CLUSTER_NAME", "cluster-foo", "CUSTOM_YAML_BLOCK", "some.thing=true")
                .setPodEnv("ingest", "CLUSTER_NAME", "cluster-foo", "CUSTOM_YAML_BLOCK", "some.thing=true")
                .setPodEnv("coordinator", "CLUSTER_NAME", "cluster-foo", "CUSTOM_YAML_BLOCK", "some.thing=true")
                .setBuildTemplateParams(
                        "elastic-version", "1.2.3",
                        "support-diagnostics-version", "4.5.6")
                .run();
    }
}
