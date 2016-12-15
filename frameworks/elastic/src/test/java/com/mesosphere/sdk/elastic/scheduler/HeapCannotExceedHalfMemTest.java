package com.mesosphere.sdk.elastic.scheduler;

import com.mesosphere.sdk.config.validate.ConfigurationValidationError;
import com.mesosphere.sdk.config.validate.ConfigurationValidator;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.ValueUtils;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.mockito.Mockito.when;

public class HeapCannotExceedHalfMemTest {

    private static final int VALID_HEAP_SIZE_MB = 1000;
    private static final int NODE_MEM_SIZE_MB = VALID_HEAP_SIZE_MB * 2;

    private static final Collection<ResourceSpecification> resourceSpecs = Collections.singletonList(
            new DefaultResourceSpecification(
                    "mem",
                    ValueUtils.getValue(ResourceUtils.getUnreservedScalar("mem", NODE_MEM_SIZE_MB)),
                    "ROLE",
                    "PRINCIPAL",
                    "envkey"));
    private static final ConfigurationValidator<ServiceSpec> VALIDATOR = new HeapCannotExceedHalfMem();

    private static final CommandSpec validCommandSpec = new DefaultCommandSpec("cmd",
            createEnvironment(VALID_HEAP_SIZE_MB), "user", Collections.emptyList());
    private static final CommandSpec invalidCommandSpec = new DefaultCommandSpec("cmd",
            createEnvironment(VALID_HEAP_SIZE_MB + 1), "user", Collections.emptyList());
    @Mock
    private TaskSpec masterTaskSpec1;
    @Mock
    private TaskSpec dataTaskSpec1;
    @Mock
    private TaskSpec ingestTaskSpec1;
    @Mock
    private TaskSpec coordTaskSpec1;
    @Mock
    private ResourceSet masterResourceSet1;
    @Mock
    private ResourceSet dataResourceSet1;
    @Mock
    private ResourceSet ingestResourceSet1;
    @Mock
    private ResourceSet coordResourceSet1;
    @Mock
    private PodSpec kibanaPodSpec1;
    @Mock
    private PodSpec masterPodSpec1;
    @Mock
    private PodSpec dataPodSpec1;
    @Mock
    private PodSpec ingestPodSpec1;
    @Mock
    private PodSpec coordPodSpec1;

    private static Map<String, String> createEnvironment(int value) {
        Map<String, String> env = new HashMap<>();
        env.put("ES_JAVA_OPTS", String.format("-Xms%1$dM -Xmx%1$dM", value));
        return env;
    }

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(masterPodSpec1.getType()).thenReturn("master");
        when(masterPodSpec1.getTasks()).thenReturn(Collections.singletonList(masterTaskSpec1));
        when(masterTaskSpec1.getResourceSet()).thenReturn(masterResourceSet1);
        when(masterTaskSpec1.getCommand()).thenReturn(Optional.of(invalidCommandSpec));
        when(masterResourceSet1.getResources()).thenReturn(resourceSpecs);
        when(dataPodSpec1.getType()).thenReturn("data");
        when(dataPodSpec1.getTasks()).thenReturn(Collections.singletonList(dataTaskSpec1));
        when(dataTaskSpec1.getResourceSet()).thenReturn(dataResourceSet1);
        when(dataTaskSpec1.getCommand()).thenReturn(Optional.of(validCommandSpec));
        when(dataResourceSet1.getResources()).thenReturn(resourceSpecs);
        when(ingestPodSpec1.getType()).thenReturn("ingest");
        when(ingestPodSpec1.getTasks()).thenReturn(Collections.singletonList(ingestTaskSpec1));
        when(ingestTaskSpec1.getResourceSet()).thenReturn(ingestResourceSet1);
        when(ingestTaskSpec1.getCommand()).thenReturn(Optional.of(invalidCommandSpec));
        when(ingestResourceSet1.getResources()).thenReturn(resourceSpecs);
        when(coordPodSpec1.getType()).thenReturn("coordinator");
        when(coordPodSpec1.getTasks()).thenReturn(Collections.singletonList(coordTaskSpec1));
        when(coordTaskSpec1.getResourceSet()).thenReturn(coordResourceSet1);
        when(coordTaskSpec1.getCommand()).thenReturn(Optional.of(validCommandSpec));
        when(coordResourceSet1.getResources()).thenReturn(resourceSpecs);
        when(kibanaPodSpec1.getType()).thenReturn("kibana");
    }

    @Test
    public void heapSizeVersusMem() throws InvalidRequirementException {
        ServiceSpec newServiceSpec = DefaultServiceSpec.newBuilder()
                .name("elastic")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(kibanaPodSpec1, masterPodSpec1, dataPodSpec1, ingestPodSpec1, coordPodSpec1))
                .apiPort(8080)
                .build();

        Collection<ConfigurationValidationError> errors = VALIDATOR.validate(null, newServiceSpec);
        Assert.assertEquals(2, errors.size());
        Assert.assertTrue(errors.toArray()[0].toString().contains("Elasticsearch master node heap size 1001 exceeds half memory size 2000 in Service 'elastic'"));
        Assert.assertTrue(errors.toArray()[1].toString().contains("Elasticsearch ingest node heap size 1001 exceeds half memory size 2000 in Service 'elastic'"));
    }
}