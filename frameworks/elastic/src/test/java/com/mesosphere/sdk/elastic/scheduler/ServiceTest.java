package com.mesosphere.sdk.elastic.scheduler;

import com.mesosphere.sdk.config.validate.ConfigValidator;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.testing.*;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;

public class ServiceTest {
    private static final String HOST_RULE = "[[\"@hostname\", \"UNIQUE\"]]";
    private static final String MAX_PER_ZONE_RULE = "[[\"@zone\", \"MAX_PER\", \"3\"]]";
    private static final String GROUP_BY_ZONE_RULE = "[[\"@zone\", \"GROUP_BY\", \"3\"]]";
    private static final ConfigValidator<ServiceSpec> validator = new ElasticZoneValidator();

    @Test
    public void testSpec() throws Exception {
        getDefaultRunner().run();
    }

    @Test
    public void rejectRackEnablement() throws Exception {
        ConfigValidatorUtils.rejectRackEnablement(validator, getDefaultRunner(), "MASTER_NODE_PLACEMENT");
        ConfigValidatorUtils.rejectRackEnablement(validator, getDefaultRunner(), "DATA_NODE_PLACEMENT");
        ConfigValidatorUtils.rejectRackEnablement(validator, getDefaultRunner(), "INGEST_NODE_PLACEMENT");
        ConfigValidatorUtils.rejectRackEnablement(validator, getDefaultRunner(), "COORDINATOR_NODE_PLACEMENT");
    }

    @Test
    public void rejectRackDisablement() throws Exception {
        ConfigValidatorUtils.rejectRackDisablement(validator, getDefaultRunner(), "MASTER_NODE_PLACEMENT");
        ConfigValidatorUtils.rejectRackDisablement(validator, getDefaultRunner(), "DATA_NODE_PLACEMENT");
        ConfigValidatorUtils.rejectRackDisablement(validator, getDefaultRunner(), "INGEST_NODE_PLACEMENT");
        ConfigValidatorUtils.rejectRackDisablement(validator, getDefaultRunner(), "COORDINATOR_NODE_PLACEMENT");
    }

    @Test
    public void allowRackChanges() throws Exception {
        ConfigValidatorUtils.allowRackChanges(validator, getDefaultRunner(), "MASTER_NODE_PLACEMENT");
        ConfigValidatorUtils.allowRackChanges(validator, getDefaultRunner(), "DATA_NODE_PLACEMENT");
        ConfigValidatorUtils.allowRackChanges(validator, getDefaultRunner(), "INGEST_NODE_PLACEMENT");
        ConfigValidatorUtils.allowRackChanges(validator, getDefaultRunner(), "COORDINATOR_NODE_PLACEMENT");
    }

    private ServiceTestRunner getDefaultRunner() {
        // `CLUSTER_NAME` and `CUSTOM_YAML_BLOCK` are set in our Main.java.
        // `ZONE` is set during offer evaluation.
        // `elastic-version` and `support-diagnostics-version` would normally be provided via elastic's
        // build.sh/versions.sh.
        return new ServiceTestRunner()
                .setPodEnv(
                        "master", "CLUSTER_NAME",
                        "cluster-foo", "CUSTOM_YAML_BLOCK",
                        "some.thing=true",
                        "ZONE", "us-east-1a")
                .setPodEnv(
                        "data", "CLUSTER_NAME",
                        "cluster-foo", "CUSTOM_YAML_BLOCK",
                        "some.thing=true",
                        "ZONE", "us-east-1a")
                .setPodEnv(
                        "ingest", "CLUSTER_NAME",
                        "cluster-foo", "CUSTOM_YAML_BLOCK",
                        "some.thing=true",
                        "ZONE", "us-east-1a")
                .setPodEnv(
                        "coordinator", "CLUSTER_NAME",
                        "cluster-foo", "CUSTOM_YAML_BLOCK",
                        "some.thing=true",
                        "ZONE", "us-east-1a")
                .setBuildTemplateParams(
                        "elastic-version", "1.2.3",
                        "support-diagnostics-version", "4.5.6");
    }
}
