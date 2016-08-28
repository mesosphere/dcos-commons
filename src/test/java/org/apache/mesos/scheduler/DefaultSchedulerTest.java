package org.apache.mesos.scheduler;

import org.apache.mesos.specification.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Created by gabriel on 8/28/16.
 */
public class DefaultSchedulerTest {
    private static final String SERVICE_NAME = "test-service";
    private static final int TASK_A_COUNT = 1;
    private static final String TASK_A_NAME = "A";
    private static final double TASK_A_CPU = 1.0;
    private static final double TASK_A_MEM = 1000.0;
    private static final String TASK_A_CMD = "echo " + TASK_A_NAME;

    private static final int TASK_B_COUNT = 2;
    private static final String TASK_B_NAME = "B";
    private static final double TASK_B_CPU = 2.0;
    private static final double TASK_B_MEM = 2000.0;
    private static final String TASK_B_CMD = "echo " + TASK_B_NAME;

    private static ServiceSpecification serviceSpecification;
    private DefaultScheduler defaultScheduler;

    @BeforeClass
    public static void beforeAll() {
        serviceSpecification = new ServiceSpecification() {
            @Override
            public String getName() {
                return SERVICE_NAME;
            }

            @Override
            public List<TaskTypeSpecification> getTaskSpecifications() {
                return Arrays.asList(
                        TestTaskSpecificationFactory.getTaskSpecification(
                                TASK_A_NAME,
                                TASK_A_COUNT,
                                TASK_A_CMD,
                                TASK_A_CPU,
                                TASK_A_MEM),
                        TestTaskSpecificationFactory.getTaskSpecification(
                                TASK_B_NAME,
                                TASK_B_COUNT,
                                TASK_B_CMD,
                                TASK_B_CPU,
                                TASK_B_MEM));
            }
        };
    }

    @Before
    public void beforeEach() {
        defaultScheduler = new DefaultScheduler(serviceSpecification);
    }

    @Test
    public void testConstruction() {
        Assert.assertNotNull(defaultScheduler);
    }


}
