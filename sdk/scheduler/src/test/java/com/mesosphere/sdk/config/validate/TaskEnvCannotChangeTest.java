package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.config.validate.TaskEnvCannotChange.Rule;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.specification.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import java.util.*;

import org.mockito.*;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link TaskEnvCannotChange}.
 */
public class TaskEnvCannotChangeTest {
    private static final String POD_TYPE = "pod";
    private static final String TASK_NAME = "task";
    private static final String ENV_NAME = "SOME_ENV";

    private ServiceSpec serviceEnvUnset;
    private ServiceSpec serviceEnvEmpty;
    private ServiceSpec serviceEnvValue;
    private ServiceSpec serviceEnvValue2;

    private static final ConfigValidator<ServiceSpec> VALIDATOR_NO_RULES =
            new TaskEnvCannotChange(POD_TYPE, TASK_NAME, ENV_NAME);
    private static final ConfigValidator<ServiceSpec> VALIDATOR_UNSET_TO_SET =
            new TaskEnvCannotChange(POD_TYPE, TASK_NAME, ENV_NAME, Rule.ALLOW_UNSET_TO_SET);
    private static final ConfigValidator<ServiceSpec> VALIDATOR_SET_TO_UNSET =
            new TaskEnvCannotChange(POD_TYPE, TASK_NAME, ENV_NAME, Rule.ALLOW_SET_TO_UNSET);
    private static final ConfigValidator<ServiceSpec> VALIDATOR_SET_TO_UNSET_TO_SET =
            new TaskEnvCannotChange(POD_TYPE, TASK_NAME, ENV_NAME, Rule.ALLOW_UNSET_TO_SET, Rule.ALLOW_SET_TO_UNSET);

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        serviceEnvUnset = specWithEnv(null);
        serviceEnvEmpty = specWithEnv("");
        serviceEnvValue = specWithEnv("val");
        serviceEnvValue2 = specWithEnv("val2");
    }

    private static ServiceSpec specWithEnv(String val) {
        // Use mocks to avoid *Spec validation hell
        CommandSpec mockCommand = mock(CommandSpec.class);
        when(mockCommand.getEnvironment()).thenReturn(
                val == null ? Collections.emptyMap() : Collections.singletonMap(ENV_NAME, val));

        TaskSpec mockTask = mock(TaskSpec.class);
        when(mockTask.getName()).thenReturn(TASK_NAME);
        when(mockTask.getCommand()).thenReturn(Optional.of(mockCommand));

        PodSpec mockPod = mock(PodSpec.class);
        when(mockPod.getType()).thenReturn(POD_TYPE);
        when(mockPod.getTasks()).thenReturn(Arrays.asList(mockTask));

        ServiceSpec mockService = mock(ServiceSpec.class);
        when(mockService.getPods()).thenReturn(Arrays.asList(mockPod));
        return mockService;
    }

    @Test
    public void testNoRules() throws InvalidRequirementException {
        Assert.assertEquals(0, VALIDATOR_NO_RULES.validate(Optional.empty(), serviceEnvUnset).size());
        Assert.assertEquals(0, VALIDATOR_NO_RULES.validate(Optional.empty(), serviceEnvEmpty).size());
        Assert.assertEquals(0, VALIDATOR_NO_RULES.validate(Optional.empty(), serviceEnvValue).size());

        Assert.assertEquals(0, VALIDATOR_NO_RULES.validate(Optional.of(serviceEnvUnset), serviceEnvUnset).size());
        Assert.assertEquals(0, VALIDATOR_NO_RULES.validate(Optional.of(serviceEnvUnset), serviceEnvEmpty).size());
        Assert.assertEquals(1, VALIDATOR_NO_RULES.validate(Optional.of(serviceEnvUnset), serviceEnvValue).size());

        Assert.assertEquals(0, VALIDATOR_NO_RULES.validate(Optional.of(serviceEnvEmpty), serviceEnvUnset).size());
        Assert.assertEquals(0, VALIDATOR_NO_RULES.validate(Optional.of(serviceEnvEmpty), serviceEnvEmpty).size());
        Assert.assertEquals(1, VALIDATOR_NO_RULES.validate(Optional.of(serviceEnvEmpty), serviceEnvValue).size());

        Assert.assertEquals(1, VALIDATOR_NO_RULES.validate(Optional.of(serviceEnvValue), serviceEnvUnset).size());
        Assert.assertEquals(1, VALIDATOR_NO_RULES.validate(Optional.of(serviceEnvValue), serviceEnvEmpty).size());
        Assert.assertEquals(0, VALIDATOR_NO_RULES.validate(Optional.of(serviceEnvValue), serviceEnvValue).size());

        Assert.assertEquals(1, VALIDATOR_NO_RULES.validate(Optional.of(serviceEnvValue), serviceEnvValue2).size());
        Assert.assertEquals(1, VALIDATOR_NO_RULES.validate(Optional.of(serviceEnvValue2), serviceEnvValue).size());
    }

    @Test
    public void testAllowUnsetToSet() throws InvalidRequirementException {
        Assert.assertEquals(0, VALIDATOR_UNSET_TO_SET.validate(Optional.empty(), serviceEnvUnset).size());
        Assert.assertEquals(0, VALIDATOR_UNSET_TO_SET.validate(Optional.empty(), serviceEnvEmpty).size());
        Assert.assertEquals(0, VALIDATOR_UNSET_TO_SET.validate(Optional.empty(), serviceEnvValue).size());

        Assert.assertEquals(0, VALIDATOR_UNSET_TO_SET.validate(Optional.of(serviceEnvUnset), serviceEnvUnset).size());
        Assert.assertEquals(0, VALIDATOR_UNSET_TO_SET.validate(Optional.of(serviceEnvUnset), serviceEnvEmpty).size());
        Assert.assertEquals(0, VALIDATOR_UNSET_TO_SET.validate(Optional.of(serviceEnvUnset), serviceEnvValue).size());

        Assert.assertEquals(0, VALIDATOR_UNSET_TO_SET.validate(Optional.of(serviceEnvEmpty), serviceEnvUnset).size());
        Assert.assertEquals(0, VALIDATOR_UNSET_TO_SET.validate(Optional.of(serviceEnvEmpty), serviceEnvEmpty).size());
        Assert.assertEquals(0, VALIDATOR_UNSET_TO_SET.validate(Optional.of(serviceEnvEmpty), serviceEnvValue).size());

        Assert.assertEquals(1, VALIDATOR_UNSET_TO_SET.validate(Optional.of(serviceEnvValue), serviceEnvUnset).size());
        Assert.assertEquals(1, VALIDATOR_UNSET_TO_SET.validate(Optional.of(serviceEnvValue), serviceEnvEmpty).size());
        Assert.assertEquals(0, VALIDATOR_UNSET_TO_SET.validate(Optional.of(serviceEnvValue), serviceEnvValue).size());

        Assert.assertEquals(1, VALIDATOR_UNSET_TO_SET.validate(Optional.of(serviceEnvValue), serviceEnvValue2).size());
        Assert.assertEquals(1, VALIDATOR_UNSET_TO_SET.validate(Optional.of(serviceEnvValue2), serviceEnvValue).size());
    }

    @Test
    public void testAllowSetToUnset() throws InvalidRequirementException {
        Assert.assertEquals(0, VALIDATOR_SET_TO_UNSET.validate(Optional.empty(), serviceEnvUnset).size());
        Assert.assertEquals(0, VALIDATOR_SET_TO_UNSET.validate(Optional.empty(), serviceEnvEmpty).size());
        Assert.assertEquals(0, VALIDATOR_SET_TO_UNSET.validate(Optional.empty(), serviceEnvValue).size());

        Assert.assertEquals(0, VALIDATOR_SET_TO_UNSET.validate(Optional.of(serviceEnvUnset), serviceEnvUnset).size());
        Assert.assertEquals(0, VALIDATOR_SET_TO_UNSET.validate(Optional.of(serviceEnvUnset), serviceEnvEmpty).size());
        Assert.assertEquals(1, VALIDATOR_SET_TO_UNSET.validate(Optional.of(serviceEnvUnset), serviceEnvValue).size());

        Assert.assertEquals(0, VALIDATOR_SET_TO_UNSET.validate(Optional.of(serviceEnvEmpty), serviceEnvUnset).size());
        Assert.assertEquals(0, VALIDATOR_SET_TO_UNSET.validate(Optional.of(serviceEnvEmpty), serviceEnvEmpty).size());
        Assert.assertEquals(1, VALIDATOR_SET_TO_UNSET.validate(Optional.of(serviceEnvEmpty), serviceEnvValue).size());

        Assert.assertEquals(0, VALIDATOR_SET_TO_UNSET.validate(Optional.of(serviceEnvValue), serviceEnvUnset).size());
        Assert.assertEquals(0, VALIDATOR_SET_TO_UNSET.validate(Optional.of(serviceEnvValue), serviceEnvEmpty).size());
        Assert.assertEquals(0, VALIDATOR_SET_TO_UNSET.validate(Optional.of(serviceEnvValue), serviceEnvValue).size());

        Assert.assertEquals(1, VALIDATOR_SET_TO_UNSET.validate(Optional.of(serviceEnvValue), serviceEnvValue2).size());
        Assert.assertEquals(1, VALIDATOR_SET_TO_UNSET.validate(Optional.of(serviceEnvValue2), serviceEnvValue).size());
    }

    @Test
    public void testAllowSetToUnset_UnsetToSet() throws InvalidRequirementException {
        Assert.assertEquals(0, VALIDATOR_SET_TO_UNSET_TO_SET.validate(Optional.empty(), serviceEnvUnset).size());
        Assert.assertEquals(0, VALIDATOR_SET_TO_UNSET_TO_SET.validate(Optional.empty(), serviceEnvEmpty).size());
        Assert.assertEquals(0, VALIDATOR_SET_TO_UNSET_TO_SET.validate(Optional.empty(), serviceEnvValue).size());

        Assert.assertEquals(0, VALIDATOR_SET_TO_UNSET_TO_SET.validate(Optional.of(serviceEnvUnset), serviceEnvUnset).size());
        Assert.assertEquals(0, VALIDATOR_SET_TO_UNSET_TO_SET.validate(Optional.of(serviceEnvUnset), serviceEnvEmpty).size());
        Assert.assertEquals(0, VALIDATOR_SET_TO_UNSET_TO_SET.validate(Optional.of(serviceEnvUnset), serviceEnvValue).size());

        Assert.assertEquals(0, VALIDATOR_SET_TO_UNSET_TO_SET.validate(Optional.of(serviceEnvEmpty), serviceEnvUnset).size());
        Assert.assertEquals(0, VALIDATOR_SET_TO_UNSET_TO_SET.validate(Optional.of(serviceEnvEmpty), serviceEnvEmpty).size());
        Assert.assertEquals(0, VALIDATOR_SET_TO_UNSET_TO_SET.validate(Optional.of(serviceEnvEmpty), serviceEnvValue).size());

        Assert.assertEquals(0, VALIDATOR_SET_TO_UNSET_TO_SET.validate(Optional.of(serviceEnvValue), serviceEnvUnset).size());
        Assert.assertEquals(0, VALIDATOR_SET_TO_UNSET_TO_SET.validate(Optional.of(serviceEnvValue), serviceEnvEmpty).size());
        Assert.assertEquals(0, VALIDATOR_SET_TO_UNSET_TO_SET.validate(Optional.of(serviceEnvValue), serviceEnvValue).size());

        Assert.assertEquals(1, VALIDATOR_SET_TO_UNSET_TO_SET.validate(Optional.of(serviceEnvValue), serviceEnvValue2).size());
        Assert.assertEquals(1, VALIDATOR_SET_TO_UNSET_TO_SET.validate(Optional.of(serviceEnvValue2), serviceEnvValue).size());
    }
}
