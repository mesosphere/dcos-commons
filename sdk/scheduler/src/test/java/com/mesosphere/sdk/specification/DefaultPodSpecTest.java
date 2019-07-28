package com.mesosphere.sdk.specification;

import com.mesosphere.sdk.offer.evaluate.placement.HostnameRule;
import com.mesosphere.sdk.offer.evaluate.placement.RegexMatcher;
import com.mesosphere.sdk.specification.RLimitSpec.InvalidRLimitException;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This class tests {@link DefaultPodSpec}.
 */
public class DefaultPodSpecTest {

    /**
     * Two {@link TaskSpec}s with the same name should fail validation.
     */
    @Test(expected = IllegalArgumentException.class)
    public void clonePodSpecDupeNamesFail() throws InvalidRLimitException {
        TaskSpec mockTaskSpec = Mockito.mock(TaskSpec.class);
        Mockito.when(mockTaskSpec.getName()).thenReturn("test-task");

        getPodSpec(Arrays.asList(mockTaskSpec, mockTaskSpec));
    }

    @Test
    public void clonePodSpec() throws InvalidRLimitException {
        // Just use a mock object for the task spec. We just want to check that the lists match.
        // Copying TaskSpecs is tested in DefaultTaskSpecTest.
        TaskSpec mockTaskSpec = Mockito.mock(TaskSpec.class);
        Mockito.when(mockTaskSpec.getName()).thenReturn("test-task");

        PodSpec original = getPodSpec(Arrays.asList(mockTaskSpec));
        PodSpec clone = DefaultPodSpec.newBuilder(original).build();
        Assert.assertEquals(original, clone);
    }

    private static PodSpec getPodSpec(List<TaskSpec> taskSpecs) throws InvalidRLimitException {
        return DefaultPodSpec.newBuilder("podtype", 5, taskSpecs)
                .user("root")
                .allowDecommission(true)
                .image("mesosphere:image")
                .networks(Collections.singleton(DefaultNetworkSpec.newBuilder()
                        .networkName("net-name")
                        .portMappings(Collections.singletonMap(5, 4))
                        .networkLabels(Collections.singletonMap("key", "val"))
                        .build()))
                .rlimits(Collections.singleton(new RLimitSpec("RLIMIT_CPU", 20L, 50L)))
                .uris(Collections.singleton(URI.create("http://example.com/artifact.tgz")))
                .placementRule(new HostnameRule(RegexMatcher.create(".*")))
                .volumes(Collections.singleton(DefaultVolumeSpec.createRootVolume(
                        100,
                        TestConstants.CONTAINER_PATH,
                        TestConstants.ROLE,
                        TestConstants.PRE_RESERVED_ROLE,
                        TestConstants.PRINCIPAL)))
                .preReservedRole("slave_public")
                .secrets(Collections.singleton(DefaultSecretSpec.newBuilder()
                        .secretPath("secretPath")
                        .envKey("envKey")
                        .filePath("filePath")
                        .build()))
                .sharePidNamespace(true)
                .sharedMemory("PRIVATE")
                .sharedMemorySize(256)
                .build();
    }
}
