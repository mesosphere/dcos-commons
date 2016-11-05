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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import static com.mesosphere.sdk.elastic.scheduler.Elastic.*;
import static org.mockito.Mockito.when;


public class HeapCannotExceedHalfMemTest {
    private static final int VALID_HEAP_SIZE_MB = 1000;
    private static final int NODE_MEM_SIZE_MB = VALID_HEAP_SIZE_MB * 2;

    private static final Collection<ResourceSpecification> resourceSpecs = Collections.singletonList(
            new DefaultResourceSpecification(
                    "mem",
                    ValueUtils.getValue(ResourceUtils.getUnreservedScalar("mem", NODE_MEM_SIZE_MB)),
                    "ROLE",
                    "PRINCIPAL"));
    private static final ConfigurationValidator<ServiceSpecification> VALIDATOR = new HeapCannotExceedHalfMem();
    private static final Protos.Environment.Variable validHeapEnvVar = OfferUtils.createEnvironmentVariable("ES_JAVA_OPTS",
            OfferUtils.elasticsearchHeapOpts(VALID_HEAP_SIZE_MB));
    private static final Protos.Environment.Variable invalidHeapEnvVar = OfferUtils.createEnvironmentVariable("ES_JAVA_OPTS",
            OfferUtils.elasticsearchHeapOpts(VALID_HEAP_SIZE_MB + 1));

    private static final Protos.CommandInfo validCommandInfo = Protos.CommandInfo.newBuilder()
            .setEnvironment(Protos.Environment.newBuilder().addAllVariables(Collections.singletonList(validHeapEnvVar)))
            .build();
    private static final Protos.CommandInfo invalidCommandInfo = Protos.CommandInfo.newBuilder()
            .setEnvironment(Protos.Environment.newBuilder().addAllVariables(Collections.singletonList(invalidHeapEnvVar)))
            .build();
    @Mock
    private TaskSpecification kibanaTaskSpec1;
    @Mock
    private TaskSpecification masterTaskSpec1;
    @Mock
    private TaskSpecification dataTaskSpec1;
    @Mock
    private TaskSpecification ingestTaskSpec1;
    @Mock
    private TaskSpecification coordTaskSpec1;
    @Mock
    private TaskSpecification kibanaTaskSpec2;
    @Mock
    private TaskSpecification masterTaskSpec2;
    @Mock
    private TaskSpecification dataTaskSpec2;
    @Mock
    private TaskSpecification ingestTaskSpec2;
    @Mock
    private TaskSpecification coordTaskSpec2;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void heapSizeVersusMem() throws InvalidRequirementException {
        ServiceSpecification oldServiceSpec = new DefaultServiceSpecification(
                "elastic",
                Arrays.asList(DefaultTaskSet.create(KIBANA_TYPE_NAME, Collections.singletonList(kibanaTaskSpec1)),
                        DefaultTaskSet.create(MASTER_NODE_TYPE_NAME, Collections.singletonList(masterTaskSpec1)),
                        DefaultTaskSet.create(DATA_NODE_TYPE_NAME, Collections.singletonList(dataTaskSpec1)),
                        DefaultTaskSet.create(INGEST_NODE_TYPE_NAME, Collections.singletonList(ingestTaskSpec1)),
                        DefaultTaskSet.create(COORDINATOR_NODE_TYPE_NAME, Collections.singletonList(coordTaskSpec1))));

        ServiceSpecification newServiceSpec = new DefaultServiceSpecification(
                "elastic",
                Arrays.asList(DefaultTaskSet.create(KIBANA_TYPE_NAME, Collections.singletonList(kibanaTaskSpec2)),
                        DefaultTaskSet.create(MASTER_NODE_TYPE_NAME, Collections.singletonList(masterTaskSpec2)),
                        DefaultTaskSet.create(DATA_NODE_TYPE_NAME, Collections.singletonList(dataTaskSpec2)),
                        DefaultTaskSet.create(INGEST_NODE_TYPE_NAME, Collections.singletonList(ingestTaskSpec2)),
                        DefaultTaskSet.create(COORDINATOR_NODE_TYPE_NAME, Collections.singletonList(coordTaskSpec2))));
        when(masterTaskSpec1.getResources()).thenReturn(resourceSpecs);
        when(masterTaskSpec2.getResources()).thenReturn(resourceSpecs);
        when(masterTaskSpec1.getCommand()).thenReturn(Optional.of(validCommandInfo));
        when(masterTaskSpec2.getCommand()).thenReturn(Optional.of(invalidCommandInfo));
        when(dataTaskSpec1.getResources()).thenReturn(resourceSpecs);
        when(dataTaskSpec2.getResources()).thenReturn(resourceSpecs);
        when(dataTaskSpec1.getCommand()).thenReturn(Optional.of(validCommandInfo));
        when(dataTaskSpec2.getCommand()).thenReturn(Optional.of(validCommandInfo));
        when(ingestTaskSpec1.getResources()).thenReturn(resourceSpecs);
        when(ingestTaskSpec2.getResources()).thenReturn(resourceSpecs);
        when(ingestTaskSpec1.getCommand()).thenReturn(Optional.of(validCommandInfo));
        when(ingestTaskSpec2.getCommand()).thenReturn(Optional.of(invalidCommandInfo));
        when(coordTaskSpec1.getResources()).thenReturn(resourceSpecs);
        when(coordTaskSpec2.getResources()).thenReturn(resourceSpecs);
        when(coordTaskSpec1.getCommand()).thenReturn(Optional.of(validCommandInfo));
        when(coordTaskSpec2.getCommand()).thenReturn(Optional.of(validCommandInfo));

        Collection<ConfigurationValidationError> errors = VALIDATOR.validate(oldServiceSpec, newServiceSpec);
        Assert.assertEquals(2, errors.size());
        Assert.assertTrue(errors.toArray()[0].toString().contains("Elasticsearch master node heap size 1001 exceeds half memory size 2000 in Service 'elastic'"));
        Assert.assertTrue(errors.toArray()[1].toString().contains("Elasticsearch ingest node heap size 1001 exceeds half memory size 2000 in Service 'elastic'"));
    }
}