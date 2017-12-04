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

func getBooleanEnvvar(envvar string) bool {
	val, set := os.LookupEnv(envvar)
	if !set {
		return false
	}

	result, err := strconv.ParseBool(val)
	if err != nil {
		log.Printf("Could not parse boolean for envvar: %s (%s)",
			envvar,
			err.Error(),
		)
		return false
	}

	return result
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

	log.Print("Setting security.inter.broker.protocol...")
	err = setInterBrokerProtocol()
	if err != nil {
		return err
	}
	log.Print("Set security.inter.broker.protocol.")
	return nil
}

func parseToggles() (kerberos bool, tls bool, plaintext bool) {
	kerberosEnabled := getBooleanEnvvar(kerberosEnvvar)
	tlsEncryptionEnabled := getBooleanEnvvar(tlsEncryptionEnvvar)
	allowPlainText := getBooleanEnvvar(tlsAllowPlainEnvvar)

	return kerberosEnabled, tlsEncryptionEnabled, allowPlainText
}

func setListeners() error {
	var listeners []string
	var advertisedListeners []string

	kerberosEnabled, tlsEncryptionEnabled, allowPlainText := parseToggles()

	if kerberosEnabled { // Kerberos enabled

		if tlsEncryptionEnabled { // Transport encryption on
			listeners = append(listeners,
				getListener("SASL_SSL", brokerPortTLS))
			advertisedListeners = append(advertisedListeners,
				getAdvertisedListener("SASL_SSL", brokerPortTLS))

			if allowPlainText { // Allow plaintext as well
				listeners = append(listeners,
					getListener("SASL_PLAINTEXT", brokerPort))
				advertisedListeners = append(advertisedListeners,
					getAdvertisedListener("SASL_PLAINTEXT", brokerPort))
			}
		} else { // Plaintext only
			listeners = append(listeners,
				getListener("SASL_PLAINTEXT", brokerPort))
			advertisedListeners = append(advertisedListeners,
				getAdvertisedListener("SASL_PLAINTEXT", brokerPort))
		}

	} else if tlsEncryptionEnabled { // No kerberos, but Transport encryption is on
		listeners = append(listeners,
			getListener("SSL", brokerPortTLS))
		advertisedListeners = append(advertisedListeners,
			getAdvertisedListener("SSL", brokerPortTLS))

		if allowPlainText { // Plaintext allowed
			listeners = append(listeners,
				getListener("PLAINTEXT", brokerPort))
			advertisedListeners = append(advertisedListeners,
				getAdvertisedListener("PLAINTEXT", brokerPort))
		}
	} else { // No TLS, no Kerberos, Plaintext only
		listeners = append(listeners,
			getListener("PLAINTEXT", brokerPort))
		advertisedListeners = append(advertisedListeners,
			getAdvertisedListener("PLAINTEXT", brokerPort))
	}

	err := writeToWorkingDirectory("listeners-config",
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

func setInterBrokerProtocol() error {
	const property = "security.inter.broker.protocol"
	kerberosEnabled, tlsEncryptionEnabled, _ := parseToggles()

	protocol := ""
	if kerberosEnabled {
		if tlsEncryptionEnabled {
			protocol = "SASL_SSL"
		} else {
			protocol = "SASL_PLAINTEXT"
		}
	} else if tlsEncryptionEnabled {
		protocol = "SSL"
	} else {
		protocol = "PLAINTEXT"
	}

	return writeToWorkingDirectory(property, fmt.Sprintf("%s=%s", property, protocol))
}
