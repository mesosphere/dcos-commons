package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.Value;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

/**
 * Tests for {@link MaxPerHostnameRule}.
 */
public class MaxPerHostnameRuleTest {
    private static final String HOSTNAME_1 = "www.hostname.1";

    private static final Offer OFFER_HOSTNAME;
    static {
        Attribute.Builder a = Attribute.newBuilder()
                .setType(Value.Type.TEXT)
                .setName("footext");
        a.getTextBuilder().setValue("123");
        OFFER_HOSTNAME = getOfferWithResources().toBuilder().setHostname(HOSTNAME_1).build();
    }

    private static final TaskInfo TASK_NO_HOSTNAME = TaskTestUtils.getTaskInfo(Collections.emptyList());
    private static final TaskInfo TASK_HOSTNAME = getTask("match-1__abc", OFFER_HOSTNAME);

    private static TaskInfo getTask(String id, Offer offer) {
        TaskInfo.Builder taskBuilder = TaskTestUtils.getTaskInfo(Collections.emptyList()).toBuilder();
        taskBuilder.getTaskIdBuilder().setValue(id);
        try {
            taskBuilder.setName(CommonIdUtils.toTaskName(taskBuilder.getTaskId()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        taskBuilder.setLabels(new TaskLabelWriter(taskBuilder).setHostname(offer).toProto());
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
        MaxPerRule rule = new MaxPerHostnameRule(2, AnyMatcher.create());
        Assert.assertEquals(1, rule.getKeys(TASK_HOSTNAME).size());
        Assert.assertEquals(HOSTNAME_1, rule.getKeys(TASK_HOSTNAME).stream().findFirst().get());
    }

    @Test
    public void getTaskKeysNoHostname() {
        MaxPerRule rule = new MaxPerHostnameRule(2, AnyMatcher.create());
        Assert.assertEquals(0, rule.getKeys(TASK_NO_HOSTNAME).size());
    }

    @Test
    public void getOfferKeys() {
        MaxPerRule rule = new MaxPerHostnameRule(2, AnyMatcher.create());
        Assert.assertEquals(1, rule.getKeys(OFFER_HOSTNAME).size());
        Assert.assertEquals(HOSTNAME_1, rule.getKeys(OFFER_HOSTNAME).stream().findFirst().get());
    }

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRule rule = new MaxPerHostnameRule(2);
        Assert.assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));
    }

    @Test
    public void testDeserializeNoOptionalTaskFilter() throws IOException {
        String str = "{ '@type': 'MaxPerHostnameRule', 'max': 2 }".replace('\'', '"');
        SerializationUtils.fromString(str,
                PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER);
    }

    @Test
    public void testDeserializeAllParams() throws IOException {
        String str = "{ '@type': 'MaxPerHostnameRule', 'max': 2, 'task-filter': { '@type': 'ExactMatcher', 'string': 'foo' } }".replace('\'', '"');
        SerializationUtils.fromString(str,
                PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER);
    }
}
