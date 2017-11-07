package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.Value;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

/**
 * Tests for {@link MaxPerAttributeRule}.
 */
public class MaxPerAttributeRuleTest {
    private static final String ATTR_PATTERN = "^footext:.*$";
    private static final StringMatcher ATTR_MATCHER = RegexMatcher.create(ATTR_PATTERN);

    private static final Offer OFFER_NO_ATTRS = getOfferWithResources();
    private static final Offer OFFER_ATTR_MATCH_1;
    static {
        Attribute.Builder a = Attribute.newBuilder()
                .setType(Value.Type.TEXT)
                .setName("footext");
        a.getTextBuilder().setValue("123");
        OFFER_ATTR_MATCH_1 = OFFER_NO_ATTRS.toBuilder().addAttributes(a.build()).build();
    }

    private static final TaskInfo TASK_ATTR_MATCH_1 = getTask("match-1__abc", OFFER_ATTR_MATCH_1);

    private static TaskInfo getTask(String id, Offer offer) {
        TaskInfo.Builder taskBuilder = TaskTestUtils.getTaskInfo(Collections.emptyList()).toBuilder();
        taskBuilder.getTaskIdBuilder().setValue(id);
        try {
            taskBuilder.setName(CommonIdUtils.toTaskName(taskBuilder.getTaskId()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        taskBuilder.setLabels(new TaskLabelWriter(taskBuilder).setOfferAttributes(offer).toProto());
        return taskBuilder.build();
    }

    private static Offer getOfferWithResources() {
        Offer.Builder o = OfferTestUtils.getEmptyOfferBuilder();
        OfferTestUtils.addResource(o, "a");
        OfferTestUtils.addResource(o, "b");
        return o.build();
    }

    @Test
    public void getAllTaskKeys() {
        MaxPerRule rule = new MaxPerAttributeRule(2, AnyMatcher.create());

        Assert.assertEquals(1, rule.getKeys(TASK_ATTR_MATCH_1).size());
        Assert.assertEquals("footext:123", rule.getKeys(TASK_ATTR_MATCH_1).stream().findFirst().get());
    }

    @Test
    public void getTaskKeysMatchNoAttributes() {
        final StringMatcher matcher = RegexMatcher.create("banana");
        MaxPerRule rule = new MaxPerAttributeRule(2, matcher);

        Assert.assertEquals(0, rule.getKeys(TASK_ATTR_MATCH_1).size());
    }

    @Test
    public void getTaskKeysMatchNoTasks() {
        final StringMatcher attributeMatcher = AnyMatcher.create();
        final StringMatcher taskMatcher = RegexMatcher.create("banana");
        MaxPerRule rule = new MaxPerAttributeRule(2, attributeMatcher, taskMatcher);

        Assert.assertEquals(0, rule.getKeys(TestConstants.TASK_INFO).size());
    }

    @Test
    public void getAllOfferKeys() {
        final StringMatcher matcher = AnyMatcher.create();
        MaxPerRule rule = new MaxPerAttributeRule(2, matcher);

        Assert.assertEquals(1, rule.getKeys(OFFER_ATTR_MATCH_1).size());
        Assert.assertEquals("footext:123", rule.getKeys(OFFER_ATTR_MATCH_1).stream().findFirst().get());
    }

    @Test
    public void getOfferKeysMatchNoAttributes() {
        final StringMatcher matcher = RegexMatcher.create("banana");
        MaxPerRule rule = new MaxPerAttributeRule(2, matcher);

        Assert.assertEquals(0, rule.getKeys(OFFER_ATTR_MATCH_1).size());
    }

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRule rule = new MaxPerAttributeRule(2, ATTR_MATCHER);
        Assert.assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));
    }

    @Test
    public void testDeserializeNoOptionalTaskFilter() throws IOException {
        String str = "{ '@type': 'MaxPerAttributeRule', 'max': 2, 'matcher': { '@type': 'AnyMatcher' } }".replace('\'', '"');
        SerializationUtils.fromString(str,
                PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER);
    }

    @Test
    public void testDeserializeAllParams() throws IOException {
        String str = "{ '@type': 'MaxPerAttributeRule', 'max': 2, 'matcher': { '@type': 'AnyMatcher' }, 'task-filter': { '@type': 'ExactMatcher', 'string': 'foo' } }".replace('\'', '"');
        SerializationUtils.fromString(str,
                PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER);
    }
}
