package org.apache.mesos.specification;

//import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
//import org.apache.mesos.offer.constrain.*;
//import org.junit.Assert;
//import org.junit.Before;
//import org.junit.Test;
//
//import java.util.Arrays;
//import java.util.List;
//import java.util.Optional;
//
///**
// * This class tests the DefaultServiceSpecification class.
// */
//public class DefaultServiceSpecificationTest {
//    private static final String SERVICE_NAME = "test-service-name";
//    private static final PlacementRuleGenerator HUGE_GENERATOR = new AndRule.Generator(Arrays.asList(
//            new MaxPerAttributeGenerator(3, AttributeSelector.createRegexSelector(".+")),
//            new HostnameRule.AvoidHostnameGenerator("avoidhost"),
//            new HostnameRule.RequireHostnamesGenerator("requirehost1", "requirehost2"),
//            new AgentRule.RequireAgentsGenerator("requireagent1", "requireagent2"),
//            new AgentRule.AvoidAgentGenerator("avoidagent"),
//            new AttributeRule.Generator(AttributeSelector.createStringSelector("hello")),
//            new OrRule.Generator(Arrays.asList(
//                    new MaxPerAttributeGenerator(3, AttributeSelector.createStringSelector("hi")),
//                    new HostnameRule.AvoidHostnamesGenerator("avoidhost1", "avoidhost2"),
//                    new HostnameRule.RequireHostnameGenerator("requirehost"),
//                    new AgentRule.RequireAgentGenerator("requireagent"),
//                    new AgentRule.AvoidAgentsGenerator("avoidagent1", "avoidagent2"),
//                    TaskTypeGenerator.createAvoid("avoidme"),
//                    new NotRule.Generator(TaskTypeGenerator.createColocate("colocateme"))))));
//    private static final List<TaskSet> TASK_SETS = Arrays.asList(
//            TestTaskSetFactory.getTaskSet(Arrays.asList(
//                    new DefaultConfigFileSpecification(
//                            "../different/path/to/config",
//                            "different path to a different template"),
//                    new DefaultConfigFileSpecification(
//                            "../relative/path/to/config2",
//                            "this is a second config template")),
//                    Optional.of(HUGE_GENERATOR)));
//
//    private DefaultServiceSpecification serviceSpecification;
//
//    @Before
//    public void beforeEach() {
//        serviceSpecification = new DefaultServiceSpecification(SERVICE_NAME, TASK_SETS);
//    }
//
//    @Test
//    public void testGetName() {
//        Assert.assertEquals(SERVICE_NAME, serviceSpecification.getName());
//    }
//
//    @Test
//    public void testGetTaskSpecifications() {
//        Assert.assertEquals(TASK_SETS, serviceSpecification.getTaskSets());
//    }
//
//    @Test
//    public void testSerializeDeserialize() throws Exception {
//        Assert.assertEquals(ReflectionToStringBuilder.toString(serviceSpecification), serviceSpecification,
//                DefaultServiceSpecification.getFactoryInstance().parse(serviceSpecification.getBytes()));
//    }
//}
