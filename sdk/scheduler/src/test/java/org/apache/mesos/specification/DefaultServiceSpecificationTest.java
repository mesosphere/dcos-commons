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
            new MaxPerAttributeRule(3, AnyMatcher.create()),
            new MaxPerAttributeRule(3, ExactMatcher.create("foo"), RegexMatcher.create("index-.*")),
            new MaxPerAttributeRule(3, RegexMatcher.create(".+"), AnyMatcher.create()),
            new MaxPerHostnameRule(-1, ExactMatcher.create("bar")),
            new MaxPerHostnameRule(8),
            new PassthroughRule(),
            HostnameRule.avoid(ExactMatcher.create("avoidhost")),
            HostnameRule.require(RegexMatcher.create("requirehost1"), AnyMatcher.create()),
            AgentRule.require("requireagent1", "requireagent2"),
            AgentRule.avoid("avoidagent"),
            AttributeRule.require(ExactMatcher.create("hello")),
            new OrRule(Arrays.asList(
                    new MaxPerAttributeRule(3, ExactMatcher.create("hi")),
                    HostnameRule.avoid(RegexMatcher.create("avoidhost1"), ExactMatcher.create("avoidhost2")),
                    HostnameRule.require(AnyMatcher.create()),
                    new RoundRobinByAttributeRule("foo", Optional.of(3)),
                    new RoundRobinByHostnameRule(Optional.of(3)),
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
