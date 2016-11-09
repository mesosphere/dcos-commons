package org.apache.mesos.specification;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.offer.constrain.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * This class tests the DefaultServiceSpecification class.
 */
public class DefaultServiceSpecificationTest {
    private static final String SERVICE_NAME = "test-service-name";
    private static final PlacementRule HUGE_RULE = new AndRule(Arrays.asList(
            new MaxPerAttributeRule(3, StringMatcher.createAny()),
            new MaxPerAttributeRule(3, StringMatcher.createExact("foo"), StringMatcher.createRegex("index-.*")),
            new MaxPerAttributeRule(3, StringMatcher.createRegex(".+"), StringMatcher.createAny()),
            HostnameRule.avoid("avoidhost"),
            HostnameRule.require("requirehost1", "requirehost2"),
            AgentRule.require("requireagent1", "requireagent2"),
            AgentRule.avoid("avoidagent"),
            new AttributeRule(StringMatcher.createExact("hello")),
            new OrRule(Arrays.asList(
                    new MaxPerAttributeRule(3, StringMatcher.createExact("hi")),
                    HostnameRule.avoid("avoidhost1", "avoidhost2"),
                    HostnameRule.require("requirehost"),
                    AgentRule.require("requireagent"),
                    AgentRule.avoid("avoidagent1", "avoidagent2"),
                    TaskTypeRule.avoid("avoidme"),
                    new NotRule(TaskTypeRule.colocateWith("colocateme"))))));
    private static final List<TaskSet> TASK_SETS = Arrays.asList(
            TestTaskSetFactory.getTaskSet(Arrays.asList(
                    new DefaultConfigFileSpecification(
                            "../different/path/to/config",
                            "different path to a different template"),
                    new DefaultConfigFileSpecification(
                            "../relative/path/to/config2",
                            "this is a second config template")),
                    Optional.of(HUGE_RULE)));

    private DefaultServiceSpecification serviceSpecification;

    @Before
    public void beforeEach() {
        serviceSpecification = new DefaultServiceSpecification(SERVICE_NAME, TASK_SETS);
    }

    @Test
    public void testGetName() {
        Assert.assertEquals(SERVICE_NAME, serviceSpecification.getName());
    }

    @Test
    public void testGetTaskSpecifications() {
        Assert.assertEquals(TASK_SETS, serviceSpecification.getTaskSets());
    }

    @Test
    public void testSerializeDeserialize() throws Exception {
        Assert.assertEquals(ReflectionToStringBuilder.toString(serviceSpecification), serviceSpecification,
                DefaultServiceSpecification.getFactory(serviceSpecification, Collections.emptyList())
                        .parse(serviceSpecification.getBytes()));
    }
}
