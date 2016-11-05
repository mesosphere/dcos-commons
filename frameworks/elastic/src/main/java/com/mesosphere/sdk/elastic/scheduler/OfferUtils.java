package com.mesosphere.sdk.elastic.scheduler;

import org.apache.mesos.Protos;

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

}
