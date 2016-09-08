package org.apache.mesos.testutils;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * This class provides utilities useful for all tests which may have Network related needs.
 */
public class NetworkTestUtils {
    public static int getRandomPort() throws IOException {
        ServerSocket serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        serverSocket.close();
        return port;
    }
}
