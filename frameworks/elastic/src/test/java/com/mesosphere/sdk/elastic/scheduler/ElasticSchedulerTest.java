package com.mesosphere.sdk.elastic.scheduler;

import org.apache.curator.test.TestingServer;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.scheduler.DefaultScheduler;
import org.apache.mesos.scheduler.plan.Plan;
import org.apache.mesos.scheduler.plan.strategy.ParallelStrategy;
import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;
import org.apache.mesos.specification.DefaultServiceSpecification;
import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.specification.TestTaskSetFactory;
import org.apache.mesos.state.StateStore;
import org.apache.mesos.state.StateStoreCache;
import org.apache.mesos.testing.CuratorTestUtils;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;

public class ElasticSchedulerTest {
    private static final String SERVICE_NAME = "test-service";
    private static final int A_COUNT = 1;
    private static final String A_NAME = "A";
    private static final double A_CPU = 1.0;
    private static final double A_MEM = 1000.0;
    private static final double A_DISK = 1500.0;
    private static final String A_CMD = "echo " + A_NAME;
    private static final int B_COUNT = 2;
    private static final String B_NAME = "B";
    private static final double B_CPU = 2.0;
    private static final double B_MEM = 2000.0;
    private static final double B_DISK = 2500.0;
    private static final String B_CMD = "echo " + B_NAME;
    private static final ServiceSpecification SERVICE_SPECIFICATION = new DefaultServiceSpecification(
            SERVICE_NAME,
            Arrays.asList(TestTaskSetFactory.getTaskSet(A_NAME, A_COUNT, A_CMD, A_CPU, A_MEM, A_DISK),
                    TestTaskSetFactory.getTaskSet(B_NAME, B_COUNT, B_CMD, B_CPU, B_MEM, B_DISK)));
    private static TestingServer testingServer;

    @Mock
    private SchedulerDriver mockSchedulerDriver;
    private ElasticScheduler elasticScheduler;
    private EnvironmentVariables environmentVariables;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testingServer = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        CuratorTestUtils.clear(testingServer);
        environmentVariables = new EnvironmentVariables();
        environmentVariables.set("EXECUTOR_URI", "");
    }

    @Test
    public void testParallelPlan() throws Exception {
        environmentVariables.set("PLAN_STRATEGY", "PARALLEL");
        StateStoreCache.resetInstanceForTests();
        elasticScheduler = new ElasticScheduler(SERVICE_SPECIFICATION, getStateStore(), getConfigStore(),
                Collections.emptyList(), 1000, 1000);
        register();
        Plan plan = elasticScheduler.getDeploymentPlanManager().getPlan();
        Assert.assertTrue(plan.getStrategy() instanceof ParallelStrategy);
        Assert.assertTrue(plan.getChildren().get(0).getStrategy() instanceof ParallelStrategy);
    }

    @Test
    public void testSerialPlan() throws Exception {
        environmentVariables.set("PLAN_STRATEGY", "SERIAL");
        StateStoreCache.resetInstanceForTests();
        elasticScheduler = new ElasticScheduler(SERVICE_SPECIFICATION, getStateStore(), getConfigStore(),
                Collections.emptyList(), 1000, 1000);
        register();
        Plan plan = elasticScheduler.getDeploymentPlanManager().getPlan();
        Assert.assertTrue(plan.getStrategy() instanceof SerialStrategy);
        Assert.assertTrue(plan.getChildren().get(0).getStrategy() instanceof SerialStrategy);
    }

    @Test
    public void testDefaultPlanIsSerial() throws Exception {
        environmentVariables.set("PLAN_STRATEGY", null);
        StateStoreCache.resetInstanceForTests();
        elasticScheduler = new ElasticScheduler(SERVICE_SPECIFICATION, getStateStore(), getConfigStore(),
                Collections.emptyList(), 1000, 1000);
        register();
        Plan plan = elasticScheduler.getDeploymentPlanManager().getPlan();
        Assert.assertTrue(plan.getStrategy() instanceof SerialStrategy);
        Assert.assertTrue(plan.getChildren().get(0).getStrategy() instanceof SerialStrategy);
    }

    private ConfigStore<ServiceSpecification> getConfigStore() throws ConfigStoreException {
        return DefaultScheduler.createConfigStore(SERVICE_SPECIFICATION, testingServer.getConnectString(),
                Collections.emptyList());
    }

    private void register() {
        elasticScheduler.registered(mockSchedulerDriver, TestConstants.FRAMEWORK_ID, TestConstants.MASTER_INFO);
    }

    private StateStore getStateStore() {
        return DefaultScheduler.createStateStore(SERVICE_SPECIFICATION, testingServer.getConnectString());
    }
}