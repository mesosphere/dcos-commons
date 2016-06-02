package org.apache.mesos.dns;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Interface for a resolver.
 */
public interface DnsResolver {

  InetAddress[] resolve(String host) throws UnknownHostException;

  boolean isResolvable(String fullQualifiedName);

}
