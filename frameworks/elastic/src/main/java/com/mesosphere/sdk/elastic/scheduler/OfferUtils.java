package com.mesosphere.sdk.elastic.scheduler;

import org.apache.mesos.Protos;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class OfferUtils {

    private static final String DELIMITER = "-";


    static String idToName(String nodeType, Integer nodeId) {
        return nodeType + DELIMITER + Integer.toString(nodeId);
    }

    static String nameToTaskType(String nodeName) {
        return nodeName.substring(0, nodeName.indexOf(DELIMITER));
    }

    static Protos.Value.Range buildSinglePortRange(int port) {
        return Protos.Value.Range.newBuilder().setBegin(port).setEnd(port).build();
    }

    static Protos.Environment.Variable createEnvironmentVariable(String key, String value) {
        return Protos.Environment.Variable.newBuilder().setName(key).setValue(value).build();
    }

    static Protos.HealthCheck createCommandHealthCheck(String command, double gracePeriod) {
        Protos.CommandInfo commandInfo = Protos.CommandInfo.newBuilder().setValue(command).build();
        return Protos.HealthCheck.newBuilder().setCommand(commandInfo).setGracePeriodSeconds(gracePeriod).build();
    }

    static String elasticsearchHeapOpts(int heapSizeMb) {
        return String.format("-Xms%1$dM -Xmx%1$dM", heapSizeMb);
    }

    static int heapFromOpts(String esHeapOpts) {
        Pattern pattern = Pattern.compile("-Xms(\\d+)M");
        Matcher matcher = pattern.matcher(esHeapOpts);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

}
