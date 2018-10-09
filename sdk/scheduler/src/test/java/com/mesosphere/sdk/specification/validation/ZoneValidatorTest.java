package com.mesosphere.sdk.specification.validation;

import com.mesosphere.sdk.config.validate.ConfigValidationError;
import com.mesosphere.sdk.config.validate.ZoneValidator;
import com.mesosphere.sdk.offer.evaluate.placement.ExactMatcher;
import com.mesosphere.sdk.offer.evaluate.placement.HostnameRule;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.offer.evaluate.placement.RoundRobinByZoneRule;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.testutils.TestConstants;

import org.junit.Assert;
import org.junit.Test;

import java.util.*;

/**
 * This class tests the {@link ZoneValidator} class.
 */
public class ZoneValidatorTest {
    private static final String POD_TYPE = TestConstants.POD_TYPE;
    private static final String TASK_NAME = TestConstants.TASK_NAME;

    @Test
    public void noOldConfig() {
        Collection<ConfigValidationError> errors = ZoneValidator.validate(
                Optional.empty(), null, POD_TYPE);
        Assert.assertTrue(errors.isEmpty());
    }

    @Test
    public void taskTypeMissingInOldSpec() {
        TaskSpec oldTaskSpec = getTaskSpec(TASK_NAME, Collections.emptyMap());
        TaskSpec newTaskSpec = getTaskSpec(Collections.emptyMap());
        ServiceSpec oldSpec = getServiceSpec(oldTaskSpec);
        ServiceSpec newSpec = getServiceSpec(newTaskSpec);

        Collection<ConfigValidationError> errors = ZoneValidator.validate(Optional.of(oldSpec), newSpec, POD_TYPE);
        Assert.assertTrue(errors.isEmpty());
    }

    @Test
    public void emptyToTrueShouldFail() {
        TaskSpec oldTaskSpec = getTaskSpec(Collections.emptyMap());

        ServiceSpec oldSpec = getServiceSpec(oldTaskSpec);
        ServiceSpec newSpec = getServiceSpec(true);

        Collection<ConfigValidationError> errors = ZoneValidator.validate(Optional.of(oldSpec), newSpec, POD_TYPE);
        Assert.assertEquals(1, errors.size());
    }

    @Test
    public void emptyToFalseShouldSucceed() {
        TaskSpec oldTaskSpec = getTaskSpec(Collections.emptyMap());

        ServiceSpec oldSpec = getServiceSpec(oldTaskSpec);
        ServiceSpec newSpec = getServiceSpec(false);

        Collection<ConfigValidationError> errors = ZoneValidator.validate(Optional.of(oldSpec), newSpec, POD_TYPE);
        Assert.assertTrue(errors.isEmpty());
    }

    @Test
    public void trueToFalseShouldFail() {
        ServiceSpec oldSpec = getServiceSpec(true);
        ServiceSpec newSpec = getServiceSpec(false);

        Collection<ConfigValidationError> errors = ZoneValidator.validate(Optional.of(oldSpec), newSpec, POD_TYPE);
        Assert.assertEquals(1, errors.size());
    }

    @Test
    public void falseToTrueShouldFail() {
        ServiceSpec oldSpec = getServiceSpec(false);
        ServiceSpec newSpec = getServiceSpec(true);

        Collection<ConfigValidationError> errors = ZoneValidator.validate(Optional.of(oldSpec), newSpec, POD_TYPE);
        Assert.assertEquals(1, errors.size());
    }

    @Test
    public void trueToTrueShouldSucceed() {
        ServiceSpec oldSpec = getServiceSpec(true);
        ServiceSpec newSpec = getServiceSpec(true);

        Collection<ConfigValidationError> errors = ZoneValidator.validate(Optional.of(oldSpec), newSpec, POD_TYPE);
        Assert.assertTrue(errors.isEmpty());
    }

    private static ServiceSpec getServiceSpec(boolean referenceZones) {
        PlacementRule zoneRule = new RoundRobinByZoneRule(3);
        PlacementRule hostRule = new HostnameRule(ExactMatcher.create("hostname"));
        PlacementRule rule = referenceZones ? zoneRule : hostRule;

        PodSpec podSpec = getPodSpec(getTaskSpec(Collections.emptyMap()));
        podSpec = DefaultPodSpec.newBuilder(podSpec).placementRule(rule).build();

        return getServiceSpec(podSpec);
    }

    private static TaskSpec getTaskSpec(String name, Map<String, String> env) {
        return DefaultTaskSpec.newBuilder()
                .name(name)
                .goalState(GoalState.RUNNING)
                .resourceSet(
                        DefaultResourceSet.newBuilder(TestConstants.ROLE, Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                                .id(TestConstants.RESOURCE_SET_ID)
                                .cpus(1.0)
                                .memory(256.)
                                .addRootVolume(4096., TestConstants.CONTAINER_PATH)
                                .build())
                .commandSpec(DefaultCommandSpec.newBuilder(Collections.emptyMap())
                        .value("./server")
                        .environment(env)
                        .build())
                .build();
    }

    private static TaskSpec getTaskSpec(Map<String, String> env) {
        return getTaskSpec(TASK_NAME, env);
    }

    private static PodSpec getPodSpec(TaskSpec taskSpec) {
        return DefaultPodSpec.newBuilder(POD_TYPE, 1, Arrays.asList(taskSpec))
                .user(TestConstants.SERVICE_USER)
                .build();
    }

    private static ServiceSpec getServiceSpec(TaskSpec taskSpec) {
        return getServiceSpec(getPodSpec(taskSpec));
    }

    private static ServiceSpec getServiceSpec(PodSpec podSpec) {
        return DefaultServiceSpec.newBuilder()
                .name(TestConstants.SERVICE_NAME)
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .user(TestConstants.SERVICE_USER)
                .webUrl("http://web-url")
                .zookeeperConnection("http://zookeeper")
                .pods(Collections.singletonList(podSpec))
                .build();
    }
}
