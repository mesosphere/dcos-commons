package com.mesosphere.sdk.specification;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.google.common.collect.Iterables;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.PortRequirement;
import com.mesosphere.sdk.offer.ResourceRequirement;
import com.mesosphere.sdk.offer.evaluate.PortsRequirement;
import org.apache.commons.collections.MapUtils;
import org.apache.mesos.Protos;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.util.RLimit;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.DefaultConfigStore;
import com.mesosphere.sdk.state.DefaultStateStore;
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

import com.mesosphere.sdk.specification.yaml.RawNetwork;
import com.mesosphere.sdk.specification.yaml.WriteOnceLinkedHashMap;
import java.net.URI;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory.*;
import static com.mesosphere.sdk.testutils.TestConstants.*;


public class DefaultServiceSpecTest {
    private static final SchedulerFlags flags = OfferRequirementTestUtils.getTestSchedulerFlags();
    @Mock private FileReader mockFileReader;
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
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file), flags, mockFileReader);
        Assert.assertNotNull(serviceSpec);
    }

    @Test
    public void validMinimal() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-minimal.yml").getFile());
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file), flags);
        Assert.assertNotNull(serviceSpec);
    }

    @Test
    public void validSimple() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-simple.yml").getFile());
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file), flags);
        Assert.assertNotNull(serviceSpec);
        Assert.assertFalse(DefaultService.serviceSpecRequestsGpuResources(serviceSpec));
        validateServiceSpec("valid-simple.yml", DcosConstants.DEFAULT_GPU_POLICY);
    }

    @Test
    public void validGpuResource() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-gpu-resource.yml").getFile());
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file), flags);
        Assert.assertNotNull(serviceSpec);
        Boolean obs = DefaultService.serviceSpecRequestsGpuResources(serviceSpec);
        Assert.assertTrue(String.format("Expected serviceSpec to request support GPUs got %s", obs), obs);
        validateServiceSpec("valid-gpu-resource.yml", true);
    }

    @Test
    public void validGpuResourceSet() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-gpu-resourceset.yml").getFile());
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file), flags);
        Assert.assertNotNull(serviceSpec);
        Boolean obs = DefaultService.serviceSpecRequestsGpuResources(serviceSpec);
        Assert.assertTrue(String.format("Expected serviceSpec to request support GPUs got %s", obs), obs);
    }

    @Test
    public void validPortResourceEnvKey() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-envkey-ports.yml").getFile());
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file), flags);

        List<ResourceSpec> portsResources = serviceSpec.getPods().get(0).getTasks().get(0).getResourceSet()
                .getResources()
                .stream()
                .filter(r -> r.getName().equals("ports"))
                .collect(Collectors.toList());

        Assert.assertEquals(1, portsResources.size());

       PortsRequirement portsRequirement = (PortsRequirement) portsResources.get(0).getResourceRequirement(null);
       List<ResourceRequirement> portReqList = (List<ResourceRequirement>) portsRequirement.getPortRequirements();

       Assert.assertEquals(3, portReqList.size());

       Assert.assertEquals("key1", ((PortRequirement) portReqList.get(0)).getCustomEnvKey().get());
       Assert.assertFalse(((PortRequirement) portReqList.get(1)).getCustomEnvKey().isPresent());
       Assert.assertFalse(((PortRequirement) portReqList.get(2)).getCustomEnvKey().isPresent());

    }

    @Test
    public void validPortResource() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-multiple-ports.yml").getFile());
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file), flags);

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
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("readiness-check.yml").getFile());
        RawServiceSpec rawServiceSpec = generateRawSpecFromYAML(file);
        DefaultServiceSpec serviceSpec = generateServiceSpec(rawServiceSpec, flags);

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
    public void validCniSpec() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-cni.yml").getFile());
        // parse the YAML and check it
        RawServiceSpec rawServiceSpec = generateRawSpecFromYAML(file);
        Assert.assertNotNull(rawServiceSpec);

        // get the raw networks and make sure they were parsed correctly
        WriteOnceLinkedHashMap<String, RawNetwork> rawNetworkMap = rawServiceSpec
                .getPods()
                .get("meta-data")
                .getNetworks();
        // test that we populated the RawNetwork object
        Assert.assertTrue(MapUtils.isNotEmpty(rawNetworkMap));
        Assert.assertTrue(rawNetworkMap.containsKey(OVERLAY_NETWORK_NAME));

        // test that the port mappings are correct
        RawNetwork rawNetwork = rawNetworkMap.get(OVERLAY_NETWORK_NAME);
        // host port
        ArrayList<Integer> expectedHostPorts = new ArrayList<>();
        expectedHostPorts.add(HOST_PORT);
        Assert.assertTrue(rawNetwork.getHostPorts().equals(expectedHostPorts));

        // container port
        ArrayList<Integer> expectedContainerPorts = new ArrayList<>();
        expectedContainerPorts.add(CONTAINER_PORT);
        Assert.assertTrue(rawNetwork.getContainerPorts().equals(expectedContainerPorts));

        // test that the serviceSpec can be translated from the raw service spec
        ServiceSpec serviceSpec = generateServiceSpec(rawServiceSpec, flags);
        Assert.assertNotNull(serviceSpec);

        // check that there are the correct number of networks and they have the correct name
        for (int i = 0; i < serviceSpec.getPods().size(); i++) {
            List<NetworkSpec> networkSpecs = serviceSpec.getPods().get(i)
                    .getNetworks()
                    .stream()
                    .collect(Collectors.toList());
            Integer exp = 1;
            Integer obs = networkSpecs.size();
            Assert.assertTrue(String.format("Got incorrect number of networks, should be %s got %s ",
                    exp, obs), obs.equals(exp));
            for (NetworkSpec networkSpec : networkSpecs) {
                Assert.assertTrue(networkSpec.getName().equals(OVERLAY_NETWORK_NAME));
            }
        }

        // check that they have the correct port mappings
        Function<Integer, Map<Integer, Integer>> getPortMappings = (index) ->
                serviceSpec.getPods().get(index)
                        .getNetworks()
                        .stream().collect(Collectors.toList())
                        .get(0).getPortMappings();  // we've already confirmed that there is only one NetworkSpec

        // Check the first one
        Map<Integer, Integer> portsMap = getPortMappings.apply(0);
        Assert.assertTrue(portsMap.size() == 1);
        Assert.assertTrue(portsMap.get(HOST_PORT) == CONTAINER_PORT);

        // Check the second one
        portsMap = getPortMappings.apply(1);
        Assert.assertTrue(portsMap.size() == 2);
        Assert.assertTrue(portsMap.get(HOST_PORT) == CONTAINER_PORT);
        Assert.assertTrue(portsMap.get(4041) == 8081);
        validateServiceSpec("valid-cni.yml", DcosConstants.DEFAULT_GPU_POLICY);
    }

    @Test
    public void validMinimalNetgroups() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-netgroups.yml").getFile());
        // parse the YAML and check it
        RawServiceSpec rawServiceSpec = generateRawSpecFromYAML(file);
        Assert.assertNotNull(rawServiceSpec);

        // get the RawNetwork object and check that the netgroups are correct
        WriteOnceLinkedHashMap<String, RawNetwork> networks = rawServiceSpec.getPods()
                .get("meta-data")
                .getNetworks();
        Assert.assertTrue(networks.size() == 1);
        Assert.assertTrue("Didn't find default overlay network 'dcos'",
                networks.containsKey(OVERLAY_NETWORK_NAME));
        List<String> netgroups = networks.get(OVERLAY_NETWORK_NAME).getNetgroups();
        Assert.assertTrue(netgroups.size() == 2);  // ["mygroup", "testgroup"]
        Assert.assertTrue(netgroups.get(0).equals("mygroup"));
        Assert.assertTrue(netgroups.get(1).equals("testgroup"));

        networks = rawServiceSpec.getPods()
                .get("meta-data-anothergroup")
                .getNetworks();
        Assert.assertTrue(networks.size() == 1);
        Assert.assertTrue("Didn't find default overlay network 'dcos'",
                networks.containsKey(OVERLAY_NETWORK_NAME));
        netgroups = networks.get(OVERLAY_NETWORK_NAME).getNetgroups();
        Assert.assertTrue(netgroups.size() == 2);  // ["mygroup", "anothergroup"]
        Assert.assertTrue(netgroups.get(0).equals("mygroup"));
        Assert.assertTrue(netgroups.get(1).equals("anothergroup"));

        networks = rawServiceSpec.getPods()
                .get("meta-data-joins-all")
                .getNetworks();
        Assert.assertTrue(networks.size() == 1);
        Assert.assertTrue("Didn't find default overlay network 'dcos'",
                networks.containsKey(OVERLAY_NETWORK_NAME));
        netgroups = networks.get(OVERLAY_NETWORK_NAME).getNetgroups();
        Assert.assertTrue(netgroups.size() == 0);

        // Now we check the ServiceSpec object
        ServiceSpec serviceSpec = generateServiceSpec(rawServiceSpec, flags);
        Assert.assertNotNull(serviceSpec);

        // test that the pods have the correct groups
        for (PodSpec podSpec : serviceSpec.getPods()) {
            for (NetworkSpec networkSpec : podSpec.getNetworks()) {
                if (networkSpec.getNetgroups().size() > 0) {
                    Assert.assertTrue(networkSpec.getNetgroups().size() == 2);
                    Assert.assertTrue(networkSpec.getNetgroups().contains("mygroup"));
                    Assert.assertTrue(networkSpec.getNetgroups().contains("anothergroup")
                            || networkSpec.getNetgroups().contains("testgroup"));
                } else {
                    Assert.assertTrue(networkSpec.getNetgroups().size() == 0);
                }
            }
        }
    }

    @Test
    public void validCniPortForwarding() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-automatic-cni-port-mapping.yml").getFile());
        // load the raw service spec and check that it parsed correctly
        RawServiceSpec rawServiceSpec = generateRawSpecFromYAML(file);
        Assert.assertNotNull(rawServiceSpec);
        Assert.assertEquals(rawServiceSpec
                .getPods().get("pod-type")
                .getNetworks().get(OVERLAY_NETWORK_NAME)
                .numberOfPortMappings(), 0);
        Assert.assertTrue(rawServiceSpec
                .getPods().get("meta-data-with-group")
                .getNetworks().get(OVERLAY_NETWORK_NAME)
                .getNetgroups().contains("mygroup"));
        Assert.assertTrue(rawServiceSpec
                .getPods().get("meta-data-with-port-mapping")
                .getNetworks().get(OVERLAY_NETWORK_NAME)
                .numberOfPortMappings() == 2);

        // Check that the raw service spec was correctly translated into the ServiceSpec
        ServiceSpec serviceSpec = generateServiceSpec(rawServiceSpec, flags);
        Assert.assertNotNull(serviceSpec);
        Assert.assertTrue(serviceSpec.getPods().size() == 4);
        // check the first pod
        PodSpec podSpec = serviceSpec.getPods().get(0);
        Assert.assertTrue(podSpec.getNetworks().size() == 1);
        NetworkSpec networkSpec = Iterables.get(podSpec.getNetworks(), 0);
        Assert.assertTrue(networkSpec.getNetgroups().size() == 0);
        Assert.assertTrue(networkSpec.getPortMappings().size() == 1);
        Assert.assertTrue(networkSpec.getPortMappings().get(8080) == 8080);
        // check the second pod
        podSpec = serviceSpec.getPods().get(1);
        Assert.assertTrue(podSpec.getNetworks().size() == 1);
        networkSpec = Iterables.get(podSpec.getNetworks(), 0);
        Assert.assertTrue(networkSpec.getPortMappings().size() == 2);
        Assert.assertTrue(networkSpec.getPortMappings().get(8080) == 8080);
        Assert.assertTrue(networkSpec.getPortMappings().get(8081) == 8081);
        // check the third pod
        podSpec = serviceSpec.getPods().get(2);
        Assert.assertTrue(podSpec.getNetworks().size() == 1);
        networkSpec = Iterables.get(podSpec.getNetworks(), 0);
        Assert.assertTrue(networkSpec.getPortMappings().size() == 2);
        Assert.assertTrue(networkSpec.getPortMappings().get(8080) == 8080);
        Assert.assertTrue(networkSpec.getPortMappings().get(8081) == 8081);
        Assert.assertTrue(networkSpec.getNetgroups().size() == 1);
        Assert.assertTrue(networkSpec.getNetgroups().contains("mygroup"));
        // check the fourth and final
        podSpec = serviceSpec.getPods().get(3);
        Assert.assertTrue(podSpec.getNetworks().size() == 1);
        networkSpec = Iterables.get(podSpec.getNetworks(), 0);
        Assert.assertTrue(networkSpec.getPortMappings().size() == 2);
        Assert.assertTrue(networkSpec.getPortMappings().get(4040)== 8080);
        Assert.assertTrue(networkSpec.getPortMappings().get(4041) == 8081);
        Assert.assertTrue(networkSpec.getNetgroups().size() == 1);
        Assert.assertTrue(networkSpec.getNetgroups().contains("mygroup"));
    }

    @Test
    public void validIpContainerMapping() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-ip-container-mapping.yml").getFile());
        // parse the YAML and make sure it's correct
        RawServiceSpec rawServiceSpec = generateRawSpecFromYAML(file);
        Assert.assertNotNull(rawServiceSpec);
        Assert.assertTrue(rawServiceSpec.getPods().get("meta-data").getNetworks().size() == 1);
        RawNetwork rawNetwork = Iterables
                .get(rawServiceSpec.getPods().get("meta-data").getNetworks().values(), 0);
        Assert.assertTrue(rawNetwork.getIpAddresses().size() == 1);
        Assert.assertTrue(rawNetwork.getIpAddresses().get(0).equals(IPADDRESS1));

        ServiceSpec serviceSpec = generateServiceSpec(rawServiceSpec, flags);
        Assert.assertNotNull(serviceSpec);
        NetworkSpec networkSpec = Iterables.get(serviceSpec.getPods().get(0).getNetworks(), 0);
        Assert.assertTrue(networkSpec.getIpAddresses().size() == 1);
        Assert.assertTrue(networkSpec.getIpAddresses().contains(IPADDRESS1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidDuplicateNetgroup() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-netgroup.yml").getFile());
        generateServiceSpec(generateRawSpecFromYAML(file), flags);
    }

    @Test
    public void invalidDuplicatePodName() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-pod-name.yml").getFile());
        try {
            generateServiceSpec(generateRawSpecFromYAML(file), flags);
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
            generateServiceSpec(generateRawSpecFromYAML(file), flags);
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
            generateServiceSpec(generateRawSpecFromYAML(file), flags);
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
            generateServiceSpec(generateRawSpecFromYAML(file), flags);
            Assert.fail("Expected exception");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("Both 'volume' and 'volumes'"));
        }
    }

    @Test(expected = FileNotFoundException.class)
    public void invalidConfigFile() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-config-file.yml").getFile());
        generateServiceSpec(generateRawSpecFromYAML(file), flags);
    }

    @Test(expected = IllegalStateException.class)
    public void invalidPlanSteps() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-plan-steps.yml").getFile());
        RawServiceSpec rawSpec = generateRawSpecFromYAML(file);
        DefaultScheduler.newBuilder(generateServiceSpec(rawSpec, flags), flags)
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
                generateServiceSpec(generateRawSpecFromYAML(file), flags, mockFileReader);
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
            generateServiceSpec(generateRawSpecFromYAML(file), flags);
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
        generateServiceSpec(generateRawSpecFromYAML(file), flags);
    }

    @Test
    public void validImage() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-image.yml").getFile());
        DefaultServiceSpec defaultServiceSpec = generateServiceSpec(generateRawSpecFromYAML(file), flags);
        Assert.assertEquals("group/image", defaultServiceSpec.getPods().get(0).getImage().get());
    }

    @Test
    public void validImageLegacy() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-image-legacy.yml").getFile());
        DefaultServiceSpec defaultServiceSpec = generateServiceSpec(generateRawSpecFromYAML(file), flags);
        Assert.assertEquals("group/image", defaultServiceSpec.getPods().get(0).getImage().get());
    }

    @Test
    public void validNetworks() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-network.yml").getFile());
        DefaultServiceSpec defaultServiceSpec = generateServiceSpec(generateRawSpecFromYAML(file), flags);
        Assert.assertEquals("test", Iterables.get(defaultServiceSpec.getPods().get(0).getNetworks(), 0)
                .getName());
    }

    @Test
    public void validNetworksLegacy() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-network-legacy.yml").getFile());
        DefaultServiceSpec defaultServiceSpec = generateServiceSpec(generateRawSpecFromYAML(file), flags);
        Assert.assertEquals("test", Iterables.get(defaultServiceSpec.getPods().get(0).getNetworks(), 0)
                .getName());
    }

    @Test(expected = UnrecognizedPropertyException.class)
    public void invalidNetworks() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-network.yml").getFile());
        generateServiceSpec(generateRawSpecFromYAML(file), flags);
    }

    @Test
    public void invalidScalarCpuResource() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        try {
            File file = new File(classLoader.getResource("invalid-scalar-cpu-resource.yml").getFile());
            generateServiceSpec(generateRawSpecFromYAML(file), flags);
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
            generateServiceSpec(generateRawSpecFromYAML(file), flags);
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
            generateServiceSpec(generateRawSpecFromYAML(file), flags);
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
        generateServiceSpec(generateRawSpecFromYAML(file), flags);
    }

    @Test(expected = RLimit.InvalidRLimitException.class)
    public void invalidRLimitNameLegacy() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-rlimit-legacy-name.yml").getFile());
        generateServiceSpec(generateRawSpecFromYAML(file), flags);
    }

    @Test
    public void invalidTaskNamePojo() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());

        when(mockFileReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(mockFileReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(mockFileReader.read("config-three.conf.mustache")).thenReturn("hi");

        DefaultServiceSpec defaultServiceSpec =
                generateServiceSpec(generateRawSpecFromYAML(file), flags, mockFileReader);
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
            generateServiceSpec(generateRawSpecFromYAML(file), flags);
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
            generateServiceSpec(generateRawSpecFromYAML(file), flags);
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
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file), flags);
        Assert.assertNotNull(serviceSpec);
        Assert.assertNotNull(serviceSpec.getZookeeperConnection());
        Assert.assertEquals(DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING, serviceSpec.getZookeeperConnection());
    }

    @Test
    public void customZKConnection() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-customzk.yml").getFile());
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file), flags);
        Assert.assertNotNull(serviceSpec);
        Assert.assertNotNull(serviceSpec.getZookeeperConnection());
        Assert.assertEquals("custom.master.mesos:2181", serviceSpec.getZookeeperConnection());
    }

    @Test
    public void executorUriInjection() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-minimal.yml").getFile());

        DefaultServiceSpec defaultServiceSpec = generateServiceSpec(generateRawSpecFromYAML(file), flags);
        Assert.assertTrue(defaultServiceSpec.getPods().get(0).getUris().contains(URI.create("test-executor-uri")));
    }

    private void validateServiceSpec(String fileName, Boolean supportGpu) throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(fileName).getFile());
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file), flags);

        capabilities = mock(Capabilities.class);
        when(capabilities.supportsGpuResource()).thenReturn(supportGpu);
        when(capabilities.supportCniPortMapping()).thenReturn(true);

        Persister persister = new MemPersister();
        DefaultScheduler.newBuilder(serviceSpec, flags)
                .setStateStore(new DefaultStateStore(persister))
                .setConfigStore(
                        new DefaultConfigStore<>(DefaultServiceSpec.getConfigurationFactory(serviceSpec), persister))
                .setCapabilities(capabilities)
                .build();
    }
}
