package org.apache.mesos.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;

import java.util.Random;

public class TestExecutorTask extends ExecutorTask {

    public TestExecutorTask(Protos.TaskInfo taskInfo, ExecutorDriver driver) {
    }

    @Override
    public void start() {
        System.out.println("Doing some work");

        final int expected = new Random().nextInt(100);

        while (true) {
            int test = new Random().nextInt(100);
            if (test == expected) {
                break;
            }
        }

        System.out.println("Done with work");
    }

    @Override
    public boolean checkHealth() {
        return true;
    }

    @Override
    public void stop() {

    }
}
