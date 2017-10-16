package com.mesosphere.sdk.dcos;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.offer.ProcessBuilderUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Facilitates installation of DC/OS cert inside framework's JRE.
 */
public class DcosCertInstaller {
    private static final Logger LOGGER = LoggerFactory.getLogger(DcosCertInstaller.class);

    private static final String DEFAULT_JRE_KEYSTORE_PASS = "changeit";
    /**
     * Sandbox location of the ca cert on pre-1.10 DC/OS clusters.
     */
    private static final String PRE_110_CERT_PATH = ".ssl/ca.crt";
    /**
     * Sandbox location of the ca cert on 1.10+ DC/OS clusters.
     */
    private static final String POST_110_CERT_PATH = ".ssl/ca-bundle.crt";

    private static AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Returns whether {@link #installCertificate(String)} has been invoked.
     */
    public static boolean isInitialized() {
        return initialized.get();
    }

    /**
     * Overrides whether {@link #installCertificate(String)} has been invoked, for testing.
     */
    public static void setInitialized(boolean value) {
        initialized.set(value);
    }

    /**
     * Updates the JRE certificate store to include any DC/OS certificates.
     *
     * @return whether any work was performed
     */
    public static boolean installCertificate(String pathToJRE) {
        if (initialized.getAndSet(true)) {
            return false;
        }
        if (StringUtils.isEmpty(pathToJRE)) {
            LOGGER.error("No JRE specified, skipping certificate installation.");
            return false;
        }
        return installCertificate(pathToJRE, new ProcessRunner());
    }

    @VisibleForTesting
    protected static boolean installCertificate(String pathToJRE, ProcessRunner processRunner) {
        try {
            final Path jrePath = Paths.get(pathToJRE);
            if (!Files.exists(jrePath)) {
                LOGGER.warn("JRE not found at path, nothing to do: {}", jrePath.toAbsolutePath());
                return false;
            }

            final Optional<Path> sandboxCertPath = determineCertPath();
            if (!sandboxCertPath.isPresent()) {
                LOGGER.info("Cert file not found in the sandbox. This is expected if the cluster is not in STRICT" +
                        "mode. No work to be done.");
                return false;
            }

            final Path jreDefaultKeystorePath = Paths.get(pathToJRE, "/lib/security/cacerts");
            final String jreDefaultKeystoreAbsolutePath = jreDefaultKeystorePath.toAbsolutePath().toString();

            final Path keytoolPath = Paths.get(pathToJRE, "/bin/keytool");
            final String keytoolAbsolutePath = keytoolPath.toAbsolutePath().toString();

            final String certAbsolutePath = sandboxCertPath.get().toAbsolutePath().toString();

            String cmd = String.format("%s -importcert -noprompt -alias dcoscert -keystore %s -file %s -storepass %s",
                    keytoolAbsolutePath,
                    jreDefaultKeystoreAbsolutePath,
                    certAbsolutePath,
                    DEFAULT_JRE_KEYSTORE_PASS);

            LOGGER.info("Installing DC/OS cert using command: {}", cmd);

            final int exitCode = processRunner.run(ProcessBuilderUtils.buildProcess(cmd, Collections.emptyMap()), 10);
            LOGGER.info("Certificate install process completed with exit code: {}", exitCode);
            return exitCode == 0;
        } catch (Throwable t) {
            LOGGER.error(String.format("Error installing cert inside JRE: %s", pathToJRE), t);
            return true;
        }
    }

    private static Optional<Path> determineCertPath() {
        // (Written 7/11/17) Pre 1.10, the cert path was different than 1.10 onward.
        // We can't determine our version in strict (chicken, egg, etc) without the cert,
        // so we'll blindly try both paths.
        Path sandboxCertPath = Paths.get(POST_110_CERT_PATH);
        if (!Files.exists(sandboxCertPath)) {
            LOGGER.info("Cert file not found at the expected path, {}, for a 1.10 cluster, checking the pre 1.10 path.",
                    sandboxCertPath.toAbsolutePath());

            sandboxCertPath = Paths.get(PRE_110_CERT_PATH);
            if (!Files.exists(sandboxCertPath)) {
                LOGGER.info("Cert file not found at the expected path, {}, for a pre 1.10 cluster.",
                        sandboxCertPath.toAbsolutePath());
                return Optional.empty();
            }
        }

        return Optional.of(sandboxCertPath);
    }

    /**
     * Runs the provided process and returns an exit value. This is broken out into a separate
     * function to allow mockery in tests.
     */
    @VisibleForTesting
    static class ProcessRunner {
        public int run(ProcessBuilder processBuilder, double timeoutSeconds)
                throws IOException, InterruptedException {
            Process process = processBuilder.start();
            synchronized (process) {
                process.wait((long) (timeoutSeconds * 1000));
            }
            return process.exitValue();
        }
    }
}
