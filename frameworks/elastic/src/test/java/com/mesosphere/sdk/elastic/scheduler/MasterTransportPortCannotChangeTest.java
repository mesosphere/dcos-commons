package com.mesosphere.sdk.elastic.scheduler;

import org.apache.mesos.Protos;
import org.apache.mesos.config.validate.ConfigurationValidationError;
import org.apache.mesos.config.validate.ConfigurationValidator;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.ResourceUtils;
import org.apache.mesos.offer.ValueUtils;
import org.apache.mesos.specification.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mesosphere.sdk.elastic.scheduler.Elastic.MASTER_NODE_TYPE_NAME;
import static org.mockito.Mockito.when;


public class MasterTransportPortCannotChangeTest {
    private static final ConfigurationValidator<ServiceSpecification> validator = new MasterTransportPortCannotChange();
    private static final Protos.Resource initialPorts = ResourceUtils.getUnreservedRanges("ports",
            Stream.of(9200, 9300).map(OfferUtils::buildSinglePortRange).collect(Collectors.toList()));
    private static final Protos.Resource changedPorts = ResourceUtils.getUnreservedRanges("ports",
            Stream.of(12340, 12345).map(OfferUtils::buildSinglePortRange).collect(Collectors.toList()));
    private static final Collection<ResourceSpecification> initialResourceSpecs = Collections.singletonList(
            new DefaultResourceSpecification("ports", ValueUtils.getValue(initialPorts), "ROLE", "PRINCIPAL"));
    private static final Collection<ResourceSpecification> changedResourceSpecs = Collections.singletonList(
            new DefaultResourceSpecification("ports", ValueUtils.getValue(changedPorts), "ROLE", "PRINCIPAL"));

    @Mock
    private TaskSpecification masterTaskSpec1;
    @Mock
    private TaskSpecification masterTaskSpec2;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testMasterTransportPortCannotChange() throws InvalidRequirementException {
        ServiceSpecification oldServiceSpec = new DefaultServiceSpecification("elastic",
                Collections.singletonList(DefaultTaskSet.create(MASTER_NODE_TYPE_NAME,
                        Collections.singletonList(masterTaskSpec1))));

        ServiceSpecification newServiceSpec = new DefaultServiceSpecification("elastic",
                Collections.singletonList(DefaultTaskSet.create(MASTER_NODE_TYPE_NAME,
                        Collections.singletonList(masterTaskSpec2))));
        when(masterTaskSpec1.getResources()).thenReturn(initialResourceSpecs);
        when(masterTaskSpec2.getResources()).thenReturn(changedResourceSpecs);

        Collection<ConfigurationValidationError> errors = validator.validate(oldServiceSpec, newServiceSpec);
        Assert.assertEquals(1, errors.size());
        Assert.assertTrue(errors.toArray()[0].toString().contains("New config's master node TaskSet has a different transport port: 12345. Expected 9300."));
    }

    @Test
    public void testInitialMasterTransportPortSetting() throws InvalidRequirementException {
        ServiceSpecification newServiceSpec = new DefaultServiceSpecification("elastic",
                Collections.singletonList(DefaultTaskSet.create(MASTER_NODE_TYPE_NAME,
                        Collections.singletonList(masterTaskSpec2))));
        when(masterTaskSpec2.getResources()).thenReturn(changedResourceSpecs);

        Collection<ConfigurationValidationError> errors = validator.validate(null, newServiceSpec);
        Assert.assertEquals(0, errors.size());
    }
}
