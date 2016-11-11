package org.apache.mesos.offer.constrain;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.offer.AttributeStringUtils;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.TaskUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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
 *  allow rack:3 only, unless we know that there's >3 racks via the attribute_count parameter
 *
 * Example:
 *  attribute |     tasks
 * -----------+---------------
 *    rack:1  | a-1, b-1, c-1
 *    rack:2  | a-2, c-2, c-3
 *    rack:3  | b-2, c-4, b-3
 * Result:
 *  allow any of rack:1/rack:2/rack:3, unless we know that there's >3 racks via the attribute_count
 *  parameter.
 */
public class RoundRobinByAttributeRule implements PlacementRule {

    private final String attributeName;
    private final Optional<Integer> attributeValueCountOptional;
    private final StringMatcher taskFilter;

    public RoundRobinByAttributeRule(String attribute, Optional<Integer> attributeValueCount) {
        this(attribute, attributeValueCount, null);
    }

    @JsonCreator
    public RoundRobinByAttributeRule(
            @JsonProperty("name") String attributeName,
            @JsonProperty("value_count") Optional<Integer> attributeValueCount,
            @JsonProperty("task_filter") StringMatcher taskFilter) {
        this.attributeName = attributeName;
        this.attributeValueCountOptional = attributeValueCount;
        if (taskFilter == null) { // null when unspecified in serialized data
            taskFilter = AnyMatcher.create();
        }
        this.taskFilter = taskFilter;
    }

    @Override
    public Offer filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks) {
        final String offerValue = getAttributeValue(offer, attributeName);
        if (offerValue == null) {
            // offer doesn't have the required attribute at all. denied.
            return offer.toBuilder().clearResources().build();
        }

        // search across tasks, keeping counts on a per-hostname basis.
        // attribute value (for selected attribute name) => # of instances on attribute value
        Map<String, Integer> attributeValueCounts = new HashMap<>();
        for (TaskInfo task : tasks) {
            // only tally tasks which match the task matcher (eg 'index-.*')
            if (!taskFilter.match(task.getName())) {
                continue;
            }
            if (PlacementUtils.areEquivalent(task, offerRequirement)) {
                // This is stale data for the same task that we're currently evaluating for
                // placement. Don't worry about counting its usage. This occurs when we're
                // redeploying a given task with a new configuration (old data not deleted yet).
                continue;
            }

            final String taskAttributeValue = getAttributeValue(task, attributeName);
            if (taskAttributeValue == null) {
                // no attribute matching the name was found. ignore.
                continue;
            }
            Integer value = attributeValueCounts.get(taskAttributeValue);
            attributeValueCounts.put(taskAttributeValue, (value == null) ? 1 : value + 1);
        }

        int maxValueCount = 0;
        int minValueCount = Integer.MAX_VALUE;
        for (Integer count : attributeValueCounts.values()) {
            if (count > maxValueCount) {
                maxValueCount = count;
            }
            if (count < minValueCount) {
                minValueCount = count;
            }
        }
        if (minValueCount == Integer.MAX_VALUE) {
            minValueCount = 0;
        }
        Integer offerValueCount = attributeValueCounts.get(offerValue);
        if (offerValueCount == null) {
            offerValueCount = 0;
        }

        if (minValueCount == maxValueCount || offerValueCount < maxValueCount) {
            // all (known) attribute values are full at the current level,
            // or this offer's value is not full at the current level
            if (attributeValueCountOptional.isPresent()
                    && attributeValueCounts.size() < attributeValueCountOptional.get()) {
                // we know that there are other attribute values out there which have nothing on them at all.
                // only launch here if this value also has nothing on it.
                if (maxValueCount == 0) {
                    return offer;
                } else {
                    return offer.toBuilder().clearResources().build();
                }
            } else {
                // either we don't know how many attribute values are out there, or we know that no values are
                // currently missing from our counts, but in either case this value has fewer instances
                // than some other value in the system.
                return offer;
            }
        } else {
            // this attribute value is full at the current level, but other (known) values are not full yet.
            return offer.toBuilder().clearResources().build();
        }
    }

    @JsonProperty("name")
    private String getAttributeName() {
        return attributeName;
    }

    @JsonProperty("value_count")
    private Optional<Integer> getAttributeValueCount() {
        return attributeValueCountOptional;
    }

    @JsonProperty("task_filter")
    private StringMatcher getTaskFilter() {
        return taskFilter;
    }

    @Override
    public String toString() {
        return String.format("RoundRobinByAttributeRule{attribute=%s, attribute_count=%s, task_filter=%s}",
                attributeName, attributeValueCountOptional, taskFilter);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    private static String getAttributeValue(TaskInfo task, String attributeName) {
        for (String taskAttributeString : TaskUtils.getOfferAttributeStrings(task)) {
            AttributeStringUtils.NameValue taskAttributeNameValue =
                    AttributeStringUtils.split(taskAttributeString);
            if (taskAttributeNameValue.name.equalsIgnoreCase(attributeName)) {
                return taskAttributeNameValue.value;
            }
        }
        return null;
    }

    private static String getAttributeValue(Offer offer, String attributeName) {
        for (Attribute attribute : offer.getAttributesList()) {
            if (attribute.getName().equalsIgnoreCase(attributeName)) {
                return AttributeStringUtils.valueString(attribute);
            }
        }
        return null;
    }
}
