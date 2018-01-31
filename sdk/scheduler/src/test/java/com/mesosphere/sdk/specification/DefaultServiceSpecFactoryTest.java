package com.mesosphere.sdk.specification;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.testutils.SchedulerConfigTestUtils;

public class DefaultServiceSpecFactoryTest {

    private static final SchedulerConfig SCHEDULER_CONFIG = SchedulerConfigTestUtils.getTestSchedulerConfig();

    @Test
    public void testGoalStateDeserializesOldValues() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-minimal.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();

        ObjectMapper objectMapper = SerializationUtils.registerDefaultModules(new ObjectMapper());

        SimpleModule module = new SimpleModule();
        module.addDeserializer(GoalState.class, new DefaultServiceSpecFactory.GoalStateDeserializer(serviceSpec));
        objectMapper.registerModule(module);

        Assert.assertEquals(
                GoalState.ONCE, SerializationUtils.fromString("\"ONCE\"", GoalState.class, objectMapper));
        Assert.assertEquals(
                GoalState.ONCE, SerializationUtils.fromString("\"FINISHED\"", GoalState.class, objectMapper));
    }

    @Test
    public void testGoalStateDeserializesNewValues() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-finished.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();

        ObjectMapper objectMapper = SerializationUtils.registerDefaultModules(new ObjectMapper());

        SimpleModule module = new SimpleModule();
        module.addDeserializer(GoalState.class, new DefaultServiceSpecFactory.GoalStateDeserializer(serviceSpec));
        objectMapper.registerModule(module);

        Assert.assertEquals(
                GoalState.FINISHED, SerializationUtils.fromString("\"ONCE\"", GoalState.class, objectMapper));
        Assert.assertEquals(
                GoalState.FINISHED, SerializationUtils.fromString("\"FINISHED\"", GoalState.class, objectMapper));
    }
}
