package com.mesosphere.sdk.state;

import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluatorTestBase;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelWriter;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

/**
 * This class tests the {@link PersistentLaunchRecorder}.
 */
public class PersistentLaunchRecorderTest extends OfferEvaluatorTestBase {
    private static ServiceSpec serviceSpec;
    private PersistentLaunchRecorder persistentLaunchRecorder;
    private Protos.TaskInfo baseTaskInfo = Protos.TaskInfo.newBuilder()
            .setName(TestConstants.TASK_NAME)
            .setTaskId(TestConstants.TASK_ID)
            .setSlaveId(TestConstants.AGENT_ID)
            .build();

    @BeforeClass
    public static void beforeAll() throws Exception {
        ClassLoader classLoader = PersistentLaunchRecorderTest.class.getClassLoader();
        File file = new File(classLoader.getResource("shared-resource-set.yml").getFile());
        RawServiceSpec rawServiceSpec = YAMLServiceSpecFactory.generateRawSpecFromYAML(file);
        serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec, flags);
    }

    @Before
    public void beforeEach() throws Exception {
        super.beforeEach();
        persistentLaunchRecorder = new PersistentLaunchRecorder(stateStore, serviceSpec);
    }

    @Test(expected=TaskException.class)
    public void testUpdateResourcesMissingTypeLabel() throws TaskException {
        persistentLaunchRecorder.updateResources(baseTaskInfo);
    }

    @Test
    public void testUpdateResourcesNoHarmForAcceptableTaskInfo() throws TaskException {
        Protos.TaskInfo.Builder builder = baseTaskInfo.toBuilder();
        builder.setLabels(new SchedulerLabelWriter(builder)
                .setType(TestConstants.TASK_TYPE)
                .toProto());
        persistentLaunchRecorder.updateResources(builder.build());
    }

    @Test
    public void testUpdateResourcesNoSharedTasksInStateStore() throws TaskException {
        Protos.Resource targetResource = Protos.Resource.newBuilder()
                .setName("cpus")
                .setType(Protos.Value.Type.SCALAR)
                .setScalar(Protos.Value.Scalar.newBuilder().setValue(1.0))
                .build();

        String taskName = "pod-0-init";
        Protos.TaskInfo taskInfo = baseTaskInfo.toBuilder()
                .setLabels(new SchedulerLabelWriter(baseTaskInfo)
                        .setType("pod")
                        .setIndex(0)
                        .toProto())
                .setName(taskName)
                .addAllResources(Arrays.asList(targetResource))
                .build();

        stateStore.storeTasks(Arrays.asList(taskInfo));
        Assert.assertEquals(1, stateStore.fetchTaskNames().size());

        persistentLaunchRecorder.updateResources(taskInfo);
        Assert.assertEquals(1, stateStore.fetchTaskNames().size());
        Assert.assertEquals(targetResource, stateStore.fetchTask(taskName).get().getResources(0));
    }

    @Test
    public void testUpdateResourcesOneSharedTaskInStateStore() throws TaskException {
        Protos.Resource targetResource = Protos.Resource.newBuilder()
                .setName("cpus")
                .setType(Protos.Value.Type.SCALAR)
                .setScalar(Protos.Value.Scalar.newBuilder().setValue(1.0))
                .build();

        Protos.Resource previousResource = Protos.Resource.newBuilder()
                .setName("cpus")
                .setType(Protos.Value.Type.SCALAR)
                .setScalar(Protos.Value.Scalar.newBuilder().setValue(2.0))
                .build();

        String initTaskName = "pod-0-init";
        Protos.TaskInfo initTaskInfo = baseTaskInfo.toBuilder()
                .setLabels(new SchedulerLabelWriter(baseTaskInfo)
                        .setType("pod")
                        .setIndex(0)
                        .toProto())
                .setName(initTaskName)
                .addAllResources(Arrays.asList(previousResource))
                .build();

        String serverTaskName = "pod-0-server";
        Protos.TaskInfo serverTaskInfo = baseTaskInfo.toBuilder()
                .setLabels(new SchedulerLabelWriter(baseTaskInfo)
                        .setType("pod")
                        .setIndex(0)
                        .toProto())
                .setName(serverTaskName)
                .addAllResources(Arrays.asList(targetResource))
                .build();

        stateStore.storeTasks(Arrays.asList(initTaskInfo, serverTaskInfo));
        Assert.assertEquals(2, stateStore.fetchTaskNames().size());
        serverTaskInfo = stateStore.fetchTask(serverTaskName).get();
        Assert.assertFalse(
                stateStore.fetchTask(initTaskName).get().getResources(0).equals(
                stateStore.fetchTask(serverTaskName).get().getResources(0)));

        persistentLaunchRecorder.updateResources(serverTaskInfo);
        Assert.assertEquals(2, stateStore.fetchTaskNames().size());
        Assert.assertEquals(targetResource, stateStore.fetchTask(initTaskName).get().getResources(0));
        Assert.assertEquals(targetResource, stateStore.fetchTask(serverTaskName).get().getResources(0));
    }
}
