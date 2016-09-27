package org.apache.mesos.scheduler;

/** Observable. */
public interface Observable {
    void subscribe(Observer observer);
    void notifyObservers();
}
