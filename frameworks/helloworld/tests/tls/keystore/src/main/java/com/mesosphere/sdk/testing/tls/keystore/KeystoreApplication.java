package com.mesosphere.sdk.testing.tls.keystore;

import com.mesosphere.sdk.testing.tls.keystore.cli.TLSTruststoreTestCommand;
import com.mesosphere.sdk.testing.tls.keystore.core.Template;
import com.mesosphere.sdk.testing.tls.keystore.resources.HelloWorldResource;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

/**
 * An example application that can be configured server HTTPS with provided keystore.
 */
public class KeystoreApplication extends Application<KeystoreConfiguration> {

    public static void main(String[] args) throws Exception {
        new KeystoreApplication().run(args);
    }

    @Override
    public String getName() {
        return "hello-world";
    }

    @Override
    public void initialize(Bootstrap<KeystoreConfiguration> bootstrap) {
        // Enable variable substitution with environment variables
        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(
                        bootstrap.getConfigurationSourceProvider(),
                        new EnvironmentVariableSubstitutor(false)
                )
        );

        bootstrap.addCommand(new TLSTruststoreTestCommand<>(this));
    }

    @Override
    public void run(KeystoreConfiguration configuration, Environment environment) {
        final Template template = configuration.buildTemplate();
        environment.jersey().register(new HelloWorldResource(template));
    }
}
