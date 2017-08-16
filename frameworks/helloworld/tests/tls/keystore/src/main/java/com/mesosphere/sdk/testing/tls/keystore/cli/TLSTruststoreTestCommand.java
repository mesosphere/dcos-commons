package com.mesosphere.sdk.testing.tls.keystore.cli;

import com.mesosphere.sdk.testing.tls.keystore.KeystoreConfiguration;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.cli.EnvironmentCommand;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.setup.Environment;
import net.sourceforge.argparse4j.inf.Argument;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

/**
 * Runs a application as an HTTP server.
 *
 * @param <T> the {@link Configuration} subclass which is loaded from the configuration file
 */
public class TLSTruststoreTestCommand<T extends Configuration> extends EnvironmentCommand<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TLSTruststoreTestCommand.class);

    private final Class<T> configurationClass;

    public TLSTruststoreTestCommand(Application<T> application) {
        this(
                application,
                "truststoretest",
                "Runs GET request against HTTPS secured URL with provided truststore"
        );
    }

    /**
     * A constructor to allow reuse of the server command as a different name.
     * @param application the application using this command
     * @param name the argument name to invoke this command
     * @param description a summary of what the command does
     */
    protected TLSTruststoreTestCommand(final Application<T> application, final String name, final String description) {
        super(application, name, description);
        this.configurationClass = application.getConfigurationClass();
    }

    @Override
    protected Class<T> getConfigurationClass() {
        return configurationClass;
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);
        addURLArgument(subparser);
    }

    protected Argument addURLArgument(Subparser subparser) {
        return subparser.addArgument("url")
                .help("URL to be tested");
    }

    @Override
    protected void run(Environment environment, Namespace namespace, T configuration) throws Exception {
        try {
            JerseyClientConfiguration jerseyConfig = ((KeystoreConfiguration) configuration)
                    .getJerseyClientConfiguration();
            final Client client = new JerseyClientBuilder(environment)
                    .using(jerseyConfig)
                    .build("TLS-truststore-test");

            WebTarget target = client.target(namespace.getString("url"));
            Response response = target.request().buildGet().invoke();

            LOGGER.info(String.valueOf(response));

            assert response.getStatus() == 200;
        } catch (Exception e) {
            LOGGER.error(String.valueOf(e));
            throw e;
        }
    }

}
