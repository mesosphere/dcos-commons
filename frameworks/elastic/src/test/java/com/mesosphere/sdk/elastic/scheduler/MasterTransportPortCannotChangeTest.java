package com.mesosphere.sdk.elastic.scheduler;

import org.apache.mesos.Protos;
import org.apache.mesos.config.validate.ConfigurationValidationError;
import org.apache.mesos.config.validate.ConfigurationValidator;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.specification.DefaultServiceSpecification;
import org.apache.mesos.specification.DefaultTaskSet;
import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.specification.TaskSpecification;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import static com.mesosphere.sdk.elastic.scheduler.Elastic.MASTER_NODE_TYPE_NAME;
import static org.mockito.Mockito.when;


public class MasterTransportPortCannotChangeTest {
    private static final ConfigurationValidator<ServiceSpecification> validator = new MasterTransportPortCannotChange();
    private static final Protos.Environment.Variable transportPort = OfferUtils.createEnvironmentVariable("MASTER_NODE_TRANSPORT_PORT",
            "9300");
    private static final Protos.Environment.Variable differentTransportPort = OfferUtils.createEnvironmentVariable("MASTER_NODE_TRANSPORT_PORT",
            "9301");

    private static final Protos.CommandInfo commandInfo = Protos.CommandInfo.newBuilder()
            .setEnvironment(Protos.Environment.newBuilder().addAllVariables(Collections.singletonList(transportPort)))
            .build();
    private static final Protos.CommandInfo differentCommandInfo = Protos.CommandInfo.newBuilder()
            .setEnvironment(Protos.Environment.newBuilder().addAllVariables(Collections.singletonList(differentTransportPort)))
            .build();
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
        when(masterTaskSpec1.getCommand()).thenReturn(Optional.of(commandInfo));
        when(masterTaskSpec2.getCommand()).thenReturn(Optional.of(differentCommandInfo));

        Collection<ConfigurationValidationError> errors = validator.validate(oldServiceSpec, newServiceSpec);
        Assert.assertEquals(1, errors.size());
        Assert.assertTrue(errors.toArray()[0].toString().contains("New config's master node TaskSet has a different transport port: 9301. Expected 9300."));
    }

    @Test
    public void testInitialMasterTransportPortSetting() throws InvalidRequirementException {
        ServiceSpecification newServiceSpec = new DefaultServiceSpecification("elastic",
                Collections.singletonList(DefaultTaskSet.create(MASTER_NODE_TYPE_NAME,
                        Collections.singletonList(masterTaskSpec2))));
        when(masterTaskSpec2.getCommand()).thenReturn(Optional.of(commandInfo));

        Collection<ConfigurationValidationError> errors = validator.validate(null, newServiceSpec);
        Assert.assertEquals(0, errors.size());
    }
}
