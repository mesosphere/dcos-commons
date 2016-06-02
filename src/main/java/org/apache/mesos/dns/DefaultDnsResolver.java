package org.apache.mesos.dns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * DNS resolver that uses the default OS implementation for resolving host names.
 */
public class DefaultDnsResolver implements DnsResolver {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Override
  public InetAddress[] resolve(final String host) throws UnknownHostException {
    return InetAddress.getAllByName(host);
  }

  @Override
  public boolean isResolvable(final String host) {
    boolean success = true;
    logger.debug("Resolving DNS for " + host);
    try {
      resolve(host);
    } catch (SecurityException | IOException e) {
      logger.warn("Couldn't resolve host: " + host);
      success = false;
    }
    return success;
  }
}
