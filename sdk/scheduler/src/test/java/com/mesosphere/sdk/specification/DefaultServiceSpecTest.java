package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidNullException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.Iterables;
import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementField;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.offer.evaluate.placement.TestPlacementUtils;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLToInternalMappers;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.testutils.SchedulerConfigTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import com.mesosphere.sdk.testutils.TestPodFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class DefaultServiceSpecTest {

    private static final SchedulerConfig SCHEDULER_CONFIG = SchedulerConfigTestUtils.getTestSchedulerConfig();

    private static final PodSpec POD_SPEC = TestPodFactory.getPodSpec(
            "POD-A",
            TestConstants.RESOURCE_SET_ID + "-A",
            "A",
            "echo A",
            TestConstants.SERVICE_USER,
            1,
            1.0,
            1000.0,
            1500.0);

    @Mock
    private YAMLToInternalMappers.ConfigTemplateReader configTemplateReader;
    @Mock
    private Capabilities capabilities;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    public void validExhaustive() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();

        when(configTemplateReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(configTemplateReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(configTemplateReader.read("config-three.conf.mustache")).thenReturn("hi");

        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG)
                .setConfigTemplateReader(configTemplateReader)
                .build();
        Assert.assertNotNull(serviceSpec);
    }

    @Test
    public void validMinimal() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-minimal.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
        Assert.assertNotNull(serviceSpec);
    }

    @Test
    public void validSimple() throws Exception {
        validateServiceSpec("valid-simple.yml", DcosConstants.DEFAULT_GPU_POLICY);
    }

    @Test
    public void validGpuResource() throws Exception {
        validateServiceSpec("valid-gpu-resource.yml", true);
    }

    @Test
    public void validGpuResourceSet() throws Exception {
        validateServiceSpec("valid-gpu-resourceset.yml", true);
    }

    @Test
    public void validProfileMountVolume() throws Exception {
        validateServiceSpec("valid-profile-mount-volume.yml", DcosConstants.DEFAULT_GPU_POLICY);
    }

    @Test
    public void validPortResourceEnvKey() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-envkey-ports.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();

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
    public void validSeccompUnconfined() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("seccomp-unconfined.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
        PodSpec spec = serviceSpec.getPods().get(0);
        Assert.assertEquals(spec.getSeccompUnconfined(), true);
        Assert.assertEquals(spec.getSeccompProfileName(), Optional.empty());
    }

    @Test
    public void validSeccompProfileName() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("seccomp-profile-name.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
        PodSpec spec = serviceSpec.getPods().get(0);
        Assert.assertEquals(spec.getSeccompUnconfined(), false);
        Assert.assertEquals(spec.getSeccompProfileName().get(), "foobar");
    }

    @Test(expected = Exception.class)
    public void invalidSeccompInfo() throws Exception {
        //cannot specify both seccomp-unconfined and seccomp-profile at the sane ti
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-seccomp-info.yml").getFile());
        DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
    }

    public void validSeccompInfoAndProfile() throws Exception {
        //cannot specify both seccomp-unconfined and seccomp-profile at the sane ti
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-seccomp-info.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
        PodSpec spec = serviceSpec.getPods().get(0);
        Assert.assertEquals(spec.getSeccompUnconfined(), false);
        Assert.assertEquals(spec.getSeccompProfileName().get(), "foobar");
    }

    @Test
    public void validPortRangesTest() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("ranges.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();

        List<ResourceSpec> portsResources = serviceSpec.getPods().get(0).getTasks().get(0).getResourceSet()
            .getResources()
            .stream()
            .filter(r -> r.getName().equals("ports"))
            .collect(Collectors.toList());

        Assert.assertEquals(2, portsResources.size());

        PortSpec portSpec = (PortSpec) portsResources.get(0);
        Assert.assertEquals("name1", portSpec.getPortName());
        Assert.assertEquals("key1", portSpec.getEnvKey());
        Assert.assertEquals(1, portSpec.getRanges().get(0).getBegin().intValue());
        Assert.assertEquals(21, portSpec.getRanges().get(0).getEnd().intValue());

        Assert.assertEquals(2000, portSpec.getRanges().get(1).getBegin().intValue());
        Assert.assertEquals(5050, portSpec.getRanges().get(1).getEnd().intValue());


        portSpec = (PortSpec) portsResources.get(1);
        Assert.assertEquals("name2", portSpec.getPortName());
        Assert.assertEquals(RangeSpec.MIN_PORT, portSpec.getRanges().get(0).getBegin().intValue());
        Assert.assertEquals(21, portSpec.getRanges().get(0).getEnd().intValue());

        Assert.assertEquals(5000, portSpec.getRanges().get(1).getBegin().intValue());
        Assert.assertEquals(RangeSpec.MAX_PORT, portSpec.getRanges().get(1).getEnd().intValue());
    }

    @Test
    public void validPortResource() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-multiple-ports.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();

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
            DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
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
            DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
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
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();

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
        ServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(rawServiceSpec, SCHEDULER_CONFIG, file.getParentFile()).build();
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
        Assert.assertTrue(networkSpec.getPortMappings().get(4040) == 8080);
        Assert.assertTrue(networkSpec.getPortMappings().get(4041) == 8081);
        validateServiceSpec("valid-automatic-cni-port-forwarding.yml", DcosConstants.DEFAULT_GPU_POLICY);
    }

    @Test
    public void validTaskKillGracePeriodSeconds() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-task-kill-grace-period-seconds.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();

        Assert.assertNotEquals(15, DefaultTaskSpec.TASK_KILL_GRACE_PERIOD_SECONDS_DEFAULT);
        int taskKillGracePeriodSeconds = getTaskKillGracePeriodSeconds(serviceSpec);
        Assert.assertEquals(15, taskKillGracePeriodSeconds);
    }

    @Test
    public void validTaskKillGracePeriodSecondsReasonableDefault() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-minimal.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();

        int taskKillGracePeriodSeconds = getTaskKillGracePeriodSeconds(serviceSpec);
        Assert.assertEquals(DefaultTaskSpec.TASK_KILL_GRACE_PERIOD_SECONDS_DEFAULT, taskKillGracePeriodSeconds);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidTaskKillGracePeriodSeconds() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-task-kill-grace-period-seconds.yml").getFile());
        DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
    }

    private int getTaskKillGracePeriodSeconds(DefaultServiceSpec serviceSpec) {
        return serviceSpec.getPods().get(0).getTasks().get(0).getTaskKillGracePeriodSeconds();
    }

    @Test
    public void invalidDuplicatePodName() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-pod-name.yml").getFile());
        try {
            DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
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
            DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
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
            DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
            Assert.fail("Expected exception");
        } catch (JsonMappingException e) {
            Assert.assertTrue(e.getCause().toString(), e.getCause() instanceof JsonParseException);
            JsonParseException cause = (JsonParseException) e.getCause();
            Assert.assertTrue(cause.getMessage().contains("Duplicate field 'count'"));
        }
    }

    @Test
    public void validHostVolumeMode() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-host-volume.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
        PodSpec spec = serviceSpec.getPods().get(0);

        for (HostVolumeSpec volumeSpec : spec.getHostVolumes()) {
            Assert.assertEquals("host-volume-etc", volumeSpec.getContainerPath());
            Assert.assertEquals("/etc", volumeSpec.getHostPath());
            Assert.assertEquals(Protos.Volume.Mode.RO, volumeSpec.getMode().get());
        }
    }


    @Test
    public void invalidVolumeAndVolumes() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-volume-and-volumes.yml").getFile());
        try {
            DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
            Assert.fail("Expected exception");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("Both 'volume' and 'volumes'"));
        }
    }

    @Test(expected = FileNotFoundException.class)
    public void invalidConfigFile() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-config-file.yml").getFile());
        DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
    }

    @Test(expected = IllegalStateException.class)
    public void invalidPlanSteps() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-plan-steps.yml").getFile());
        RawServiceSpec rawSpec = RawServiceSpec.newBuilder(file).build();
        DefaultScheduler.newBuilder(
                DefaultServiceSpec.newGenerator(
                        rawSpec,
                        SCHEDULER_CONFIG,
                        file.getParentFile()).build(),
                SCHEDULER_CONFIG,
                MemPersister.newBuilder().build())
                .setPlansFrom(rawSpec)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidPodNamePojo() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());

        when(configTemplateReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(configTemplateReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(configTemplateReader.read("config-three.conf.mustache")).thenReturn("hi");

        DefaultServiceSpec defaultServiceSpec =
                DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG)
                        .setConfigTemplateReader(configTemplateReader)
                        .build();
        List<PodSpec> pods = defaultServiceSpec.getPods();
        pods.add(pods.get(0));
        DefaultServiceSpec.newBuilder(defaultServiceSpec)
                .pods(pods)
                .build();
    }

    @Test
    public void invalidTaskName() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-task-name.yml").getFile());
        try {
            DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
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
        DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
    }

    @Test
    public void validImage() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-image.yml").getFile());
        DefaultServiceSpec defaultServiceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
        Assert.assertEquals("group/image", defaultServiceSpec.getPods().get(0).getImage().get());
    }

    @Test
    public void validTaskLabels() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-task-labels.yml").getFile());
        DefaultServiceSpec defaultServiceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
        PodSpec podSpec = defaultServiceSpec.getPods().get(0);
        Assert.assertTrue("", Iterables.get(podSpec.getTasks(), 0).getTaskLabels().containsKey("label1"));
        Assert.assertTrue("", Iterables.get(podSpec.getTasks(), 0).getTaskLabels()
                          .get("label1").equals("label1-value"));
        Assert.assertTrue("", Iterables.get(podSpec.getTasks(), 0).getTaskLabels().containsKey("label2"));
        Assert.assertTrue("", Iterables.get(podSpec.getTasks(), 0).getTaskLabels()
                          .get("label2").equals("path:/"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidTaskLabelsFormat() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-task-labels-format.yml").getFile());
        DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidTaskLabelsBlank() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-task-labels-blank.yml").getFile());
        DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidImageNull() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-image-null.yml").getFile());
        DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
    }

    @Test
    public void validNetworks() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-network.yml").getFile());
        DefaultServiceSpec defaultServiceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
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
                .get("key2").equals("val2a:val2b"));
    }

    @Test
    public void validPortMappingNetworkRespectsPortResources() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-automatic-cni-port-forwarding.yml").getFile());
        DefaultServiceSpec defaultServiceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
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

    @Test(expected = IllegalArgumentException.class)
    public void invalidNetworks() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        // this service spec contains specifies an overlay network that doesn't support port mapping, but contains
        // port mapping requests
        File file = new File(classLoader.getResource("invalid-network.yml").getFile());
        DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidNetworkLabelsFormat() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-network-labels-format.yml").getFile());
        DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidNetworkLabelsBlank() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-network-labels-blank.yml").getFile());
        DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidScalarCpuResource() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-scalar-cpu-resource.yml").getFile());
        DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidScalarMemResource() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-scalar-mem-resource.yml").getFile());
        DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidScalarDiskResource() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-scalar-disk-resource.yml").getFile());
        DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
    }

    @Test(expected = RLimitSpec.InvalidRLimitException.class)
    public void invalidRLimitName() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-rlimit-name.yml").getFile());
        DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidTaskNamePojo() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());

        when(configTemplateReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(configTemplateReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(configTemplateReader.read("config-three.conf.mustache")).thenReturn("hi");

        DefaultServiceSpec defaultServiceSpec =
                DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG)
                        .setConfigTemplateReader(configTemplateReader)
                        .build();
        List<PodSpec> pods = defaultServiceSpec.getPods();
        PodSpec aPod = pods.get(0);
        List<TaskSpec> tasks = aPod.getTasks();
        tasks.add(tasks.get(0));
        DefaultPodSpec.newBuilder(aPod)
                .tasks(tasks)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void vipPortNameCollision() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-vip-port-name-collision.yml").getFile());
        DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidTaskSpecNoResource() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-task-resources.yml").getFile());
        DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
    }

    @Test
    public void invalidDuplicateResourceSetName() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-resource-set-name.yml").getFile());
        try {
            DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
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
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
        Assert.assertNotNull(serviceSpec);
        Assert.assertNotNull(serviceSpec.getZookeeperConnection());
        Assert.assertEquals(DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING, serviceSpec.getZookeeperConnection());
    }

    @Test
    public void customZKConnection() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-customzk.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
        Assert.assertNotNull(serviceSpec);
        Assert.assertNotNull(serviceSpec.getZookeeperConnection());
        Assert.assertEquals("custom.master.mesos:2181", serviceSpec.getZookeeperConnection());
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

    @Test
    public void getUserWithNullPodSpecListReturnsDefaultUser() {
        Assert.assertEquals(DcosConstants.DEFAULT_SERVICE_USER, DefaultServiceSpec.getUser(null, null));
    }

    @Test
    public void getListOfNullPodSpecsReturnsDefaultUser() {
        final List<PodSpec> listOfNull = new ArrayList<>();
        listOfNull.add(null);
        Assert.assertEquals(DcosConstants.DEFAULT_SERVICE_USER, DefaultServiceSpec.getUser(null, listOfNull));
    }


    private void validateServiceSpec(String fileName, Boolean supportGpu) throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(fileName).getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();

        capabilities = mock(Capabilities.class);
        when(capabilities.supportsGpuResource()).thenReturn(supportGpu);
        when(capabilities.supportsCNINetworking()).thenReturn(true);
        when(capabilities.supportsDomains()).thenReturn(true);

        Capabilities.overrideCapabilities(capabilities);
        DefaultScheduler.newBuilder(serviceSpec, SCHEDULER_CONFIG, MemPersister.newBuilder().build()).build();
    }

    @Test
    public void testGoalStateDeserializesOldValues() throws Exception {
        ObjectMapper objectMapper = SerializationUtils.registerDefaultModules(new ObjectMapper());
        DefaultServiceSpec.ConfigFactory.GoalStateDeserializer goalStateDeserializer =
                new DefaultServiceSpec.ConfigFactory.GoalStateDeserializer();

        SimpleModule module = new SimpleModule();
        module.addDeserializer(GoalState.class, goalStateDeserializer);
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
        try {
            DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
            Assert.fail("expected exception");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals(
                    "Unsupported GoalState FINISHED in task meta-data-task, expected one of: [UNKNOWN, RUNNING, FINISH, ONCE]", e.getMessage());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructConfigStoreWithUnknownCustomType() {
        ServiceSpec serviceSpec = getServiceSpec(
                DefaultPodSpec.newBuilder(POD_SPEC)
                        .placementRule(TestPlacementUtils.PASS)
                        .build());
        Assert.assertTrue(serviceSpec.getPods().get(0).getPlacementRule().isPresent());
        DefaultServiceSpec.getConfigurationFactory(serviceSpec);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructConfigStoreWithRegisteredCustomTypeMissingEquals() {
        ServiceSpec serviceSpec = getServiceSpec(
                DefaultPodSpec.newBuilder(POD_SPEC)
                        .placementRule(new PlacementRuleMissingEquality())
                        .build());
        Assert.assertTrue(serviceSpec.getPods().get(0).getPlacementRule().isPresent());
        DefaultServiceSpec.getConfigurationFactory(serviceSpec, Arrays.asList(PlacementRuleMissingEquality.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructConfigStoreWithRegisteredCustomTypeBadAnnotations() {
        ServiceSpec serviceSpec = getServiceSpec(
                DefaultPodSpec.newBuilder(POD_SPEC)
                        .placementRule(new PlacementRuleMismatchedAnnotations("hi"))
                        .build());
        Assert.assertTrue(serviceSpec.getPods().get(0).getPlacementRule().isPresent());
        DefaultServiceSpec.getConfigurationFactory(serviceSpec, Arrays.asList(PlacementRuleMismatchedAnnotations.class));
    }

    @Test
    public void testConstructConfigStoreWithRegisteredGoodCustomType() {
        ServiceSpec serviceSpec = getServiceSpec(
                DefaultPodSpec.newBuilder(POD_SPEC)
                        .placementRule(TestPlacementUtils.PASS)
                        .build());
        Assert.assertTrue(serviceSpec.getPods().get(0).getPlacementRule().isPresent());
        DefaultServiceSpec.getConfigurationFactory(serviceSpec, Arrays.asList(TestPlacementUtils.PASS.getClass()));
    }

    private static ServiceSpec getServiceSpec(PodSpec... pods) {
        return DefaultServiceSpec.newBuilder()
                .name(TestConstants.SERVICE_NAME)
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .zookeeperConnection("badhost-shouldbeignored:2181")
                .pods(Arrays.asList(pods))
                .user(TestConstants.SERVICE_USER)
                .build();
    }

    private static class PlacementRuleMissingEquality implements PlacementRule {
        @Override
        public EvaluationOutcome filter(Offer offer, PodInstance podInstance, Collection<TaskInfo> tasks) {
            return EvaluationOutcome.pass(this, "test pass").build();
        }

        @Override
        public Collection<PlacementField> getPlacementFields() {
            return Collections.emptyList();
        }
    }

    private static class PlacementRuleMismatchedAnnotations implements PlacementRule {

        private final String fork;

        @JsonCreator
        PlacementRuleMismatchedAnnotations(@JsonProperty("wrong") String spoon) {
            this.fork = spoon;
        }

        @Override
        public EvaluationOutcome filter(Offer offer, PodInstance podInstance, Collection<TaskInfo> tasks) {
            return EvaluationOutcome.pass(this, "test pass").build();
        }

        @Override
        public Collection<PlacementField> getPlacementFields() {
            return Collections.emptyList();
        }

        @JsonProperty("message")
        private String getMsg() {
            return fork;
        }

        @Override
        public boolean equals(Object o) {
            return EqualsBuilder.reflectionEquals(this, o);
        }

        @Override
        public int hashCode() {
            return HashCodeBuilder.reflectionHashCode(this);
        }
    }
}
