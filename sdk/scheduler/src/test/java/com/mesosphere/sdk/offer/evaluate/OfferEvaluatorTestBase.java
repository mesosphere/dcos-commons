package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.curator.CuratorStateStore;
import com.mesosphere.sdk.offer.DefaultOfferRequirementProvider;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.offer.OfferRequirementProvider;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.yaml.*;
import com.mesosphere.sdk.state.PersistentOperationRecorder;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.CuratorTestUtils;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.curator.test.TestingServer;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by gabriel on 1/20/17.
 */
public class OfferEvaluatorTestBase {
    public static final EnvironmentVariables environmentVariables =
            OfferRequirementTestUtils.getOfferRequirementProviderEnvironment();

    protected static final String ROOT_ZK_PATH = "/test-root-path";
    static TestingServer testZk;

    protected OfferRequirementProvider offerRequirementProvider;
    protected StateStore stateStore;
    protected OfferEvaluator evaluator;
    protected PersistentOperationRecorder operationRecorder;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testZk = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        CuratorTestUtils.clear(testZk);
        stateStore = new CuratorStateStore(ROOT_ZK_PATH, testZk.getConnectString());
        offerRequirementProvider = new DefaultOfferRequirementProvider(stateStore, UUID.randomUUID());
        evaluator = new OfferEvaluator(stateStore, offerRequirementProvider);
        operationRecorder = new PersistentOperationRecorder(stateStore);
    }

    protected static Label getFirstLabel(Resource resource) {
        return resource.getReservation().getLabels().getLabels(0);
    }

    protected PodInstanceRequirement getPodInstanceRequirement(Resource resource, boolean isVolume) throws Exception {
        return getPodInstanceRequirement(resource, Collections.emptyList(), Collections.emptyList(), isVolume);
    }

    protected PodInstanceRequirement getPodInstanceRequirement(Resource resource) throws Exception {
        return getPodInstanceRequirement(resource, false);
    }

    protected PodInstanceRequirement getPodInstanceRequirement(boolean isVolume, RawServiceSpec rawServiceSpec)
            throws Exception {
        return getPodInstanceRequirement(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                isVolume,
                rawServiceSpec);
    }

    protected PodInstanceRequirement getPodInstanceRequirement(boolean isVolume, String yamlFile) throws Exception {
        return getPodInstanceRequirement(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                isVolume,
                yamlFile);
    }

    protected PodInstanceRequirement getPodInstanceRequirement(
            Collection<Resource> resources,
            List<String> avoidAgents,
            List<String> collocateAgents,
            boolean isVolume,
            RawServiceSpec rawServiceSpec) throws Exception {
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

    protected PodInstanceRequirement getPodInstanceRequirement(
            Collection<Resource> resources,
            List<String> avoidAgents,
            List<String> collocateAgents,
            boolean isVolume,
            String yamlFile) throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(yamlFile).getFile());
        RawServiceSpec rawServiceSpec = YAMLServiceSpecFactory.generateRawSpecFromYAML(file);

        return getPodInstanceRequirement(resources, avoidAgents, collocateAgents, isVolume, rawServiceSpec);
    }

    protected PodInstanceRequirement getPodInstanceRequirement(
            Resource resource,
            List<String> avoidAgents,
            List<String> collocateAgents,
            boolean isVolume) throws Exception {
        return getPodInstanceRequirement(
                Arrays.asList(resource), avoidAgents, collocateAgents, isVolume, getSingleTaskServiceSpec());
    }

    protected PodInstanceRequirement getExistingPortPodInstanceRequirement(
            Resource resource, String yamlFile) throws Exception {
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(resource);
        PodInstanceRequirement podInstanceRequirement = getPodInstanceRequirement(false, yamlFile);
        String stateStoreName = TaskSpec.getInstanceName(
                podInstanceRequirement.getPodInstance(),
                podInstanceRequirement.getPodInstance().getPod().getTasks().get(0));
        TaskInfo.Builder existingTaskInfo = offerRequirement.getTaskRequirements().iterator().next()
                .getTaskInfo()
                .toBuilder()
                .setName(stateStoreName);
        existingTaskInfo.getLabelsBuilder().setLabels(
                0, existingTaskInfo.getLabels().getLabels(0).toBuilder().setValue("pod-type"));
        existingTaskInfo.getCommandBuilder()
                .getEnvironmentBuilder()
                .addVariablesBuilder()
                .setName(TestConstants.PORT_ENV_NAME)
                .setValue(Long.toString(resource.getRanges().getRange(0).getBegin()));
        offerRequirement.updateTaskRequirement(TestConstants.TASK_NAME, existingTaskInfo.build());
        stateStore.storeTasks(Arrays.asList(existingTaskInfo.build()));

        return podInstanceRequirement;
    }

    protected PodInstanceRequirement getExistingPodInstanceRequirement(
            Resource resource, boolean isVolume) throws Exception {
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(resource);
        PodInstanceRequirement podInstanceRequirement = getPodInstanceRequirement(resource, isVolume);
        TaskInfo existingTaskInfo = offerRequirement.getTaskRequirements().iterator().next()
                .getTaskInfo()
                .toBuilder()
                .setName(TaskSpec.getInstanceName(
                        podInstanceRequirement.getPodInstance(),
                        podInstanceRequirement.getPodInstance().getPod().getTasks().get(0)))
                .build();
        stateStore.storeTasks(Arrays.asList(existingTaskInfo));

        return podInstanceRequirement;
    }

    /**
     * single-task.yml
     *
     * name: "test"
     *   scheduler:
     *     principal: "test-principal"
     *   pods:
     *     pod-type:
     *       count: 1
     *       tasks:
     *         test-task:
     *           goal: RUNNING
     *           cmd: "./task-cmd"
     *           cpus: 0.1
     */
    protected RawServiceSpec getSingleTaskServiceSpec() {
        return getSimpleRawServiceSpec(
                "test-task",
                RawTask.newBuilder()
                        .goal("RUNNING")
                        .cmd("./task-cmd")
                        .cpus(0.1)
                        .build());
    }

    protected  RawServiceSpec getSimpleRawServiceSpec(String taskName, RawTask rawTask) {
        Map<String, RawTask> taskMap = new HashMap<>();
        taskMap.put(taskName, rawTask);
        return getSimpleRawServiceSpec(taskMap);
    }

    protected  RawServiceSpec getSimpleRawServiceSpec(Map<String, RawTask> tasks) {
        WriteOnceLinkedHashMap<String, RawTask> taskMap =  new WriteOnceLinkedHashMap<>();
        taskMap.putAll(tasks);

        WriteOnceLinkedHashMap<String, RawPod> pods =  new WriteOnceLinkedHashMap<>();
        pods.put(
                "pod-type",
                RawPod.newBuilder()
                        .count(1)
                        .tasks(taskMap)
                        .build());

        return RawServiceSpec.newBuilder()
                .name("test")
                .pods(pods)
                .build();
    }
}
