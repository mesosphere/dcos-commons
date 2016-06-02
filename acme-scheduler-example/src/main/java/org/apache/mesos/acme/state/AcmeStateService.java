package org.apache.mesos.acme.state;

import org.apache.mesos.Protos;
import org.apache.mesos.reconciliation.TaskStatusProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Observable;
import java.util.Observer;
import java.util.Set;

/**
 */
public class AcmeStateService implements Observer, TaskStatusProvider {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Override
  public void update(Observable o, Object arg) {

  }

  @Override
  public Set<Protos.TaskStatus> getTaskStatuses() throws Exception {
    return null;
  }
}
