package fwd.cloud.frameworks.mongodbsidecar.scheduler;

import com.mesosphere.sdk.api.types.RestartHook;
import com.mesosphere.sdk.api.types.TaskInfoAndStatus;

import java.util.Collection;

/**
 * This provides custom logic to tasks that were restarted.
 */
public class CustomHook implements RestartHook {
  public boolean notify(Collection<TaskInfoAndStatus> tasks, boolean replace) {
    return true;
  }
}
