package org.apache.mesos.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Failure utilities.
 */
public class FailureUtils {
  private static final Logger log = LoggerFactory.getLogger(FailureUtils.class);

  @edu.umd.cs.findbugs.annotations.SuppressWarnings(
    value = "DM_EXIT",
    justification = "Framework components should fail fast sometimes.")
  public static void exit(String msg, Integer exitCode) {
    log.error(msg);
    System.exit(exitCode);
  }
}
