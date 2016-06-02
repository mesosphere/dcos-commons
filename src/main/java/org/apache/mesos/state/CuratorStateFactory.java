package org.apache.mesos.state;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.mesos.config.ConfigUtil;
import org.apache.mesos.config.ConfigurationService;

/**
 * Creates instances of the CuratorFramework for the framework.
 */
public class CuratorStateFactory {

  private static final String ZK_HOST = "framework.zk.hosts";
  private static final String ZK_POLL_DELAY = "framework.zk.poll-delay";
  private static final String ZK_MAX_RETRY = "framework.zk.max-retries";

  private static final String[] REQUIRED_PROPERTIES =
    {ZK_HOST, ZK_POLL_DELAY, ZK_MAX_RETRY};

  public CuratorFramework getCurator(ConfigurationService frameworkConfigurationService) {

    ConfigUtil.assertAllRequiredProperties(frameworkConfigurationService, REQUIRED_PROPERTIES);

    // todo: use namespace
    String hosts = frameworkConfigurationService.get(ZK_HOST);
    final int pollDelayMs = ConfigUtil.getTypedValue(frameworkConfigurationService, ZK_POLL_DELAY, 0);
    final int curatorMaxRetries = ConfigUtil.getTypedValue(frameworkConfigurationService, ZK_MAX_RETRY, 0);

    RetryPolicy retryPolicy =
      new ExponentialBackoffRetry(pollDelayMs, curatorMaxRetries);
    CuratorFramework client = CuratorFrameworkFactory.builder()
      .connectString(hosts)
      .retryPolicy(retryPolicy)
//      .namespace(HDFSConstants.FRAMEWORK_ZK_NAMESPACE)
      .build();

    client.start();
    return client;
  }
}
