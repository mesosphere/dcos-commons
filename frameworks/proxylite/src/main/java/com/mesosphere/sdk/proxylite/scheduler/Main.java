package com.mesosphere.sdk.proxylite.scheduler;

import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;

import java.io.File;
import java.util.Collections;

/**
 * Hello World Service.
 */
public class Main {
    private static final Integer COUNT = Integer.valueOf(System.getenv("PROXYLITE_COUNT"));
    private static final Double CPUS = Double.valueOf(System.getenv("PROXYLITE_CPUS"));
    private static final String POD_TYPE = "proxylite";
    private static final String TASK_NAME = "proxylite";

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            new ProxyLite(new File(args[0]));
        }
    }

    private static class ProxyLite extends DefaultService {
        public ProxyLite(File pathToYamlSpecification) throws Exception {
            super(pathToYamlSpecification);
        }

        @Override
        protected Protos.FrameworkInfo getFrameworkInfo(ServiceSpec serviceSpec, StateStore stateStore) {
            Protos.FrameworkInfo frameworkInfo = super.getFrameworkInfo(serviceSpec, stateStore);
            return frameworkInfo.toBuilder()
                    .setWebuiUrl("FIX ME HERE!")
                    .build();
        }
    }
}
