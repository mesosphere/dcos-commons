package com.mesosphere.sdk.curator;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;

public class CuratorTestUtils {
    public static void clear(TestingServer testingServer) throws Exception {
        CuratorFramework client =
                CuratorFrameworkFactory.newClient(testingServer.getConnectString(), new RetryOneTime(1000));
        client.start();

        for (String rootNode : client.getChildren().forPath("/")) {
            if (!rootNode.equals("zookeeper")) {
                client.delete().deletingChildrenIfNeeded().forPath("/" + rootNode);
            }
        }

        client.close();
    }
}
