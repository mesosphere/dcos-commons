package com.mesosphere.sdk.queues.scheduler;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

/**
 * This class tests the {@link SpecStore}.
 */
public class SpecStoreTest {
    private final Persister persister = new MemPersister();
    private static final UUID defaultID = UUID.fromString("fe729576-85a0-3eb2-9391-0204b3e018c0");

    private SpecStore specStore;

    @Before
    public void beforeEach() {
        specStore = new SpecStore(persister);
    }

    @Test
    public void equalIds() throws ConfigStoreException {
        UUID first = SpecStore.getId(getSpec());
        UUID second = SpecStore.getId(getSpec());
        Assert.assertEquals(first, second);
    }

    @Test
    public void equalIdsAcrossRestart() throws ConfigStoreException {
        UUID id = SpecStore.getId(getSpec());
        Assert.assertEquals(defaultID, id);
    }

    @Test
    public void differentIds() throws ConfigStoreException {
        UUID first = SpecStore.getId(getSpec());
        UUID second = SpecStore.getId(getSpec("second"));
        Assert.assertNotEquals(first, second);
    }

    @Test
    public void storeFetch() throws ConfigStoreException {
        ServiceSpec specA = getSpec();
        UUID id = specStore.store(specA);
        ServiceSpec specB = specStore.fetch(id);
        Assert.assertEquals(specA, specB);
    }

    static private ServiceSpec getSpec() {
        return getSpec("cmd");
    }

    static private ServiceSpec getSpec(String cmd) {
        return getServiceSpec(getPodSpec(getTaskSpec(cmd)));
    }

    static private ServiceSpec getServiceSpec(PodSpec... podSpecs) {
        DefaultServiceSpec.Builder builder = DefaultServiceSpec.newBuilder().name(TestConstants.SERVICE_NAME);
        for (PodSpec podSpec : podSpecs) {
            builder.addPod(podSpec);
        }

        return builder.build();
    }

    static private PodSpec getPodSpec(TaskSpec... taskSpecs) {
        return DefaultPodSpec.newBuilder("executor-uri")
                .type(TestConstants.POD_TYPE)
                .count(1)
                .tasks(Arrays.asList(taskSpecs))
                .build();
    }

    static private TaskSpec getTaskSpec(String cmd) {
        CommandSpec commandSpec = new DefaultCommandSpec(cmd, Collections.emptyMap());
        ResourceSpec resourceSpec = DefaultResourceSpec.newBuilder()
                .name("cpus")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .value(
                        Protos.Value.newBuilder()
                                .setType(Protos.Value.Type.SCALAR)
                                .setScalar(Protos.Value.Scalar.newBuilder()
                                        .setValue(1.0))
                                .build())
                .build();
        ResourceSet resourceSet =
                DefaultResourceSet.newBuilder(TestConstants.ROLE, Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                        .addResource(resourceSpec)
                        .id(TestConstants.RESOURCE_SET_ID)
                        .build();

        return DefaultTaskSpec.newBuilder()
                .name(TestConstants.TASK_NAME)
                .commandSpec(commandSpec)
                .resourceSet(resourceSet)
                .goalState(GoalState.RUNNING)
                .build();
    }
}
