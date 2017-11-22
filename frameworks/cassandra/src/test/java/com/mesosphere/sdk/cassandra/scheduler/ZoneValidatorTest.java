package com.mesosphere.sdk.cassandra.scheduler;

import com.mesosphere.sdk.config.validate.ConfigValidationError;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.testing.TestPodFactory;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

/**
 * This class tests the {@link ZoneValidator} class.
 */
public class ZoneValidatorTest {
    private static final ZoneValidator validator = new ZoneValidator();

    @Test
    public void noOldConfig() {
        Collection<ConfigValidationError> errors = validator.validate(Optional.empty(), null);
        Assert.assertTrue(errors.isEmpty());
    }

    @Test
    public void taskTypeMissingInOldSpec() {
        TaskSpec oldTaskSpec = getTaskSpec(TestConstants.TASK_NAME, Collections.emptyMap());
        TaskSpec newTaskSpec = getTaskSpec(Collections.emptyMap());
        ServiceSpec oldSpec = getServiceSpec(oldTaskSpec);
        ServiceSpec newSpec = getServiceSpec(newTaskSpec);

        Collection<ConfigValidationError> errors = validator.validate(Optional.of(oldSpec), newSpec);
        Assert.assertTrue(errors.isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void taskTypeMissingInNewSpec() {
        TaskSpec oldTaskSpec = getTaskSpec(Collections.emptyMap());
        TaskSpec newTaskSpec = getTaskSpec(TestConstants.TASK_NAME, Collections.emptyMap());
        ServiceSpec oldSpec = getServiceSpec(oldTaskSpec);
        ServiceSpec newSpec = getServiceSpec(newTaskSpec);

        validator.validate(Optional.of(oldSpec), newSpec);
    }

    @Test(expected = IllegalArgumentException.class)
    public void detectZonesMissingInNewSpec() {
        TaskSpec oldTaskSpec = getTaskSpec(Collections.emptyMap());
        TaskSpec newTaskSpec = getTaskSpec(Collections.emptyMap());

        ServiceSpec oldSpec = getServiceSpec(oldTaskSpec);
        ServiceSpec newSpec = getServiceSpec(newTaskSpec);

        validator.validate(Optional.of(oldSpec), newSpec);
    }

    @Test
    public void emptyToTrueShouldFail() {
        TaskSpec oldTaskSpec = getTaskSpec(Collections.emptyMap());

        ServiceSpec oldSpec = getServiceSpec(oldTaskSpec);
        ServiceSpec newSpec = getServiceSpec("true");

        Collection<ConfigValidationError> errors = validator.validate(Optional.of(oldSpec), newSpec);
        Assert.assertEquals(1, errors.size());
    }

    @Test
    public void blankToTrueShouldFail() {
        ServiceSpec oldSpec = getServiceSpec("");
        ServiceSpec newSpec = getServiceSpec("true");

        Collection<ConfigValidationError> errors = validator.validate(Optional.of(oldSpec), newSpec);
        Assert.assertEquals(1, errors.size());
    }

    @Test
    public void emptyToFalseShouldSucceed() {
        TaskSpec oldTaskSpec = getTaskSpec(Collections.emptyMap());

        ServiceSpec oldSpec = getServiceSpec(oldTaskSpec);
        ServiceSpec newSpec = getServiceSpec("false");

        Collection<ConfigValidationError> errors = validator.validate(Optional.of(oldSpec), newSpec);
        Assert.assertTrue(errors.isEmpty());
    }

    @Test
    public void blankToFalseShouldSucceed() {
        ServiceSpec oldSpec = getServiceSpec("");
        ServiceSpec newSpec = getServiceSpec("false");

        Collection<ConfigValidationError> errors = validator.validate(Optional.of(oldSpec), newSpec);
        Assert.assertTrue(errors.isEmpty());
    }

    @Test(expected = IllegalStateException.class)
    public void emptyToUnexpectedShouldThrow() {
        TaskSpec oldTaskSpec = getTaskSpec(Collections.emptyMap());

        ServiceSpec oldSpec = getServiceSpec(oldTaskSpec);
        ServiceSpec newSpec = getServiceSpec("foo");

        validator.validate(Optional.of(oldSpec), newSpec);
    }

    @Test
    public void trueToFalseShouldFail() {
        ServiceSpec oldSpec = getServiceSpec("true");
        ServiceSpec newSpec = getServiceSpec("false");

        Collection<ConfigValidationError> errors = validator.validate(Optional.of(oldSpec), newSpec);
        Assert.assertEquals(1, errors.size());
    }

    @Test
    public void falseToTrueShouldFail() {
        ServiceSpec oldSpec = getServiceSpec("false");
        ServiceSpec newSpec = getServiceSpec("true");

        Collection<ConfigValidationError> errors = validator.validate(Optional.of(oldSpec), newSpec);
        Assert.assertEquals(1, errors.size());
    }

    @Test
    public void trueToTrueShouldSucceed() {
        ServiceSpec oldSpec = getServiceSpec("true");
        ServiceSpec newSpec = getServiceSpec("true");

        Collection<ConfigValidationError> errors = validator.validate(Optional.of(oldSpec), newSpec);
        Assert.assertTrue(errors.isEmpty());
    }

    @Test
    public void envNullToTrueShouldFail() {
        Collection<ConfigValidationError> errors = validator.validateTransition(null, "true");
        Assert.assertEquals(1, errors.size());
    }

    @Test
    public void envBlankToTrueShouldFail() {
        Collection<ConfigValidationError> errors = validator.validateTransition("", "true");
        Assert.assertEquals(1, errors.size());
    }

    @Test
    public void envNullToFalseShouldSucceed() {
        Collection<ConfigValidationError> errors = validator.validateTransition(null, "false");
        Assert.assertTrue(errors.isEmpty());
    }

    @Test
    public void envBlankToFalseShouldSucceed() {
        Collection<ConfigValidationError> errors = validator.validateTransition("", "false");
        Assert.assertTrue(errors.isEmpty());
    }

    @Test
    public void envTrueToFalseShouldFail() {
        Collection<ConfigValidationError> errors = validator.validateTransition("true", "false");
        Assert.assertEquals(1, errors.size());
    }

    @Test
    public void envFalseToTrueShouldFail() {
        Collection<ConfigValidationError> errors = validator.validateTransition("false", "true");
        Assert.assertEquals(1, errors.size());
    }

    @Test
    public void envTrueToTrueShouldSucceed() {
        Collection<ConfigValidationError> errors = validator.validateTransition("true", "true");
        Assert.assertTrue(errors.isEmpty());
    }

    private ServiceSpec getServiceSpec(String detectZones) {
        Map<String, String> env = new HashMap<>();
        env.put(ZoneValidator.DETECT_ZONES_ENV, detectZones);
        TaskSpec taskSpec = getTaskSpec(env);
        return getServiceSpec(taskSpec);
    }

    private TaskSpec getTaskSpec(String name, Map<String, String> env) {
        return DefaultTaskSpec.newBuilder()
                .name(name)
                .goalState(GoalState.RUNNING)
                .resourceSet(
                        TestPodFactory.getResourceSet(
                                TestConstants.RESOURCE_SET_ID,
                                1.0,
                                256,
                                4096))
                .commandSpec(DefaultCommandSpec.newBuilder(Collections.emptyMap())
                        .value("./server")
                        .environment(env)
                        .build())
                .build();
    }

    private TaskSpec getTaskSpec(Map<String, String> env) {
        return getTaskSpec(ZoneValidator.TASK_NAME, env);
    }

    private PodSpec getPodSpec(TaskSpec taskSpec) {
        return TestPodFactory.getPodSpec(
                ZoneValidator.POD_TYPE,
                TestConstants.SERVICE_USER,
                1,
                Arrays.asList(taskSpec));
    }

    private ServiceSpec getServiceSpec(TaskSpec taskSpec) {
       return getServiceSpec(getPodSpec(taskSpec));
    }

    private ServiceSpec getServiceSpec(PodSpec podSpec) {
        return new DefaultServiceSpec(
                TestConstants.SERVICE_NAME,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                "http://web-url",
                "http://zookeeper",
                Arrays.asList(podSpec),
                null,
                TestConstants.SERVICE_USER);
    }
}
