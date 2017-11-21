package main

import (
	"fmt"
	"io/ioutil"
	"log"
	"os"
	"path"
	"strconv"
	"strings"
)

const (
	kerberosEnvvar        = "SECURITY_KERBEROS_ENABLED"
	tlsEncryptionEnvvar   = "KAFKA_ENABLE_TLS"
	tlsAllowPlainEnvvar   = "KAFKA_ALLOW_PLAINTEXT"
	brokerPort            = "KAFKA_BROKER_PORT"
	brokerPortTLS         = "KAFKA_BROKER_PORT_TLS"
	mesosSandboxEnvvar    = "MESOS_SANDBOX"
	taskNameEnvvar        = "TASK_NAME"
	frameworkHostEnvvar   = "FRAMEWORK_HOST"
	ipEnvvar              = "MESOS_CONTAINER_IP"
	kerberosPrimaryEnvvar = "SECURITY_KERBEROS_PRIMARY"
)

func main() {
	log.Printf("Starting setup-helper...")
	log.Printf("Calculating security and listener settings...")
	err := calculateSettings()
	if err != nil {
		log.Fatalf("Failed to calculate security and listener settings: %s", err.Error())
	}
	log.Printf("Calculated security and listener settings.")
	log.Printf("setup-helper complete.")
}

func getBooleanEnvvar(envvar string) (bool, error) {
	val, set := os.LookupEnv(envvar)
	if !set {
		return false, nil
	}

	result, err := strconv.ParseBool(val)
	if err != nil {
		return false, fmt.Errorf("Could not parse boolean for envvar %s: %s",
			envvar,
			err.Error())
	}

	return result, nil
}

func getStringEnvvar(envvar string) string {
	return os.Getenv(envvar)
}

func calculateSettings() error {
	log.Printf("Setting listeners...")
	err := setListeners()
	if err != nil {
		return err
	}
	log.Printf("Set listeners.")
	// log.Printf("Setting security settings...")
	return nil
}

func setListeners() error {
	var listeners []string
	var advertisedListeners []string

	kerberosEnabled, err := getBooleanEnvvar(kerberosEnvvar)
	if err != nil {
		return err
	}
	tlsEncryptionEnabled, err := getBooleanEnvvar(tlsEncryptionEnvvar)
	if err != nil {
		return err
	}
	allowPlainText, err := getBooleanEnvvar(tlsAllowPlainEnvvar)
	if err != nil {
		return err
	}

	if kerberosEnabled {
		// Kerberos is enabled.
		if allowPlainText {
			listeners = append(listeners,
				getListener("SASL_PLAINTEXT", brokerPort))
			advertisedListeners = append(advertisedListeners,
				getAdvertisedListener("SASL_PLAINTEXT", brokerPort))
		}

		if tlsEncryptionEnabled {
			listeners = append(listeners,
				getListener("SASL_SSL", brokerPortTLS))
			advertisedListeners = append(advertisedListeners,
				getAdvertisedListener("SASL_SSL", brokerPortTLS))
		}
	} else {
		if allowPlainText {
			listeners = append(listeners,
				getListener("PLAINTEXT", brokerPort))
			advertisedListeners = append(advertisedListeners,
				getAdvertisedListener("PLAINTEXT", brokerPort))
		}

		if tlsEncryptionEnabled {
			listeners = append(listeners,
				getListener("SSL", brokerPortTLS))
			advertisedListeners = append(advertisedListeners,
				getAdvertisedListener("SSL", brokerPortTLS))
		}
	}

	err = writeToWorkingDirectory("listeners-config",
		"listeners="+strings.Join(listeners, ","))
	err = writeToWorkingDirectory("advertised-listeners-config",
		"advertised.listeners="+strings.Join(advertisedListeners, ","))
	return err
}

func getListener(protocol string, portEnvvar string) string {
	return fmt.Sprintf("%s://%s:%s",
		protocol,
		getStringEnvvar(ipEnvvar),
		getStringEnvvar(portEnvvar),
	)
}

func getAdvertisedListener(protocol string, portEnvvar string) string {
	return fmt.Sprintf("%s://%s.%s:%s",
		protocol,
		getStringEnvvar(taskNameEnvvar),
		getStringEnvvar(frameworkHostEnvvar),
		getStringEnvvar(portEnvvar),
	)
}

func writeToWorkingDirectory(filename string, content string) error {
	wd, err := os.Getwd()
	if err != nil {
		return err
	}

	return ioutil.WriteFile(
		path.Join(wd, filename),
		[]byte(content),
		0644,
	)
}
