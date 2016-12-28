package com.mesosphere.sdk.scheduler;

/** Both an Observer and Observable.  Forwards notifications. */
public class ChainedObserver extends DefaultObservable implements Observer {
    @Override
    public void update(Observable obj) {
        notifyObservers();
    }
}
