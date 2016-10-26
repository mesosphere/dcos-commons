package org.apache.mesos.offer.constrain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.offer.TaskUtils;

/**
 * Ensures that the given Offer’s attributes each have no more than N instances of the task type
 * running on them.
 *
 * For example, this can ensure that no more than N tasks are running against the 'rack:foo'
 * attribute (exact match), or it can ensure that no distinct 'rack:.*' value has more than N tasks
 * running against it (wildcarded grouping).
 *
 * To illustrate, let's look at a deployment scenario of 5 agents with 3 distinct 'rack' values:
 *  agent |  attr  | # tasks
 * -------+--------+---------
 *    1   | rack:a |   3
 *    2   | rack:b |   2
 *    3   | rack:c |   1
 *    4   | rack:a |   2
 *    5   | rack:b |   2
 *
 * Given a {@link MaxPerAttributeGenerator} with a limit of 5 and a regex of 'rack:.*', let's see
 * what PlacementRule would be produced:
 *
 * In this example, the regex of 'rack:.*' will result in grouping the task counts as follows:
 * - rack:a: 5 tasks
 * - rack:b: 4 tasks
 * - rack:c: 1 task
 *
 * With the limit value of 5, this would result in a PlacementRule that blocks any offers with
 * 'rack:a' from future deployments.
 */
public class MaxPerAttributeGenerator implements PlacementRuleGenerator {

    private final int maxTasksPerSelectedAttribute;
    private final AttributeSelector attributeSelector;

    /**
     * Creates a new rule generator which will block deployment on tasks which already have N
     * instances running against a specified attribute.
     */
    public MaxPerAttributeGenerator(
            int maxTasksPerSelectedAttribute, AttributeSelector attributeSelector) {
        this.maxTasksPerSelectedAttribute = maxTasksPerSelectedAttribute;
        this.attributeSelector = attributeSelector;
    }

    @Override
    public PlacementRule generate(Collection<TaskInfo> tasks) {
        // map: enforced attribute => # tasks which were launched against attribute
        Map<String, Integer> selectedAttrTaskCounts = new HashMap<>();
        for (TaskInfo task : tasks) {
            for (String attribute : TaskUtils.getOfferAttributeStrings(task)) {
                // only enforce attribute(s) that match (eg 'rack:.*'):
                if (!attributeSelector.select(attribute)) {
                    continue;
                }
                // increment the count for this exact attribute (eg 'rack:9'):
                Integer val = selectedAttrTaskCounts.get(attribute);
                if (val == null) {
                    val = 0;
                }
                val++;
                selectedAttrTaskCounts.put(attribute, val);
            }
        }
        // find the attributes which meet or exceed the limit and block any offers with those
        // (EXACT) attributes from the next launch.
        List<PlacementRule> blockedAttributes = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : selectedAttrTaskCounts.entrySet()) {
            if (entry.getValue() >= maxTasksPerSelectedAttribute) {
                blockedAttributes.add(
                        new AttributeRule(AttributeSelector.createStringSelector(entry.getKey())));
            }
        }
        switch (blockedAttributes.size()) {
        case 0:
            // nothing is full, don't filter any offers.
            return new PassthroughRule(
                    String.format("no matching attributes are full: %s", attributeSelector));
        case 1:
            // shortcut: no OrRule needed, just block the one attribute
            return new NotRule(blockedAttributes.get(0));
        default:
            // filter out any offers which contain the full attributes.
            return new NotRule(new OrRule(blockedAttributes));
        }
    }
}
