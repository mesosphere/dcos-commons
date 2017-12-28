package com.mesosphere.sdk.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The main entry point for the custom executor.
 */
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private ExecutorService executorService = null;

    public static void main(String[] args) throws Exception {
        new Main().run();
    }

    private void run() throws Exception {
        executorService = Executors.newCachedThreadPool();
        executorService.submit(new RunnableExecutor(new MesosExecutorDriverFactory().getDriver(new CustomExecutor())));
    }

    private static class RunnableExecutor implements Runnable {
        private final ExecutorDriver executorDriver;

        public RunnableExecutor(ExecutorDriver executorDriver) {
            this.executorDriver = executorDriver;
        }

        @Override
        public void run() {
            Protos.Status status = executorDriver.run();
            LOGGER.info("Driver stopped: status = {}", status);
        }
    }
}
