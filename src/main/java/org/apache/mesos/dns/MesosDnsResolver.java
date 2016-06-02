package org.apache.mesos.dns;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;

/**
 * MesosDNSResolver is a generic DNS resolver for working with Mesos-DNS.
 * It provides utilities to resolve the name of a task and DNS resolve a HOST.
 */
public class MesosDnsResolver {

  private String frameworkname;
  private String domain;
  private DnsResolver resolver = new DefaultDnsResolver();

  public static final String DEFAULT_MESOS_DOMAIN = "mesos";

  public MesosDnsResolver(String frameworkname, String domain) {
    this.frameworkname = frameworkname;
    this.domain = domain;
  }

  public MesosDnsResolver(String frameworkname) {
    this(frameworkname, DEFAULT_MESOS_DOMAIN);
  }

  /**
   * It is common in Mesos to need to check for the existance of a number of services.
   * This utility function will do just that.  It returns true if all full host names exist.
   *
   * @param hosts
   * @return
   */
  public boolean isResolvableHostSet(Set<String> hosts) {
    if (CollectionUtils.isEmpty(hosts)) {
      return false;
    }

    boolean resolvable = true;
    for (String host : hosts) {
      if (!resolver.isResolvable(host)) {
        resolvable = false;
        break;
      }
    }
    return resolvable;
  }

  /**
   * Similar to isResolvableHostSet, except the set is full of tasks names and will be
   * verified against the isResolvableTask.
   *
   * @param tasks
   * @return
   */
  public boolean isResolvableTaskSet(Set<String> tasks) {
    if (CollectionUtils.isEmpty(tasks)) {
      return false;
    }

    boolean resolvable = true;
    for (String task : tasks) {
      if (!isResolvableTask(task)) {
        resolvable = false;
        break;
      }
    }
    return resolvable;
  }


  /**
   * This checks to see if the task is resolvable.  This is the simple task name
   * and not the full fully qualified name.  For instance if looking for
   * namenode1.hdfs.mesos, the task name is namenode1.
   *
   * @param taskName
   * @return
   */
  public boolean isResolvableTask(String taskName) {
    return resolver.isResolvable(getFullNameForTask(taskName));
  }

  /**
   * Utility for getting the fully qualify host name of a task.
   *
   * @param taskName
   * @return
   */
  public String getFullNameForTask(String taskName) {
    StringBuilder builder = getFullSRVNameForTaskBuilder(taskName);
    builder.deleteCharAt(builder.length() - 1);
    return builder.toString();
  }

  public String getFullSRVNameForTask(String taskName) {
    return getFullSRVNameForTaskBuilder(taskName).toString();
  }

  public StringBuilder getFullSRVNameForTaskBuilder(String taskName) {
    StringBuilder builder = new StringBuilder(taskName).append(".");
    if (StringUtils.isNotBlank(frameworkname)) {
      builder = builder.append(frameworkname).append(".");
    }
    if (StringUtils.isNotBlank(domain)) {
      builder = builder.append(domain).append(".");
    }

    return builder;
  }
}
