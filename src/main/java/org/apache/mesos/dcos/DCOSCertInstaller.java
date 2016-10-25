package org.apache.mesos.dcos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Facilitates installation of DC/OS cert inside framework's JRE.
 */
public class DCOSCertInstaller {
    private static final Logger LOGGER = LoggerFactory.getLogger(DCOSCertInstaller.class);

    public static final String DEFAULT_JRE_KEYSTORE_PASS = "changeit";
    public static final String CERT_PATH = ".ssl/ca.crt";

    public static void installCertificate(String pathToJRE) {
        try {
            final Path jrePath = Paths.get(pathToJRE);
            // Check if JRE is present, there's nothing to do.
            if (!Files.exists(jrePath)) {
                LOGGER.info("JRE not found at path: {}", jrePath.toAbsolutePath());
                return;
            }

            final Path sandboxCertPath = Paths.get(CERT_PATH);
            // Check if cert is present, there's nothing to do.
            if (!Files.exists(sandboxCertPath)) {
                LOGGER.info("Cert file not found at path: {}", sandboxCertPath.toAbsolutePath());
                return;
            }

            final Path jreDefaultKeystorePath = Paths.get(pathToJRE, "/lib/security/cacerts");
            final String jreDefaultKeystoreAbsolutePath = jreDefaultKeystorePath.toAbsolutePath().toString();

            final Path keytoolPath = Paths.get(pathToJRE, "/bin/keytool");
            final String keytoolAbsolutePath = keytoolPath.toAbsolutePath().toString();

            final String certAbsolutePath = sandboxCertPath.toAbsolutePath().toString();

            String command = keytoolAbsolutePath + " -importcert -noprompt";
            command += " -alias dcoscert";
            command += " -keystore " + jreDefaultKeystoreAbsolutePath;
            command += " -file " + certAbsolutePath;
            command += " -storepass " + DEFAULT_JRE_KEYSTORE_PASS;

            LOGGER.info("Installing DC/OS cert using command: {}", command);

            final ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", "-c", command).inheritIO();
            final Process certInstallerProcess = processBuilder.start();
            final int exitCode = certInstallerProcess.waitFor();

            LOGGER.info("Certificate install process completed with exit code: {}", exitCode);
        } catch (Throwable t) {
            LOGGER.error("Error installing cert", t);
        }
    }
}
