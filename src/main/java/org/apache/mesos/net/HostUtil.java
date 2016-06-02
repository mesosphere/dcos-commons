package org.apache.mesos.net;

import org.apache.commons.lang3.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Utility for working with HOST resolution and IP discovery.
 */
public class HostUtil {

  /**
   * returns the host IP.  When running as a Mesos task, this will return
   * the LIBPROCESS_IP.   Working with IP appears to be necessary for
   * datacenters where DNS is not used.
   *
   * @return local IP.
   * @throws UnknownHostException
   */
  public static String getHostIP() throws UnknownHostException {
    return getHostIP("");
  }

  /**
   * This function is compatible with previous code prior to LIBPROCESS_IP.
   * The order of IP discovery is:
   * 1) LIBPROCESS_IP
   * 2) Passed in DefaultIP
   * 3) InetAddress lookup
   * <p/>
   * It is important to note that InetAddress will throw an exception in
   * certain environments which do not allow for IP resolution such as
   * CoreOS on Azure:)
   *
   * @param defaultIP - can be null or "" if there is no default.  It is the IP to use
   *                  if LIBPROCESS_IP doesn't exist.  The common case for this default
   *                  is from a config or environment variable.
   * @return
   * @throws UnknownHostException
   */
  public static String getHostIP(String defaultIP) throws UnknownHostException {

    // Mesos specific IP on agent nodes
    String hostAddress = System.getenv("LIBPROCESS_IP");
    if (StringUtils.isBlank(hostAddress)) {
      hostAddress = defaultIP;
    }

    // if unavailable acquire it the old fashion way
    if (StringUtils.isBlank(hostAddress)) {
      hostAddress = InetAddress.getLocalHost().getHostAddress();
    }
    return hostAddress;
  }

  public static String getHostIPQuietly() {
    try {
      return getHostIP("");
    } catch (UnknownHostException e) {
      return "";
    }
  }
}
