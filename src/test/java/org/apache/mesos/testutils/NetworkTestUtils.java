package org.apache.mesos.testutils;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * Created by gabriel on 9/8/16.
 */
public class NetworkTestUtils {
    public static int getRandomPort() throws IOException {
        ServerSocket serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        serverSocket.close();
        return port;
    }
}
