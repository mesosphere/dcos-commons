package com.mesosphere.sdk.scheduler;

/** Observable. */
public interface Observable {
    void subscribe(Observer observer);
    void notifyObservers();
}
