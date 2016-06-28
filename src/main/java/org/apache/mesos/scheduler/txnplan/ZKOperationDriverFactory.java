package org.apache.mesos.scheduler.txnplan;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.apache.curator.framework.CuratorFramework;
import org.apache.log4j.Logger;
import org.apache.zookeeper.KeeperException;

/**
 * Created by dgrnbrg on 6/28/16.
 */
public class ZKOperationDriverFactory implements OperationDriverFactory {

    private CuratorFramework curator;

    /**
     *
     * @param curator We assume this curator has a namespace set
     */
    public ZKOperationDriverFactory(CuratorFramework curator) {
        this.curator = curator;
    }

    @Override
    public OperationDriver makeDriver(Step step) {
        return new ZKOperationDriver(step);
    }

    public class ZKOperationDriver implements OperationDriver {
        private final String path;
        private final Step step;
        private final Logger logger;

        public ZKOperationDriver(Step step) {
            this.path = "/operations/" + step.getUuid();
            this.step = step;
            this.logger = Logger.getLogger(step.getOperation().getClass());
        }

        @Override
        public void save(Object o) {
            try {
                Output output = new Output(10*4096);
                SerializationUtil.kryos.get().writeClassAndObject(output, o);
                byte[] data = output.toBytes();
                try {
                    curator.create().creatingParentsIfNeeded().forPath(path, data);
                } catch (KeeperException.NodeExistsException e) {
                    curator.setData().forPath(path, data);
                }
            } catch (Throwable t) {
                throw new RuntimeException("Failed to save checkpoint for step " + step.getUuid(), t);
            }
        }

        @Override
        public Object load() {
            try {
                Input input = new Input(curator.getData().forPath(path));
                return SerializationUtil.kryos.get().readClassAndObject(input);
            } catch (KeeperException.NoNodeException e) {
                return null;
            } catch (Throwable t) {
                throw new RuntimeException("Failed to load checkpoint for step " + step.getUuid(), t);
            }
        }

        @Override
        public void info(String msg) {
            logger.info(msg);
        }

        @Override
        public void error(String msg) {
            logger.error(msg);
        }
    }
}
