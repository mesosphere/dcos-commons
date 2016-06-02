package org.apache.mesos.process;

import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.stream.StreamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

/**
 * Helps to create processes for command lines commonly used in mesos.
 */
public class ProcessUtil {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessUtil.class);

  public static Process startCmd(String cmd) throws IOException {
    LOG.info(String.format("Starting process: %s", cmd));
    return startCmd("sh", "-c", cmd);
  }

  public static Process startCmd(String... cmd) throws IOException {
    LOG.info(String.format("Starting process: %s", Arrays.asList(cmd)));
    return startCmd(null, cmd);
  }

  public static Process startCmd(Map<String, String> envMap, String... cmd) throws IOException {
    LOG.info(String.format("Starting process: %s", Arrays.asList(cmd)));
    ProcessBuilder processBuilder = new ProcessBuilder(cmd);
    setEnvironment(envMap, processBuilder);
    LOG.info("Process launch dir: " + processBuilder.directory());
    Process process = processBuilder.start();
    StreamUtil.redirectProcess(process);
    return process;
  }

  public static Process startCmdCurrentDir(Map<String, String> envMap, String... cmd) throws IOException {
    LOG.info(String.format("Starting process: %s", Arrays.asList(cmd)));
    ProcessBuilder processBuilder = new ProcessBuilder(cmd);
    setEnvironment(envMap, processBuilder);
    String path = System.getProperty("user.dir");
    processBuilder.directory(new File(path));
    LOG.info("Process launch dir: " + processBuilder.directory());
    Process process = processBuilder.start();
    StreamUtil.redirectProcess(process);
    return process;
  }


  private static void setEnvironment(Map<String, String> envMap, ProcessBuilder processBuilder) {
    if (envMap != null && CollectionUtils.isNotEmpty(envMap.keySet())) {
      for (Map.Entry<String, String> env : envMap.entrySet()) {
        processBuilder.environment().put(env.getKey(), env.getValue());
      }
    }
  }
}
