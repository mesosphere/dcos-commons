package com.mesosphere.sdk.testutils;

import com.mesosphere.sdk.http.queries.ArtifactQueries.TemplateUrlFactory;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.specification.*;
import org.apache.mesos.Protos;

import java.util.*;

/**
 * Utilities for generating pods for testing.
 */
public class PodTestUtils {
    public static ResourceSet getResourceSet() {
        return DefaultResourceSet.newBuilder(
                TestConstants.ROLE,
                TestConstants.PRE_RESERVED_ROLE,
                TestConstants.PRINCIPAL)
                .id(TestConstants.RESOURCE_SET_ID)
                .addResource(DefaultResourceSpec.newBuilder()
                        .name(Constants.CPUS_RESOURCE_TYPE)
                        .value(Protos.Value.newBuilder()
                                .setType(Protos.Value.Type.SCALAR)
                                .setScalar(Protos.Value.Scalar.newBuilder()
                                        .setValue(1.0))
                                .build())
                        .role(TestConstants.ROLE)
                        .preReservedRole(TestConstants.PRE_RESERVED_ROLE)
                        .principal(TestConstants.PRINCIPAL)
                        .build())
                .volumes(Collections.emptyList())
                .build();
    }

    public static TaskSpec getTaskSpec() {
        return DefaultTaskSpec.newBuilder()
                .name(TestConstants.TASK_NAME)
                .goalState(GoalState.RUNNING)
                .resourceSet(getResourceSet())
                .build();
    }

    public static PodInstance getPodInstance(int index) {
        return new DefaultPodInstance(
                DefaultPodSpec.newBuilder(TestConstants.POD_TYPE, 1, Collections.singletonList(getTaskSpec()))
                .preReservedRole(TestConstants.PRE_RESERVED_ROLE)
                .build(),
                index);
    }

    public static TemplateUrlFactory getTemplateUrlFactory() {
        return new TemplateUrlFactory() {
            @Override
            public String get(UUID configId, String podType, String taskName, String configName) {
                return String.format("http://test-template/%s/%s/%s/%s", podType, taskName, configName, configId.toString());
            }
        };
    }
}
