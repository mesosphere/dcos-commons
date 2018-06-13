package com.mesosphere.sdk.specification;

import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.yaml.RawPlan;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.testutils.SchedulerConfigTestUtils;
import org.junit.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tests for {@link PlanGenerator}.
 */
public class PlanGeneratorTest {

    private static final SchedulerConfig SCHEDULER_CONFIG = SchedulerConfigTestUtils.getTestSchedulerConfig();

    private StateStore stateStore;
    private ConfigStore<ServiceSpec> configStore;

    @Test
    public void testCustomPhases() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("custom-phases.yml").getFile());
        RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(file).build();
        DefaultServiceSpec serviceSpec =
                DefaultServiceSpec.newGenerator(rawServiceSpec, SCHEDULER_CONFIG, file.getParentFile()).build();

        Persister persister = new MemPersister();
        stateStore = new StateStore(persister);
        configStore = new ConfigStore<>(DefaultServiceSpec.getConfigurationFactory(serviceSpec), persister);

        Assert.assertNotNull(serviceSpec);

        PlanGenerator generator = new PlanGenerator(configStore, stateStore, Optional.empty());
        for (Map.Entry<String, RawPlan> entry : rawServiceSpec.getPlans().entrySet()) {
            Plan plan = generator.generate(entry.getValue(), entry.getKey(), serviceSpec.getPods());
            Assert.assertNotNull(plan);
            Assert.assertEquals(8, plan.getChildren().size());

            Phase serverPhase = plan.getChildren().get(0);
            Phase oncePhase = plan.getChildren().get(1);
            Phase interleavePhase = plan.getChildren().get(2);
            Phase parallelTasksPhase = plan.getChildren().get(3);
            Phase parallelStrategyPhase = plan.getChildren().get(4);
            Phase parallelPodsPhase = plan.getChildren().get(5);
            Phase fullCustomPhase = plan.getChildren().get(6);
            Phase partialCustomPhase = plan.getChildren().get(7);

            validatePhase(
                    serverPhase,
                    Arrays.asList(
                            Arrays.asList("server"),
                            Arrays.asList("server"),
                            Arrays.asList("server")));

            validatePhase(
                    oncePhase,
                    Arrays.asList(
                            Arrays.asList("once"),
                            Arrays.asList("once"),
                            Arrays.asList("once")));

            validatePhase(
                    interleavePhase,
                    Arrays.asList(
                            Arrays.asList("once"),
                            Arrays.asList("server"),
                            Arrays.asList("once"),
                            Arrays.asList("server"),
                            Arrays.asList("once"),
                            Arrays.asList("server")));

            validatePhase(
                    parallelTasksPhase,
                    Arrays.asList(
                            Arrays.asList("once", "server"),
                            Arrays.asList("once", "server"),
                            Arrays.asList("once", "server")));

            validatePhase(
                    parallelStrategyPhase,
                    Arrays.asList(
                            Arrays.asList("once", "server"),
                            Arrays.asList("once", "server"),
                            Arrays.asList("once", "server")));

            validatePhase(
                    parallelPodsPhase,
                    Arrays.asList(
                            Arrays.asList("once"),
                            Arrays.asList("server"),
                            Arrays.asList("once"),
                            Arrays.asList("server"),
                            Arrays.asList("once"),
                            Arrays.asList("server")));

            validatePhase(
                    fullCustomPhase,
                    Arrays.asList(
                            Arrays.asList("once"),
                            Arrays.asList("server"),
                            Arrays.asList("server"),
                            Arrays.asList("once"),
                            Arrays.asList("server")));

            validatePhase(
                    partialCustomPhase,
                    Arrays.asList(
                            Arrays.asList("server"),
                            Arrays.asList("once"),
                            Arrays.asList("once"),
                            Arrays.asList("server"),
                            Arrays.asList("server"),
                            Arrays.asList("once")));
        }
    }

    private void validatePhase(Phase phase, List<List<String>> stepTasks) {
        Assert.assertEquals(phase.getChildren().size(), stepTasks.size());
        for (int i = 0; i < stepTasks.size(); i++) {
            PodInstanceRequirement podInstanceRequirement = phase.getChildren().get(i).start().get();
            List<String> tasksToLaunch = new ArrayList<>(podInstanceRequirement.getTasksToLaunch());

            for (int j = 0; j < tasksToLaunch.size(); j++) {
                Assert.assertEquals(tasksToLaunch.get(j), stepTasks.get(i).get(j));
            }
        }
    }
}
