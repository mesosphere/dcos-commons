package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.util.RLimit;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreCache;
import org.apache.curator.test.TestingServer;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory.generateRawSpecFromYAML;
import static com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory.generateServiceSpec;

public class DefaultServiceSpecTest {
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void validExhaustive() throws Exception {
        environmentVariables.set("PORT0", "8080");

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());
        RawServiceSpec rawServiceSpec = generateRawSpecFromYAML(file);
        DefaultServiceSpec serviceSpec = generateServiceSpec(rawServiceSpec);
        Assert.assertNotNull(serviceSpec);
    }

    @Test
    public void validMinimal() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-minimal.yml").getFile());
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file));
        Assert.assertNotNull(serviceSpec);
    }

    @Test
    public void validSimple() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-simple.yml").getFile());
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file));
        Assert.assertNotNull(serviceSpec);
    }

    @Test
    public void validPortResource() throws Exception {
        environmentVariables.set("PORT0", "8080");

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-multiple-ports.yml").getFile());
        RawServiceSpec rawServiceSpec = generateRawSpecFromYAML(file);
        DefaultServiceSpec serviceSpec = generateServiceSpec(rawServiceSpec);

        List<ResourceSpec> portsResources = serviceSpec.getPods().get(0).getTasks().get(0).getResourceSet()
                .getResources()
                .stream()
                .filter(r -> r.getName().equals("ports"))
                .collect(Collectors.toList());

        Assert.assertEquals(2, portsResources.size());

        Protos.Value.Ranges ports = portsResources.get(0).getValue().getRanges();
        Assert.assertEquals(1, ports.getRangeCount());
        Assert.assertEquals(8080, ports.getRange(0).getBegin(), ports.getRange(0).getEnd());

        ports = portsResources.get(1).getValue().getRanges();
        Assert.assertEquals(8088, ports.getRange(0).getBegin(), ports.getRange(0).getEnd());
    }

    @Test
    public void validReadinessCheck() throws Exception {
        validateServiceSpec("readiness-check.yml");

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("readiness-check.yml").getFile());
        RawServiceSpec rawServiceSpec = generateRawSpecFromYAML(file);
        DefaultServiceSpec serviceSpec = generateServiceSpec(rawServiceSpec);

        Optional<ReadinessCheckSpec> readinessCheckSpecOptional =
                serviceSpec.getPods().get(0).getTasks().get(0).getReadinessCheck();
        Assert.assertTrue(readinessCheckSpecOptional.isPresent());

        ReadinessCheckSpec readinessCheckSpec = readinessCheckSpecOptional.get();
        Assert.assertEquals("./readiness-check", readinessCheckSpec.getCommand());
        Assert.assertTrue(5 == readinessCheckSpec.getInterval());
        Assert.assertTrue(0 == readinessCheckSpec.getDelay());
        Assert.assertTrue(10 == readinessCheckSpec.getTimeout());
    }

    @Test
    public void invalidReplacementFailurePolicy() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-replacement-failure-policy.yml").getFile());
        try {
            generateServiceSpec(generateRawSpecFromYAML(file));
        } catch (ConstraintViolationException e) {
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            Assert.assertTrue(constraintViolations.size() > 0);
        }
    }

    @Test
    public void invalidPodName() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-pod-name.yml").getFile());
        try {
            generateServiceSpec(generateRawSpecFromYAML(file));
        } catch (JsonMappingException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                ConstraintViolationException cause = (ConstraintViolationException) e.getCause();
                Set<ConstraintViolation<?>> constraintViolations = cause.getConstraintViolations();
                Assert.assertTrue(constraintViolations.size() > 0);
            }
        }
    }

    @Test
    public void invalidPodNamePojo() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());
        DefaultServiceSpec defaultServiceSpec = generateServiceSpec(generateRawSpecFromYAML(file));
        try {
            List<PodSpec> pods = defaultServiceSpec.getPods();
            pods.add(pods.get(0));
            DefaultServiceSpec.newBuilder(defaultServiceSpec)
                    .pods(pods)
                    .build();
        } catch (ConstraintViolationException e) {
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            Assert.assertTrue(constraintViolations.size() > 0);
        }
    }

    @Test
    public void invalidTaskName() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-task-name.yml").getFile());
        try {
            generateServiceSpec(generateRawSpecFromYAML(file));
        } catch (JsonMappingException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                ConstraintViolationException cause = (ConstraintViolationException) e.getCause();
                Set<ConstraintViolation<?>> constraintViolations = cause.getConstraintViolations();
                Assert.assertTrue(constraintViolations.size() > 0);
            }
        }
    }

    @Test(expected = RLimit.InvalidRLimitException.class)
    public void invalidRLimitName() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-rlimit-name.yml").getFile());
        generateServiceSpec(generateRawSpecFromYAML(file));
    }

    @Test
    public void invalidTaskNamePojo() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());
        DefaultServiceSpec defaultServiceSpec = generateServiceSpec(generateRawSpecFromYAML(file));
        try {
            List<PodSpec> pods = defaultServiceSpec.getPods();
            PodSpec aPod = pods.get(0);
            List<TaskSpec> tasks = aPod.getTasks();
            tasks.add(tasks.get(0));
            DefaultPodSpec.newBuilder(aPod)
                    .tasks(tasks)
                    .build();
        } catch (ConstraintViolationException e) {
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            Assert.assertTrue(constraintViolations.size() > 0);
        }
    }

    @Test
    public void invalidTaskSpecNoResource() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-task-resources.yml").getFile());
        try {
            generateServiceSpec(generateRawSpecFromYAML(file));
        } catch (ConstraintViolationException e) {
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            Assert.assertTrue(constraintViolations.size() > 0);
        }
    }

    @Test
    public void invalidResourceSetName() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-resource-set-name.yml").getFile());
        try {
            generateServiceSpec(generateRawSpecFromYAML(file));
        } catch (JsonMappingException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                ConstraintViolationException cause = (ConstraintViolationException) e.getCause();
                Set<ConstraintViolation<?>> constraintViolations = cause.getConstraintViolations();
                Assert.assertTrue(constraintViolations.size() > 0);
            }
        }
    }

    @Test
    public void defaultZKConnection() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-minimal.yml").getFile());
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file));
        Assert.assertNotNull(serviceSpec);
        Assert.assertNotNull(serviceSpec.getZookeeperConnection());
        Assert.assertEquals(DefaultServiceSpec.DEFAULT_ZK_CONNECTION, serviceSpec.getZookeeperConnection());
    }

    @Test
    public void customZKConnection() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-customzk.yml").getFile());
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file));
        Assert.assertNotNull(serviceSpec);
        Assert.assertNotNull(serviceSpec.getZookeeperConnection());
        Assert.assertEquals("custom.master.mesos:2181", serviceSpec.getZookeeperConnection());
    }

    private void validateServiceSpec(String fileName) throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(fileName).getFile());
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file));

        TestingServer testingServer = new TestingServer();
        StateStoreCache.resetInstanceForTests();
        StateStore stateStore = DefaultScheduler.createStateStore(
                serviceSpec,
                testingServer.getConnectString());
        ConfigStore<ServiceSpec> configStore = DefaultScheduler.createConfigStore(
                serviceSpec,
                testingServer.getConnectString(),
                Collections.emptyList());
        DefaultScheduler.newBuilder(serviceSpec)
                .setStateStore(stateStore)
                .setConfigStore(configStore)
                .build();
    }
}
