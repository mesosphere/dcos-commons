package org.apache.mesos.acme.state;

/**
 */
public class AcmeStateServiceFactory {

  /**
   * In a "real" system this would create an instance of a class that will store state.
   * this is typically ZK in which case you would need to lookup in the configs the zkurl
   * and connection details.
   * @return
   */
  public AcmeStateService getStateService() {

    return new AcmeStateService();
  }

}
