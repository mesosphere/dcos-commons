package com.mesosphere.sdk.testutils;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;

/**
 * This class encapsulates utilities for speeding up use of Curator's TestingServer.
 */
public class CuratorTestUtils {
    private static final int RETRY_DELAY_MS = 1000;
    private static final String ZOOKEEPER_ROOT_NODE_NAME = "zookeeper";

    public static final String USERNAME = "testusername";
    public static final String PASSWORD = "testpassword";

    public static void clear(TestingServer testingServer) throws Exception {
        CuratorFramework client = getClient(testingServer);
        client.start();

        for (String rootNode : client.getChildren().forPath("/")) {
            if (!rootNode.equals(ZOOKEEPER_ROOT_NODE_NAME)) {
                client.delete().deletingChildrenIfNeeded().forPath("/" + rootNode);
            }
        }

        client.close();
    }

    private static CuratorFramework getClient(TestingServer testingServer) {
        return CuratorFrameworkFactory.newClient(
                testingServer.getConnectString(),
                new RetryOneTime(RETRY_DELAY_MS));
    }
}
