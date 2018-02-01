package com.mesosphere.sdk.elastic.scheduler;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.testing.*;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;

public class ServiceTest {
    private static final String HOST_RULE = "[[\"@hostname\", \"UNIQUE\"]]";
    private static final String MAX_PER_ZONE_RULE = "[[\"@zone\", \"MAX_PER\", \"3\"]]";
    private static final String GROUP_BY_ZONE_RULE = "[[\"@zone\", \"GROUP_BY\", \"3\"]]";

    @Test
    public void testSpec() throws Exception {
        getDefaultRunner().run();
    }

    @Test
    public void rejectRackEnablement() throws Exception {
        rejectRackEnablement("MASTER_NODE_PLACEMENT");
        rejectRackEnablement("DATA_NODE_PLACEMENT");
        rejectRackEnablement("INGEST_NODE_PLACEMENT");
        rejectRackEnablement("COORDINATOR_NODE_PLACEMENT");
    }

    @Test
    public void rejectRackDisablement() throws Exception {
        rejectRackDisablement("MASTER_NODE_PLACEMENT");
        rejectRackDisablement("DATA_NODE_PLACEMENT");
        rejectRackDisablement("INGEST_NODE_PLACEMENT");
        rejectRackDisablement("COORDINATOR_NODE_PLACEMENT");
    }

    @Test
    public void allowRackChanges() throws Exception {
        allowRackChanges("MASTER_NODE_PLACEMENT");
        allowRackChanges("DATA_NODE_PLACEMENT");
        allowRackChanges("INGEST_NODE_PLACEMENT");
        allowRackChanges("COORDINATOR_NODE_PLACEMENT");
    }

    private void allowRackChanges(String placementEnvKey) throws Exception {
        validateZoneTransition(placementEnvKey, MAX_PER_ZONE_RULE, GROUP_BY_ZONE_RULE, Status.IN_PROGRESS);
    }

    private void rejectRackEnablement(String placementEnvKey) throws Exception {
        validateZoneTransition(placementEnvKey, HOST_RULE, GROUP_BY_ZONE_RULE, Status.ERROR);
    }

    private void rejectRackDisablement(String placementEnvKey) throws Exception {
        validateZoneTransition(placementEnvKey, GROUP_BY_ZONE_RULE, HOST_RULE, Status.ERROR);
    }

    private void validateZoneTransition(
            String placementEnvKey,
            String originalPlacement,
            String newPlacement,
            Status expectedStatus) throws Exception {

        ServiceTestResult result = getDefaultRunner()
                .setSchedulerEnv(placementEnvKey, originalPlacement)
                .run();

        Collection<SimulationTick> ticks = new ArrayList<>();
        ticks.add(Send.register());
        ticks.add(Expect.planStatus(Constants.DEPLOY_PLAN_NAME, expectedStatus));

        getDefaultRunner()
                .setSchedulerEnv(placementEnvKey, newPlacement)
                .setCustomValidators(new ElasticZoneValidator())
                .setState(result)
                .run(ticks);

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
