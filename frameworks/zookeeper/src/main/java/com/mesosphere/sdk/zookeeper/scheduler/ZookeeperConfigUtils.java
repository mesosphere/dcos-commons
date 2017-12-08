package com.mesosphere.sdk.zookeeper.scheduler;

import com.mesosphere.sdk.http.EndpointUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * This class returns a collection of seed nodes for zookeeper cluster
 * It returns in a format that the zookeeper config understands
 */
public class ZookeeperConfigUtils {
    /**
     * Returns the list of zookeeper servers configuration
     * e.g. 'server.1=zookeeper-0-node.autoip...:2888:3888'
     */
    public static List<String> getZookeeperNodes(String serviceName) {
        List<String> zkNodes = new ArrayList<>();
        for (int i = 0; i < getNodeCount(); i++) {
            String taskDns = EndpointUtils.toAutoIpHostname(serviceName, String.format("zookeeper-%d-node", i));
            zkNodes.add(String.format("server.%d=%s:%s:%s",
                i + 1, taskDns, getLeaderPort(), getLeaderElectionPort()));
        }
        return zkNodes;
    }

    /**
     * Returns the number of seed nodes in the cluster
     */
    private static int getNodeCount() {
        return Integer.parseInt(System.getenv("NODE_COUNT"));
    }

    /**
     * Returns the port followers use to connect to the leader
     */
    private static String getLeaderPort() {
        return System.getenv("TASKCFG_ALL_LEADER_PORT");
    }

    /**
     * Returns the port used for leader election
     */
    private static String getLeaderElectionPort() {
        return System.getenv("TASKCFG_ALL_LEADER_ELECTION_PORT");
    }
}
