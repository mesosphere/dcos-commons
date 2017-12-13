package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.testutils.SchedulerConfigTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import com.mesosphere.sdk.testutils.TestPodFactory;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * This class tests the {@link DefaultStepFactory} class.
 */
public class DefaultStepFactoryTest {

    private static final SchedulerConfig SCHEDULER_CONFIG = SchedulerConfigTestUtils.getTestSchedulerConfig();

    private StepFactory stepFactory;
    private ConfigStore<ServiceSpec> configStore;
    private StateStore stateStore;

    @Test
    public void testGetStepFailsOnMultipleResourceSetReferences() throws Exception {

        PodInstance podInstance = getPodInstanceWithSameResourceSets();
        List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                .map(taskSpec -> taskSpec.getName())
                .collect(Collectors.toList());
        Step step = stepFactory.getStep(podInstance, tasksToLaunch);
        Assert.assertEquals(Status.ERROR, step.getStatus());
    }

    @Test
    public void testGetStepFailsOnDuplicateDNSNames() throws Exception {

        PodInstance podInstance = getPodInstanceWithSameDnsPrefixes();
        List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                .map(taskSpec -> taskSpec.getName())
                .collect(Collectors.toList());
        Step step = stepFactory.getStep(podInstance, tasksToLaunch);
        Assert.assertEquals(Status.ERROR, step.getStatus());
    }

    private PodInstance getPodInstanceWithSameResourceSets() throws Exception {
        TaskSpec taskSpec0 =
                TestPodFactory.getTaskSpec(TestConstants.TASK_NAME + 0, TestConstants.RESOURCE_SET_ID);
        TaskSpec taskSpec1 =
                TestPodFactory.getTaskSpec(TestConstants.TASK_NAME + 1, TestConstants.RESOURCE_SET_ID);
        PodSpec podSpec = DefaultPodSpec.newBuilder(SCHEDULER_CONFIG.getExecutorURI())
                .type(TestConstants.POD_TYPE)
                .count(1)
                .tasks(Arrays.asList(taskSpec0, taskSpec1))
                .build();

        ServiceSpec serviceSpec =
                DefaultServiceSpec.newBuilder()
                        .name(TestConstants.SERVICE_NAME)
                        .role(TestConstants.ROLE)
                        .principal(TestConstants.PRINCIPAL)
                        .zookeeperConnection("foo.bar.com")
                        .pods(Arrays.asList(podSpec))
                        .build();

        Persister persister = new MemPersister();
        stateStore = new StateStore(persister);
        configStore = new ConfigStore<>(DefaultServiceSpec.getConfigurationFactory(serviceSpec), persister);

        UUID configId = configStore.store(serviceSpec);
        configStore.setTargetConfig(configId);

        stepFactory = new DefaultStepFactory(configStore, stateStore);

        return new DefaultPodInstance(podSpec, 0);
    }

    private PodInstance getPodInstanceWithSameDnsPrefixes() throws Exception {
        TaskSpec taskSpec0 =
                TestPodFactory.getTaskSpec(
                        TestConstants.TASK_NAME + 0, TestConstants.RESOURCE_SET_ID + 0, TestConstants.TASK_DNS_PREFIX);
        TaskSpec taskSpec1 =
                TestPodFactory.getTaskSpec(
                        TestConstants.TASK_NAME + 1, TestConstants.RESOURCE_SET_ID + 1, TestConstants.TASK_DNS_PREFIX);
        PodSpec podSpec = DefaultPodSpec.newBuilder(SCHEDULER_CONFIG.getExecutorURI())
                .type(TestConstants.POD_TYPE)
                .count(1)
                .tasks(Arrays.asList(taskSpec0, taskSpec1))
                .build();

        ServiceSpec serviceSpec =
                DefaultServiceSpec.newBuilder()
                        .name(TestConstants.SERVICE_NAME)
                        .role(TestConstants.ROLE)
                        .principal(TestConstants.PRINCIPAL)
                        .zookeeperConnection("foo.bar.com")
                        .pods(Arrays.asList(podSpec))
                        .build();

        Persister persister = new MemPersister();
        stateStore = new StateStore(persister);
        configStore = new ConfigStore<>(DefaultServiceSpec.getConfigurationFactory(serviceSpec), persister);

        UUID configId = configStore.store(serviceSpec);
        configStore.setTargetConfig(configId);

        stepFactory = new DefaultStepFactory(configStore, stateStore);

        return new DefaultPodInstance(podSpec, 0);
    }
}
