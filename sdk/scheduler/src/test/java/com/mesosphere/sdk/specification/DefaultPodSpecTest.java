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

import javax.validation.ConstraintViolationException;

/**
 * This class tests {@link DefaultPodSpec}.
 */
public class DefaultPodSpecTest {

    /**
     * Two {@link TaskSpec}s with the same name should fail validation.
     */
    @Test(expected = ConstraintViolationException.class)
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
        return new DefaultPodSpec(
                "podtype",
                "root",
                5,
                "mesosphere:image",
                Arrays.asList(new DefaultNetworkSpec("net-name", Collections.singletonMap(5, 4), Collections.singletonMap("key", "val"))),
                Arrays.asList(new RLimitSpec("RLIMIT_CPU", 20L, 50L)),
                Arrays.asList(URI.create("http://example.com/artifact.tgz")),
                taskSpecs,
                new HostnameRule(RegexMatcher.create(".*")),
                Arrays.asList(new DefaultVolumeSpec(
                        100,
                        VolumeSpec.Type.ROOT,
                        TestConstants.CONTAINER_PATH,
                        TestConstants.ROLE,
                        TestConstants.PRE_RESERVED_ROLE,
                        TestConstants.PRINCIPAL)),
                "slave_public",
                Arrays.asList(new DefaultSecretSpec("secretPath", "envKey", "filePath")),
                true,
                true);
    }
}
