package com.mesosphere.sdk.kafka.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServiceSpecTest extends BaseServiceSpecTest {
    @BeforeClass
    public static void beforeAll() {
        ENV_VARS.set("FRAMEWORK_NAME", "kafka");
        ENV_VARS.set("FRAMEWORK_PRINCIPLE","kafka-principle");
        ENV_VARS.set("MESOS_ZOOKEEPER_URI","master.mesos:2811");
        ENV_VARS.set("CONFIG_TEMPLATE_PATH","frameworks/kafka");
        ENV_VARS.set("KAFKA_URI", "some_uri");
        ENV_VARS.set("BOOTSTRAP_URI", "another_uri");
        ENV_VARS.set("PLACEMENT_CONSTRAINTS","");
        ENV_VARS.set("PHASE_STRATEGY","serial");
        ENV_VARS.set("KAFKA_VERSION_PATH","somepath");
        ENV_VARS.set("PORT_API", "8080");
        ENV_VARS.set("BROKER_COUNT", "2");
        ENV_VARS.set("BROKER_CPUS", "0.1");
        ENV_VARS.set("BROKER_MEM", "512");
        ENV_VARS.set("BROKER_DISK_SIZE", "5000");
        ENV_VARS.set("BROKER_DISK_TYPE", "ROOT");
        ENV_VARS.set("BROKER_DATA_PATH", "ROOT");
        ENV_VARS.set("PORT_BROKER_PORT","9999");
        ENV_VARS.set("BROKER_PORT","0");

    }

    @Test
    public void testYaml() throws Exception {
        super.testYaml("svc.yml");
    }

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
