package org.apache.mesos.scheduler;

import java.util.HashSet;
import java.util.Set;

/**
 * Implementation of the Observable interface.
 */
public class DefaultObservable implements Observable {
    private Set<Observer> observers;

    public DefaultObservable() {
        observers = new HashSet<>();
    }

    @Override
    public void subscribe(Observer observer) {
        observers.add(observer);
    }

    @Override
    public void notifyObservers() {
        for (Observer observer : observers) {
            observer.update(this);
        }
    }
}
