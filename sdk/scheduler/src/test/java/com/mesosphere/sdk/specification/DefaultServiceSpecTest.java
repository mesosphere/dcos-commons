package com.mesosphere.sdk.specification;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.collect.Iterables;
import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.dcos.DcosConstants;
import org.apache.mesos.Protos;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.util.RLimit;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLToInternalMappers;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;

import java.net.URI;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class DefaultServiceSpecTest {
    private static final SchedulerFlags flags = OfferRequirementTestUtils.getTestSchedulerFlags();
    @Mock private YAMLToInternalMappers.FileReader mockFileReader;
    @Mock private ConfigStore<ServiceSpec> mockConfigStore;
    @Mock private StateStore mockStateStore;
    @Mock private Capabilities capabilities;

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
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags)
                .setFileReader(mockFileReader)
                .build();
        Assert.assertNotNull(serviceSpec);
    }

    @Test
    public void validMinimal() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-minimal.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
        Assert.assertNotNull(serviceSpec);
    }

    @Test
    public void validSimple() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-simple.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
        Assert.assertNotNull(serviceSpec);
        Assert.assertTrue(DefaultService.serviceSpecRequestsGpuResources(serviceSpec) ==
                DcosConstants.DEFAULT_GPU_POLICY);
        validateServiceSpec("valid-simple.yml", DcosConstants.DEFAULT_GPU_POLICY);
    }

    @Test
    public void validGpuResource() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-gpu-resource.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
        Assert.assertNotNull(serviceSpec);
        Boolean obs = DefaultService.serviceSpecRequestsGpuResources(serviceSpec);
        Assert.assertTrue(String.format("Expected serviceSpec to request support GPUs got %s", obs), obs);
        validateServiceSpec("valid-gpu-resource.yml", true);
    }

    @Test
    public void validGpuResourceSet() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-gpu-resourceset.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
        Assert.assertNotNull(serviceSpec);
        Boolean obs = DefaultService.serviceSpecRequestsGpuResources(serviceSpec);
        Assert.assertTrue(String.format("Expected serviceSpec to request support GPUs got %s", obs), obs);
    }

    @Test
    public void validPortResourceEnvKey() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-envkey-ports.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();

        List<ResourceSpec> portsResources = serviceSpec.getPods().get(0).getTasks().get(0).getResourceSet()
                .getResources()
                .stream()
                .filter(r -> r.getName().equals("ports"))
                .collect(Collectors.toList());

        Assert.assertEquals(3, portsResources.size());

        PortSpec portSpec = (PortSpec) portsResources.get(0);
        Assert.assertEquals("name1", portSpec.getPortName());
        Assert.assertEquals(8080, portSpec.getPort());
        Assert.assertEquals("key1", portSpec.getEnvKey());

        portSpec = (PortSpec) portsResources.get(1);
        Assert.assertEquals("name2", portSpec.getPortName());
        Assert.assertEquals(8088, portSpec.getPort());
        Assert.assertEquals(null, portSpec.getEnvKey());

        portSpec = (PortSpec) portsResources.get(2);
        Assert.assertEquals("name3", portSpec.getPortName());
        Assert.assertEquals(8089, portSpec.getPort());
        Assert.assertEquals(null, portSpec.getEnvKey());
    }

    @Test
    public void validPortResource() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-multiple-ports.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();

        List<ResourceSpec> portsResources = serviceSpec.getPods().get(0).getTasks().get(0).getResourceSet()
                .getResources()
                .stream()
                .filter(r -> r.getName().equals("ports"))
                .collect(Collectors.toList());

        Assert.assertEquals(2, portsResources.size());

        Protos.Value.Ranges http = portsResources.get(0).getValue().getRanges();
        Protos.Value.Ranges another = portsResources.get(1).getValue().getRanges();
        Assert.assertEquals(1, http.getRangeCount());
        Assert.assertEquals(1, another.getRangeCount());
        Assert.assertEquals(8080, http.getRange(0).getBegin(), http.getRange(0).getEnd());
        Assert.assertEquals(8088, another.getRange(0).getBegin(), another.getRange(0).getEnd());
    }

    @Test
    public void invalidDuplicatePorts() throws Exception {
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            File file = new File(classLoader.getResource("invalid-duplicate-ports.yml").getFile());
            DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
            Assert.fail("expected exception");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("Task has multiple ports with value 8080"));
        }
    }

    @Test
    public void invalidDuplicatePortNames() throws Exception {
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            File file = new File(classLoader.getResource("invalid-duplicate-port-names.yml").getFile());
            DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
            Assert.fail("expected exception");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains(
                    "Service has duplicate advertised ports across tasks: [across-pods, across-tasks, in-resource-set]"));
        }
    }

    @Test
    public void validReadinessCheck() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("readiness-check.yml").getFile());
        RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(file).build();
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(rawServiceSpec, flags).build();

        Assert.assertNotNull(serviceSpec);

        Optional<ReadinessCheckSpec> readinessCheckSpecOptional =
                serviceSpec.getPods().get(0).getTasks().get(0).getReadinessCheck();
        Assert.assertTrue(readinessCheckSpecOptional.isPresent());

        ReadinessCheckSpec readinessCheckSpec = readinessCheckSpecOptional.get();
        Assert.assertEquals("./readiness-check", readinessCheckSpec.getCommand());
        Assert.assertTrue(5 == readinessCheckSpec.getInterval());
        Assert.assertTrue(0 == readinessCheckSpec.getDelay());
        Assert.assertTrue(10 == readinessCheckSpec.getTimeout());
        validateServiceSpec("readiness-check.yml", DcosConstants.DEFAULT_GPU_POLICY);
    }

    @Test
    public void validBridgeNetworkWithPortForwarding() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-automatic-cni-port-forwarding.yml").getFile());
        // load the raw service spec and check that it parsed correctly
        RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(file).build();
        Assert.assertNotNull(rawServiceSpec);
        Assert.assertEquals(rawServiceSpec
                .getPods().get("pod-type")
                .getNetworks().get("mesos-bridge")
                .numberOfPortMappings(), 0);
        Assert.assertTrue(rawServiceSpec
                .getPods().get("meta-data-with-port-mapping")
                .getNetworks().get("mesos-bridge")
                .numberOfPortMappings() == 2);

        // Check that the raw service spec was correctly translated into the ServiceSpec
        ServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(rawServiceSpec, flags).build();
        Assert.assertNotNull(serviceSpec);
        Assert.assertTrue(serviceSpec.getPods().size() == 3);
        // check the first pod
        PodSpec podSpec = serviceSpec.getPods().get(0);
        Assert.assertTrue(podSpec.getNetworks().size() == 1);
        NetworkSpec networkSpec = Iterables.get(podSpec.getNetworks(), 0);
        Assert.assertTrue(networkSpec.getPortMappings().size() == 1);
        Assert.assertTrue(networkSpec.getPortMappings().get(8080) == 8080);
        // check the second pod
        podSpec = serviceSpec.getPods().get(1);
        Assert.assertTrue(podSpec.getNetworks().size() == 1);
        networkSpec = Iterables.get(podSpec.getNetworks(), 0);
        Assert.assertTrue(networkSpec.getPortMappings().size() == 2);
        Assert.assertTrue(networkSpec.getPortMappings().get(8080) == 8080);
        Assert.assertTrue(networkSpec.getPortMappings().get(8081) == 8081);
        // check the third
        podSpec = serviceSpec.getPods().get(2);
        Assert.assertTrue(podSpec.getNetworks().size() == 1);
        networkSpec = Iterables.get(podSpec.getNetworks(), 0);
        Assert.assertTrue(String.format("%s", networkSpec.getPortMappings()),
                networkSpec.getPortMappings().size() == 2);
        Assert.assertTrue(networkSpec.getPortMappings().get(4040)== 8080);
        Assert.assertTrue(networkSpec.getPortMappings().get(4041) == 8081);
        validateServiceSpec("valid-automatic-cni-port-forwarding.yml", DcosConstants.DEFAULT_GPU_POLICY);
    }

    @Test
    public void invalidDuplicatePodName() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-pod-name.yml").getFile());
        try {
            DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
            Assert.fail("Expected exception");
        } catch (JsonMappingException e) {
            Assert.assertTrue(e.getCause().toString(), e.getCause() instanceof JsonParseException);
            JsonParseException cause = (JsonParseException) e.getCause();
            Assert.assertTrue(cause.getMessage(), cause.getMessage().contains("Duplicate field 'meta-data'"));
        }
    }

    @Test
    public void invalidDuplicateDnsName() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-task-dns.yml").getFile());
        try {
            DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
            Assert.fail("Expected exception");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("Tasks in different pods cannot share DNS names"));
        }
    }

    @Test
    public void invalidDuplicateCount() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-duplicate-count.yml").getFile());
        try {
            DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
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
            DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
            Assert.fail("Expected exception");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("Both 'volume' and 'volumes'"));
        }
    }

    @Test(expected = FileNotFoundException.class)
    public void invalidConfigFile() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-config-file.yml").getFile());
        DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
    }

    @Test(expected = IllegalStateException.class)
    public void invalidPlanSteps() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-plan-steps.yml").getFile());
        RawServiceSpec rawSpec = RawServiceSpec.newBuilder(file).build();
        DefaultScheduler.newBuilder(DefaultServiceSpec.newGenerator(rawSpec, flags).build(), flags, new MemPersister())
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

        DefaultServiceSpec defaultServiceSpec =
                DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags)
                        .setFileReader(mockFileReader)
                        .build();
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
            DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
            Assert.fail("Expected exception");
        } catch (JsonMappingException e) {
            Assert.assertTrue(e.getCause().toString(), e.getCause() instanceof JsonParseException);
            JsonParseException cause = (JsonParseException) e.getCause();
            Assert.assertTrue(cause.getMessage(), cause.getMessage().contains("Duplicate field 'meta-data-task'"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void cantDefineContainerSettingsBothPlaces() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-duplicate-container-definition.yml").getFile());
        DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
    }

    @Test
    public void validImage() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-image.yml").getFile());
        DefaultServiceSpec defaultServiceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
        Assert.assertEquals("group/image", defaultServiceSpec.getPods().get(0).getImage().get());
    }

    @Test
    public void validImageLegacy() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-image-legacy.yml").getFile());
        DefaultServiceSpec defaultServiceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
        Assert.assertEquals("group/image", defaultServiceSpec.getPods().get(0).getImage().get());
    }

    @Test
    public void validNetworks() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-network.yml").getFile());
        DefaultServiceSpec defaultServiceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
        PodSpec podSpec = defaultServiceSpec.getPods().get(0);
        Assert.assertEquals("dcos", Iterables.get(podSpec.getNetworks(), 0)
                .getName());
        List<ResourceSpec> portsResources = defaultServiceSpec.getPods().get(0).getTasks().get(0).getResourceSet()
                .getResources()
                .stream()
                .filter(r -> r.getName().equals("ports"))
                .collect(Collectors.toList());
        Assert.assertEquals(2, portsResources.size());
        Assert.assertTrue("", Iterables.get(podSpec.getNetworks(), 0).getLabels().containsKey("key1"));
        Assert.assertTrue("", Iterables.get(podSpec.getNetworks(), 0).getLabels()
                .get("key1").equals("val1"));
        Assert.assertTrue("", Iterables.get(podSpec.getNetworks(), 0).getLabels().containsKey("key2"));
        Assert.assertTrue("", Iterables.get(podSpec.getNetworks(), 0).getLabels()
                .get("key2").equals("val2"));
    }

    @Test
    public void validPortMappingNetworkRespectsPortResources() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-automatic-cni-port-forwarding.yml").getFile());
        DefaultServiceSpec defaultServiceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
        Assert.assertEquals("mesos-bridge", Iterables.get(defaultServiceSpec.getPods().get(0).getNetworks(), 0)
                .getName());
        // check that the port resources are ignored
        for (PodSpec podSpec : defaultServiceSpec.getPods()) {
            for (TaskSpec taskSpec : podSpec.getTasks()) {
                List<ResourceSpec> portsResources = taskSpec.getResourceSet()
                        .getResources()
                        .stream()
                        .filter(r -> r.getName().equals("ports"))
                        .collect(Collectors.toList());

                int portCount = 1;
                if (podSpec.getType().equals("meta-data-with-port-mapping")) {
                    portCount = 2;
                }

                Assert.assertEquals(portCount, portsResources.size());
            }
        }
    }

    @Test
    public void validNetworksLegacy() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-network-legacy.yml").getFile());
        DefaultServiceSpec defaultServiceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
        Assert.assertEquals("dcos", Iterables.get(defaultServiceSpec.getPods().get(0).getNetworks(), 0).getName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidNetworks() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        // this service spec contains specifies an overlay network that doesn't support port mapping, but contains
        // port mapping requests
        File file = new File(classLoader.getResource("invalid-network.yml").getFile());
        DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
    }

    @Test
    public void invalidScalarCpuResource() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        try {
            File file = new File(classLoader.getResource("invalid-scalar-cpu-resource.yml").getFile());
            DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
            Assert.fail("Expected exception");
        } catch (ConstraintViolationException e) {
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            Assert.assertEquals(1, constraintViolations.size());
        }
    }

    @Test
    public void invalidScalarMemResource() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        try {
            File file = new File(classLoader.getResource("invalid-scalar-mem-resource.yml").getFile());
            DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
            Assert.fail("Expected exception");
        } catch (ConstraintViolationException e) {
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            Assert.assertEquals(1, constraintViolations.size());
        }
    }

    @Test
    public void invalidScalarDiskResource() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        try {
            File file = new File(classLoader.getResource("invalid-scalar-disk-resource.yml").getFile());
            DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
            Assert.fail("Expected exception");
        } catch (ConstraintViolationException e) {
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            Assert.assertEquals(1, constraintViolations.size());
        }
    }

    @Test(expected = RLimit.InvalidRLimitException.class)
    public void invalidRLimitName() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-rlimit-name.yml").getFile());
        DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
    }

    @Test(expected = RLimit.InvalidRLimitException.class)
    public void invalidRLimitNameLegacy() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-rlimit-legacy-name.yml").getFile());
        DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
    }

    @Test
    public void invalidTaskNamePojo() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());

        when(mockFileReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(mockFileReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(mockFileReader.read("config-three.conf.mustache")).thenReturn("hi");

        DefaultServiceSpec defaultServiceSpec =
                DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags)
                        .setFileReader(mockFileReader)
                        .build();
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

    @Test(expected = IllegalArgumentException.class)
    public void vipPortNameCollision() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-vip-port-name-collision.yml").getFile());
        DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
    }

    @Test
    public void invalidTaskSpecNoResource() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-task-resources.yml").getFile());
        try {
            DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
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
            DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
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
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-minimal.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
        Assert.assertNotNull(serviceSpec);
        Assert.assertNotNull(serviceSpec.getZookeeperConnection());
        Assert.assertEquals(DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING, serviceSpec.getZookeeperConnection());
    }

    @Test
    public void customZKConnection() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-customzk.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
        Assert.assertNotNull(serviceSpec);
        Assert.assertNotNull(serviceSpec.getZookeeperConnection());
        Assert.assertEquals("custom.master.mesos:2181", serviceSpec.getZookeeperConnection());
    }

    @Test
    public void executorUriInjection() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-minimal.yml").getFile());

        DefaultServiceSpec defaultServiceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();
        Assert.assertTrue(defaultServiceSpec.getPods().get(0).getUris().contains(URI.create("test-executor-uri")));
    }

    @Test
    public void getUserFromPod() {
        PodSpec podSpec = mock(PodSpec.class);
        when(podSpec.getUser()).thenReturn(Optional.of("user"));
        Assert.assertEquals("user", DefaultServiceSpec.getUser(null, Arrays.asList(podSpec)));
    }

    @Test
    public void getUserFromService() {
        PodSpec podSpec = mock(PodSpec.class);
        when(podSpec.getUser()).thenReturn(Optional.of("pod-user"));
        Assert.assertEquals("service-user", DefaultServiceSpec.getUser("service-user", Arrays.asList(podSpec)));
    }

    @Test
    public void getUserFromDefault() {
        PodSpec podSpec = mock(PodSpec.class);
        when(podSpec.getUser()).thenReturn(Optional.empty());
        Assert.assertEquals(DcosConstants.DEFAULT_SERVICE_USER, DefaultServiceSpec.getUser(null, Arrays.asList(podSpec)));
    }

    private void validateServiceSpec(String fileName, Boolean supportGpu) throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(fileName).getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).build();

        capabilities = mock(Capabilities.class);
        when(capabilities.supportsGpuResource()).thenReturn(supportGpu);
        when(capabilities.supportsCNINetworking()).thenReturn(true);

        Persister persister = new MemPersister();
        Capabilities.overrideCapabilities(capabilities);
        DefaultScheduler.newBuilder(serviceSpec, flags, new MemPersister())
                .setStateStore(new StateStore(persister))
                .setConfigStore(new ConfigStore<>(DefaultServiceSpec.getConfigurationFactory(serviceSpec), persister))
                .build();
    }
}
