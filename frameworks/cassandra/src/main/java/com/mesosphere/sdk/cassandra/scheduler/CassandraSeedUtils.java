package com.mesosphere.sdk.cassandra.scheduler;

import com.mesosphere.sdk.api.EndpointUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is a collection of useful utilities for use by Cassandra components.
 */
public class CassandraSeedUtils {
    private static final int SEEDS_COUNT;
    static {
        String seedsCount = System.getenv("LOCAL_SEEDS_COUNT");
        if (seedsCount == null) {
            seedsCount = "2";
        }
        SEEDS_COUNT = Integer.parseInt(seedsCount);
    }

    /**
     * Returns the names the autoip based names of the Cassandra seed nodes based on the service name.
     * e.g. 'node-0-server.<svcname>.autoip...,node-1-server.<svcname>.autoip...'
     */
    public static List<String> getLocalSeeds(String serviceName) {
        List<String> localSeeds = new ArrayList<>();
        for (int i = 0; i < getSeedsCount(); ++i) {
            localSeeds.add(EndpointUtils.toAutoIpHostname(serviceName, String.format("node-%d-server", i)));
        }
        return localSeeds;
    }

    /**
     * Returns the number of seed nodes in the cluster.
     */
    public static int getSeedsCount() {
        return SEEDS_COUNT;
    }

    /**
     * Determines whether the node at the provided index is a seed node.
     */
    public static boolean isSeedNode(int index) {
        return index < CassandraSeedUtils.getSeedsCount();
    }
}
