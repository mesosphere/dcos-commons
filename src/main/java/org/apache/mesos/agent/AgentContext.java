package org.apache.mesos.agent;

import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.apache.mesos.agent.AgentConstants.*;

/**
 * Provides common utility on the agent for executor or task launch settings.
 * All methods return values or null if no value exists.
 */

@Singleton
public class AgentContext {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private Map<String, String> env;

  public AgentContext() {
    env = System.getenv();
  }

  public File getSandboxDir() {
    File sandbox = null;
    String sandboxPath = env.get(MESOS_DIR);
    if (StringUtils.isNotBlank(sandboxPath)) {
      sandbox = new File(sandboxPath);
    }
    return sandbox;
  }

  public Map<String, String> getEnv() {
    return env;
  }

  public String getIP() {
    return env.get(LIBPROCESS_IP);
  }

  public int getPortCount() {
    return getInternalPortList().size();
  }

  public List<Integer> getPortList() {
    return getInternalPortList();

  }

  public Integer getPort(int portIndex) {
    return getInternalPortList().get(portIndex);
  }

  public String getExecutorId() {
    return env.get(MESOS_EXECUTOR_ID);

  }

  public String getTaskId() {
    return env.get(MESOS_TASK_ID);

  }

  public String getAgentId() {
    return env.get(MESOS_SLAVE_ID);

  }

  public String getFrameworkId() {
    return env.get(MESOS_FRAMEWORK_ID);

  }

  private List<Integer> getInternalPortList() {
    List<Integer> portList = new ArrayList<>();
    String ports = env.get(PORTS);
    if (StringUtils.isNotBlank(ports)) {
      String[] portArray = ports.split(",");
      for (String port : portArray) {
        try {
          portList.add(Integer.parseInt(port));
        } catch (NumberFormatException e) {
          logger.error("Invalided Port", e);
        }
      }
    }

    return portList;
  }
}


