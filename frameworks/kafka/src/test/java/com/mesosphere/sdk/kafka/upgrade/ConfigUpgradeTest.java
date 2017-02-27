package com.mesosphere.sdk.kafka.upgrade;

import org.junit.Assert;
import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigUpgradeTest {
    @Test
    public  void testBrokerNameChange() throws  Exception {
        String oldName = "broker-2";
        Pattern pattern = Pattern.compile("(.*)(\\d+)");
        Matcher matcher = pattern.matcher(oldName);
        Assert.assertTrue(matcher.find());
        Assert.assertEquals("broker-", matcher.group(1));
        String intString = matcher.group(2);
        int brokerID = Integer.parseInt(intString);
        String newName = "kafka-" + brokerID + "-broker"; //kafka-2-broker
        Assert.assertTrue(newName.equals("kafka-2-broker"));
    }
}
