package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.PortRequirement;
import com.mesosphere.sdk.offer.ResourceRequirement;
import com.mesosphere.sdk.offer.evaluate.PortsRequirement;
import org.apache.mesos.Protos;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.util.RLimit;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreCache;
import org.apache.curator.test.TestingServer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.Collections;
import java.util.stream.Collectors;

import static org.mockito.Mockito.when;
import static com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory.*;

public class DefaultServiceSpecTest {
    @Rule public final EnvironmentVariables environmentVariables = OfferRequirementTestUtils.getApiPortEnvironment();
    @Mock private FileReader mockFileReader;
    @Mock private ConfigStore<ServiceSpec> mockConfigStore;
    @Mock private StateStore mockStateStore;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    @SuppressFBWarnings(value="RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    public void validExhaustive() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();

        when(mockFileReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(mockFileReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(mockFileReader.read("config-three.conf.mustache")).thenReturn("hi");

        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file), mockFileReader);
        Assert.assertNotNull(serviceSpec);
    }

    @Test
    public void validMinimal() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-minimal.yml").getFile());
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file));
        Assert.assertNotNull(serviceSpec);
    }

    @Test
    public void validSimple() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-simple.yml").getFile());
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file));
        Assert.assertNotNull(serviceSpec);
    }

    @Test
    public void validPortResourceEnvKey() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-envkey-ports.yml").getFile());
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file));

        List<ResourceSpec> portsResources = serviceSpec.getPods().get(0).getTasks().get(0).getResourceSet()
                .getResources()
                .stream()
                .filter(r -> r.getName().equals("ports"))
                .collect(Collectors.toList());

        Assert.assertEquals(1, portsResources.size());

       PortsRequirement portsRequirement = (PortsRequirement) portsResources.get(0).getResourceRequirement(null);
       List<ResourceRequirement> portReqList = (List<ResourceRequirement>) portsRequirement.getPortRequirements();

       Assert.assertEquals(3, portReqList.size());

       Assert.assertEquals("key1", ((PortRequirement) portReqList.get(0)).getEnvKey());
       Assert.assertEquals(Constants.PORT_NAME_LABEL_PREFIX + "name2", ((PortRequirement) portReqList.get(1)).getEnvKey());
       Assert.assertEquals(Constants.PORT_NAME_LABEL_PREFIX  + "name3", ((PortRequirement) portReqList.get(2)).getEnvKey());

    }

    @Test
    public void validPortResource() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-multiple-ports.yml").getFile());
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file));

        List<ResourceSpec> portsResources = serviceSpec.getPods().get(0).getTasks().get(0).getResourceSet()
                .getResources()
                .stream()
                .filter(r -> r.getName().equals("ports"))
                .collect(Collectors.toList());

        Assert.assertEquals(1, portsResources.size());

        Protos.Value.Ranges ports = portsResources.get(0).getValue().getRanges();
        Assert.assertEquals(2, ports.getRangeCount());
        Assert.assertEquals(8080, ports.getRange(0).getBegin(), ports.getRange(0).getEnd());
        Assert.assertEquals(8088, ports.getRange(1).getBegin(), ports.getRange(1).getEnd());
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
    public void invalidDuplicatePodName() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-pod-name.yml").getFile());
        try {
            generateServiceSpec(generateRawSpecFromYAML(file));
            Assert.fail("Expected exception");
        } catch (JsonMappingException e) {
            Assert.assertTrue(e.getCause().toString(), e.getCause() instanceof JsonParseException);
            JsonParseException cause = (JsonParseException) e.getCause();
            Assert.assertTrue(cause.getMessage(), cause.getMessage().contains("Duplicate field 'meta-data'"));
        }
    }

    @Test
    public void invalidDuplicateCount() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-duplicate-count.yml").getFile());
        try {
            generateServiceSpec(generateRawSpecFromYAML(file));
            Assert.fail("Expected exception");
        } catch (JsonMappingException e) {
            Assert.assertTrue(e.getCause().toString(), e.getCause() instanceof JsonParseException);
            JsonParseException cause = (JsonParseException) e.getCause();
            Assert.assertTrue(cause.getMessage().contains("Duplicate field 'count'"));
        }
    }

    @Test
    public void invalidVolumeAndVolumes() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-volume-and-volumes.yml").getFile());
        try {
            generateServiceSpec(generateRawSpecFromYAML(file));
            Assert.fail("Expected exception");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("Both 'volume' and 'volumes'"));
        }
    }

    @Test(expected = FileNotFoundException.class)
    public void invalidConfigFile() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-config-file.yml").getFile());
        generateServiceSpec(generateRawSpecFromYAML(file));
    }

    @Test(expected = IllegalStateException.class)
    public void invalidPlanSteps() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-plan-steps.yml").getFile());
        RawServiceSpec rawSpec = generateRawSpecFromYAML(file);
        DefaultScheduler.newBuilder(generateServiceSpec(rawSpec))
            .setConfigStore(mockConfigStore)
            .setStateStore(mockStateStore)
            .setPlansFrom(rawSpec)
            .build();
    }

    @Test
    public void invalidPodNamePojo() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());

        when(mockFileReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(mockFileReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(mockFileReader.read("config-three.conf.mustache")).thenReturn("hi");

        DefaultServiceSpec defaultServiceSpec = generateServiceSpec(generateRawSpecFromYAML(file), mockFileReader);
        try {
            List<PodSpec> pods = defaultServiceSpec.getPods();
            pods.add(pods.get(0));
            DefaultServiceSpec.newBuilder(defaultServiceSpec)
                    .pods(pods)
                    .build();
            Assert.fail("Expected exception");
        } catch (ConstraintViolationException e) {
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            Assert.assertTrue(constraintViolations.size() > 0);
        }
    }

    @Test
    public void invalidTaskName() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-task-name.yml").getFile());
        try {
            generateServiceSpec(generateRawSpecFromYAML(file));
            Assert.fail("Expected exception");
        } catch (JsonMappingException e) {
            Assert.assertTrue(e.getCause().toString(), e.getCause() instanceof JsonParseException);
            JsonParseException cause = (JsonParseException) e.getCause();
            Assert.assertTrue(cause.getMessage(), cause.getMessage().contains("Duplicate field 'meta-data-task'"));
        }
    }

    @Test(expected = RLimit.InvalidRLimitException.class)
    public void invalidRLimitName() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-rlimit-name.yml").getFile());
        generateServiceSpec(generateRawSpecFromYAML(file));
    }

    @Test
    public void invalidTaskNamePojo() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());

        when(mockFileReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(mockFileReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(mockFileReader.read("config-three.conf.mustache")).thenReturn("hi");

        DefaultServiceSpec defaultServiceSpec = generateServiceSpec(generateRawSpecFromYAML(file), mockFileReader);
        try {
            List<PodSpec> pods = defaultServiceSpec.getPods();
            PodSpec aPod = pods.get(0);
            List<TaskSpec> tasks = aPod.getTasks();
            tasks.add(tasks.get(0));
            DefaultPodSpec.newBuilder(aPod)
                    .tasks(tasks)
                    .build();
            Assert.fail("Expected exception");
        } catch (ConstraintViolationException e) {
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            Assert.assertTrue(constraintViolations.size() > 0);
        }
    }

    @Test
    public void invalidTaskSpecNoResource() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-task-resources.yml").getFile());
        try {
            generateServiceSpec(generateRawSpecFromYAML(file));
            Assert.fail("Expected exception");
        } catch (ConstraintViolationException e) {
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            Assert.assertTrue(constraintViolations.size() > 0);
        }
    }

    @Test
    public void invalidDuplicateResourceSetName() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-resource-set-name.yml").getFile());
        try {
            generateServiceSpec(generateRawSpecFromYAML(file));
            Assert.fail("Expected exception");
        } catch (JsonMappingException e) {
            Assert.assertTrue(e.getCause().toString(), e.getCause() instanceof JsonParseException);
            JsonParseException cause = (JsonParseException) e.getCause();
            Assert.assertTrue(cause.getMessage(),
                    cause.getMessage().contains("Duplicate field 'data-store-resources'"));
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
        testingServer.close();
    }
}
