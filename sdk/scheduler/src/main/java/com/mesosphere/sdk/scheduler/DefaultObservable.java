package com.mesosphere.sdk.scheduler;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the Observable interface.
 */
public class DefaultObservable implements Observable {
    private Set<Observer> observers;

    public DefaultObservable() {
        observers = ConcurrentHashMap.newKeySet();
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
