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

/**
 * Facilitates installation of DC/OS cert inside framework's JRE.
 */
public class DcosCertInstaller {
    private static final Logger LOGGER = LoggerFactory.getLogger(DcosCertInstaller.class);

    public static final String DEFAULT_JRE_KEYSTORE_PASS = "changeit";
    public static final String CERT_PATH = ".ssl/ca.crt";

    public static boolean installCertificate(String pathToJRE) {
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

            final Path sandboxCertPath = Paths.get(CERT_PATH);
            if (!Files.exists(sandboxCertPath)) {
                LOGGER.info("Cert file not found at the expected path, nothing to do. " +
                        "This is expected when the cluster is not in strict mode: {}",
                        sandboxCertPath.toAbsolutePath());
                return false;
            }

            final Path jreDefaultKeystorePath = Paths.get(pathToJRE, "/lib/security/cacerts");
            final String jreDefaultKeystoreAbsolutePath = jreDefaultKeystorePath.toAbsolutePath().toString();

            final Path keytoolPath = Paths.get(pathToJRE, "/bin/keytool");
            final String keytoolAbsolutePath = keytoolPath.toAbsolutePath().toString();

            final String certAbsolutePath = sandboxCertPath.toAbsolutePath().toString();

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
            return false;
        }
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
