package org.apache.mesos.scheduler.txnplan;

import java.util.Map;
import java.util.UUID;

/**
 * Created by dgrnbrg on 6/22/16.
 */
public class MockOperationDriverFactory implements OperationDriverFactory {
    private Map<UUID, Object> storage;

    @Override
    public OperationDriver makeDriver(Step step) {
        final UUID id = step.getUuid();
        return new OperationDriver() {
            @Override
            public void save(Object o) {
                storage.put(id, o);
            }

            @Override
            public Object load() {
                return storage.get(id);
            }

            @Override
            public void info(String msg) {
                System.out.println("INFO:" + id.toString() + ":" + msg);
            }

            @Override
            public void error(String msg) {
                System.out.println("ERRO:" + id.toString() + ":" + msg);
            }
        };
    }
}
