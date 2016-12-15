package com.mesosphere.sdk.elastic.scheduler;

import com.mesosphere.sdk.config.validate.ConfigurationValidationError;
import com.mesosphere.sdk.config.validate.ConfigurationValidator;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.ValueUtils;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;

public class MasterTransportPortCannotChangeTest {

    private static final ConfigurationValidator<ServiceSpec> validator = new MasterTransportPortCannotChange();
    private static final Protos.Resource initialPorts = ResourceUtils.getUnreservedRanges("ports",
            Stream.of(9200, 9300).map(MasterTransportPortCannotChangeTest::buildSinglePortRange)
                    .collect(Collectors.toList()));
    private static final Protos.Resource changedPorts = ResourceUtils.getUnreservedRanges("ports",
            Stream.of(12340, 12345).map(MasterTransportPortCannotChangeTest::buildSinglePortRange)
                    .collect(Collectors.toList()));
    private static final Collection<ResourceSpecification> initialResourceSpecs = Collections.singletonList(
            new DefaultResourceSpecification("ports", ValueUtils.getValue(initialPorts), "ROLE", "PRINCIPAL", "x"));
    private static final Collection<ResourceSpecification> changedResourceSpecs = Collections.singletonList(
            new DefaultResourceSpecification("ports", ValueUtils.getValue(changedPorts), "ROLE", "PRINCIPAL", "x"));
    @Mock
    private TaskSpec masterTaskSpec1;
    @Mock
    private TaskSpec masterTaskSpec2;
    @Mock
    private ResourceSet resourceSet1;
    @Mock
    private ResourceSet resourceSet2;
    @Mock
    private PodSpec mockPodSpec1;
    @Mock
    private PodSpec mockPodSpec2;

    private static Protos.Value.Range buildSinglePortRange(int port) {
        return Protos.Value.Range.newBuilder().setBegin(port).setEnd(port).build();
    }

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockPodSpec1.getType()).thenReturn("master");
        when(mockPodSpec1.getTasks()).thenReturn(Collections.singletonList(masterTaskSpec1));
        when(mockPodSpec2.getType()).thenReturn("master");
        when(mockPodSpec2.getTasks()).thenReturn(Collections.singletonList(masterTaskSpec2));
        when(masterTaskSpec1.getResourceSet()).thenReturn(resourceSet1);
        when(masterTaskSpec2.getResourceSet()).thenReturn(resourceSet2);
        when(resourceSet1.getResources()).thenReturn(initialResourceSpecs);
        when(resourceSet2.getResources()).thenReturn(changedResourceSpecs);
    }

    @Test
    public void testMasterTransportPortCannotChange() throws InvalidRequirementException {
        ServiceSpec oldServiceSpec = DefaultServiceSpec.newBuilder()
                .name("elastic")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Collections.singletonList(mockPodSpec1))
                .apiPort(8080)
                .build();

        ServiceSpec newServiceSpec = DefaultServiceSpec.newBuilder()
                .name("elastic")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Collections.singletonList(mockPodSpec2))
                .apiPort(8080)
                .build();

        Collection<ConfigurationValidationError> errors = validator.validate(oldServiceSpec, newServiceSpec);
        Assert.assertEquals(1, errors.size());
        Assert.assertTrue(errors.toArray()[0].toString().contains("New config's master node TaskSet has a different transport port: 12345. Expected 9300."));
    }

    @Test
    public void testInitialMasterTransportPortSetting() throws InvalidRequirementException {
        ServiceSpec newServiceSpec = DefaultServiceSpec.newBuilder()
                .name("elastic")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Collections.singletonList(mockPodSpec2))
                .apiPort(8080)
                .build();
        Collection<ConfigurationValidationError> errors = validator.validate(null, newServiceSpec);
        Assert.assertEquals(0, errors.size());
    }

}