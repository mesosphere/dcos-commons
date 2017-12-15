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
	tlsEncryptionEnvvar   = "SECURITY_TRANSPORT_ENCRYPTION_ENABLED"
	tlsAllowPlainEnvvar   = "SECURITY_TRANSPORT_ENCRYPTION_ALLOW_PLAINTEXT"
	brokerPort            = "KAFKA_BROKER_PORT"
	brokerPortTLS         = "KAFKA_BROKER_PORT_TLS"
	taskNameEnvvar        = "TASK_NAME"
	frameworkNameEnvvar   = "FRAMEWORK_NAME"
	frameworkHostEnvvar   = "FRAMEWORK_HOST"
	ipEnvvar              = "MESOS_CONTAINER_IP"
	kerberosPrimaryEnvvar = "SECURITY_KERBEROS_PRIMARY"
	kerberosRealmEnvvar   = "SECURITY_KERBEROS_REALM"
	sslAuthEnvvar         = "SECURITY_SSL_AUTHENTICATION_ENABLED"
	authorizationEnvvar   = "SECURITY_AUTHORIZATION_ENABLED"
	superUsersEnvvar      = "SECURITY_AUTHORIZATION_SUPER_USERS"
	brokerCountEnvvar     = "BROKER_COUNT"

	listenersProperty           = "listeners"
	advertisedListenersProperty = "advertised.listeners"
	interBrokerProtocolProperty = "security.inter.broker.protocol"
	superUsersProperty          = "super.users"

	// Based on the RFC5280 the CN cannot be longer than 64 characters
	// ub-common-name INTEGER ::= 64
	cnMaxLength = 64
)

func main() {
	log.Printf("Starting setup-helper...")
	log.Printf("Calculating security and listener settings...")
	err := calculateSettings()
	if err != nil {
		log.Fatalf("Failed to calculate security and listener settings: %s", err.Error())
	}
	log.Printf("Calculated security and listener settings")
	log.Printf("setup-helper complete")
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

func getIntEnvvar(envvar string) int {
	val, set := os.LookupEnv(envvar)
	if !set {
		return 0
	}

	result, err := strconv.Atoi(val)
	if err != nil {
		log.Printf("Could not parse int for envvar: %s (%s)",
			envvar,
			err.Error(),
		)
		return 0
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
	log.Printf("Set listeners")

	log.Print("Setting security.inter.broker.protocol...")
	err = setInterBrokerProtocol()
	if err != nil {
		return err
	}
	log.Print("Set security.inter.broker.protocol")

	log.Print("Setting super.users")
	err = setSuperUsers()
	if err != nil {
		return err
	}
	log.Print("Set super.users")
	return nil
}

func parseToggles() (kerberos bool, tls bool, plaintext bool, authz bool, sslAuth bool) {
	return getBooleanEnvvar(kerberosEnvvar),
		getBooleanEnvvar(tlsEncryptionEnvvar),
		getBooleanEnvvar(tlsAllowPlainEnvvar),
		getBooleanEnvvar(authorizationEnvvar),
		getBooleanEnvvar(sslAuthEnvvar)
}

func setListeners() error {
	var listeners []string
	var advertisedListeners []string

	kerberosEnabled, tlsEncryptionEnabled, allowPlainText, _, _ := parseToggles()

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

	err := writeToWorkingDirectory(listenersProperty,
		"listeners="+strings.Join(listeners, ","))
	err = writeToWorkingDirectory(advertisedListenersProperty,
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
	log.Printf("Attempting to write to %s:\n%s", filename, content)
	wd, err := os.Getwd()
	if err != nil {
		return err
	}
	log.Printf("Calculated working directory as: %s", wd)

	return ioutil.WriteFile(
		path.Join(wd, filename),
		[]byte(content),
		0644,
	)
}

func setInterBrokerProtocol() error {
	kerberosEnabled, tlsEncryptionEnabled, _, _, _ := parseToggles()

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

	return writeToWorkingDirectory(interBrokerProtocolProperty,
		fmt.Sprintf("%s=%s", interBrokerProtocolProperty, protocol))
}

func setSuperUsers() error {
	kerberosEnabled, _, _, authzEnabled, sslAuthEnabled := parseToggles()

	var superUsers []string
	superUsersString := getStringEnvvar(superUsersEnvvar)
	if superUsersString != "" {
		superUsers = strings.Split(superUsersString, ";")
	}

	if authzEnabled {
		if kerberosEnabled {
			superUsers = append(superUsers, fmt.Sprintf("User:%s", getStringEnvvar(kerberosPrimaryEnvvar)))
		} else if sslAuthEnabled {
			superUsers = append(superUsers, getBrokerSSLSuperUsers()...)
		}
	}

	return writeToWorkingDirectory(superUsersProperty, strings.Join(superUsers, ";"))
}

func getBrokerSSLSuperUsers() []string {
	var supers []string
	for i := 0; i < getIntEnvvar(brokerCountEnvvar); i++ {
		cn := fmt.Sprintf("kafka-%d-broker.%s",
			i,
			strings.Replace(getStringEnvvar(frameworkNameEnvvar), "/", "", -1))

		if length := len(cn); length > cnMaxLength {
			cn = cn[length-cnMaxLength:]
		}
		supers = append(supers, fmt.Sprintf(`User:%s`, cn))
	}

	return supers
}
