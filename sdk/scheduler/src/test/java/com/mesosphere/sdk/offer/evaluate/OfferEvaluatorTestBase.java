package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.curator.CuratorStateStore;
import com.mesosphere.sdk.offer.DefaultOfferRequirementProvider;
import com.mesosphere.sdk.offer.OfferRequirementProvider;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.state.PersistentOperationRecorder;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.CuratorTestUtils;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;

import org.apache.curator.test.TestingServer;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A BaseTest for use in writing offer evaluation tests.
 */
public class OfferEvaluatorTestBase {
    public static final EnvironmentVariables environmentVariables =
            OfferRequirementTestUtils.getOfferRequirementProviderEnvironment();

    protected static final String ROOT_ZK_PATH = "/test-root-path";
    static TestingServer testZk;

    protected OfferRequirementProvider offerRequirementProvider;
    protected StateStore stateStore;
    protected OfferEvaluator evaluator;
     PersistentOperationRecorder operationRecorder;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testZk = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        CuratorTestUtils.clear(testZk);
        stateStore = new CuratorStateStore(ROOT_ZK_PATH, testZk.getConnectString());
        offerRequirementProvider =
                new DefaultOfferRequirementProvider(stateStore, TestConstants.SERVICE_NAME, UUID.randomUUID());
        evaluator = new OfferEvaluator(stateStore, offerRequirementProvider);
        operationRecorder = new PersistentOperationRecorder(stateStore);
    }

    protected PodInstanceRequirement getPodInstanceRequirement(Resource resource) throws Exception {
        return getPodInstanceRequirement(resource, false, "single-task.yml");
    }

    protected PodInstanceRequirement getPodInstanceRequirement(Resource resource, String yamlFile) throws Exception {
        return getPodInstanceRequirement(resource, false, yamlFile);
    }

    protected PodInstanceRequirement getPodInstanceRequirement(
            Resource resource,
            List<String> avoidAgents,
            List<String> collocateAgents,
            boolean isVolume,
            String yamlFile) throws Exception {
        return getPodInstanceRequirement(
                Arrays.asList(resource), avoidAgents, collocateAgents, isVolume, yamlFile);
    }

    protected PodInstanceRequirement getPodInstanceRequirement(boolean isVolume, String yamlFile) throws Exception {
        return getPodInstanceRequirement(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), isVolume, yamlFile);
    }


    protected PodInstanceRequirement getPodInstanceRequirement(
            Collection<Resource> resources,
            List<String> avoidAgents,
            List<String> collocateAgents,
            boolean isVolume,
            String yamlFile) throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(yamlFile).getFile());
        RawServiceSpec rawServiceSpec = YAMLServiceSpecFactory.generateRawSpecFromYAML(file);
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec);

        PodSpec podSpec = serviceSpec.getPods().get(0);
        if (!resources.isEmpty()) {
            podSpec = isVolume ?
                    OfferRequirementTestUtils.withVolume(
                            serviceSpec.getPods().get(0), resources.iterator().next(), serviceSpec.getPrincipal()) :
                    OfferRequirementTestUtils.withResources(
                            serviceSpec.getPods().get(0),
                            resources,
                            serviceSpec.getPrincipal(),
                            avoidAgents,
                            collocateAgents);
        }

        return PodInstanceRequirement.create(
                new DefaultPodInstance(podSpec, 0),
                podSpec.getTasks().stream().map(t -> t.getName()).collect(Collectors.toList()));
    }

    protected PodInstanceRequirement getPodInstanceRequirement(Resource resource,
                                                               boolean isVolume,
                                                               String yamlFile) throws Exception {
        return getPodInstanceRequirement(resource, Collections.emptyList(),
                Collections.emptyList(), isVolume, yamlFile);
    }

    protected static Offer getOffer(Resource resource) {
        return OfferTestUtils.getOffer(Arrays.asList(
                ResourceUtils.getUnreservedScalar("cpus", 1.0),
                ResourceUtils.getUnreservedScalar("mem", 512),
                resource));
    }

    protected static Label getFirstLabel(Resource resource) {
        return resource.getReservation().getLabels().getLabels(0);
    }
}
