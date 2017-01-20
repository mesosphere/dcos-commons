package com.mesosphere.sdk.proxylite.scheduler;

import com.mesosphere.sdk.specification.DefaultService;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Proxy-Lite Service.
 */
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

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
                    .setWebuiUrl("http://proxylite-0-server.proxylite.mesos:4040")
                    .build();
        }
    }
}
