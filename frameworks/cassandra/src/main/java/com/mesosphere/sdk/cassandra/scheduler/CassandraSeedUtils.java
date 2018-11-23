package com.mesosphere.sdk.cassandra.scheduler;

import com.mesosphere.sdk.http.EndpointUtils;
import com.mesosphere.sdk.scheduler.SchedulerConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is a collection of useful utilities for use by Cassandra components.
 */
final class CassandraSeedUtils {
  private static final int SEEDS_COUNT;

  static {
    String seedsCount = System.getenv("LOCAL_SEEDS_COUNT");
    if (seedsCount == null) {
      seedsCount = "2";
    }
    SEEDS_COUNT = Integer.parseInt(seedsCount);
  }

  private CassandraSeedUtils() {}

  /**
   * Returns the names the autoip based names of the Cassandra seed nodes based on the service name.
   * e.g. {@code 'node-0-server.<svcname>.autoip...,node-1-server.<svcname>.autoip...'}
   */
  static List<String> getLocalSeeds(String serviceName, SchedulerConfig schedulerConfig) {
    List<String> localSeeds = new ArrayList<>();
    for (int i = 0; i < getSeedsCount(); ++i) {
      localSeeds.add(EndpointUtils.toAutoIpHostname(serviceName,
          String.format("node-%d-server", i),
          schedulerConfig));
    }
    return localSeeds;
  }

  /**
   * Returns the number of seed nodes in the cluster.
   */
  private static int getSeedsCount() {
    return SEEDS_COUNT;
  }

  /**
   * Determines whether the node at the provided index is a seed node.
   */
  static boolean isSeedNode(int index) {
    return index < CassandraSeedUtils.getSeedsCount();
  }
}
