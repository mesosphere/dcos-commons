package com.mesosphere.sdk.offer.evaluate.placement;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.taskdata.AttributeStringUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;

import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

/**
 * Implements logic for Marathon's GROUP_BY operator for attribute values. Ensures that tasks are
 * evenly distributed across distinct attribute values in the system as they are rolled out.
 *
 * Example:
 *  attribute |     tasks
 * -----------+---------------
 *    rack:1  | a-1, b-1, c-1
 *    rack:2  | a-2, c-2, c-3
 *    rack:3  | b-2, c-4
 * Result:
 *  allow rack:3 only, unless we know that there's >3 racks via the attribute-count parameter
 *
 * Example:
 *  attribute |     tasks
 * -----------+---------------
 *    rack:1  | a-1, b-1, c-1
 *    rack:2  | a-2, c-2, c-3
 *    rack:3  | b-2, c-4, b-3
 * Result:
 *  allow any of rack:1/rack:2/rack:3, unless we know that there's >3 racks via the attribute-count
 *  parameter.
 *
 * This enforcement is applied by task name. By default the rule will only count e.g. tasks named
 * 'index-.*'. This allows us to only enforce the rule against certain task types or task instances
 * within the service.
 */
public class RoundRobinByAttributeRule extends AbstractRoundRobinRule {

    private final String attributeName;

    public RoundRobinByAttributeRule(String attribute, Optional<Integer> attributeValueCount) {
        this(attribute, attributeValueCount, null);
    }

    @JsonCreator
    public RoundRobinByAttributeRule(
            @JsonProperty("name") String attributeName,
            @JsonProperty("value-count") Optional<Integer> attributeValueCount,
            @JsonProperty("task-filter") StringMatcher taskFilter) {
        super(taskFilter, attributeValueCount);
        this.attributeName = attributeName;
    }

    @Override
    protected String getKey(Offer offer) {
        for (Attribute attribute : offer.getAttributesList()) {
            if (attribute.getName().equalsIgnoreCase(attributeName)) {
                return AttributeStringUtils.valueString(attribute);
            }
        }
        return null;
    }

    @Override
    protected String getKey(TaskInfo task) {
        for (String taskAttributeString : new TaskLabelReader(task).getOfferAttributeStrings()) {
            AttributeStringUtils.NameValue taskAttributeNameValue =
                    AttributeStringUtils.split(taskAttributeString);
            if (taskAttributeNameValue.name.equalsIgnoreCase(attributeName)) {
                return taskAttributeNameValue.value;
            }
        }
        return null;
    }

    @JsonProperty("name")
    private String getAttributeName() {
        return attributeName;
    }

    @JsonProperty("value-count")
    private Optional<Integer> getAttributeValueCount() {
        return distinctKeyCount;
    }

    @Override
    public String toString() {
        return String.format("RoundRobinByAttributeRule{attribute=%s, attribute-count=%s, task-filter=%s}",
                attributeName, distinctKeyCount, taskFilter);
    }

    @Override
    public Collection<PlacementField> getPlacementFields() {
        return Arrays.asList(PlacementField.ATTRIBUTE);
    }
}
