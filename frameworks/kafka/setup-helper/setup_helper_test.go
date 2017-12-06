package main

import "testing"
import "os"
import "path"
import "io/ioutil"
import "github.com/stretchr/testify/assert"
import "log"
import "strings"

func TestCalculateSettingsHappyPath(t *testing.T) {
	asrt := assert.New(t)

	setEnv(kerberosEnvvar, "")
	setEnv(tlsEncryptionEnvvar, "")
	setEnv(tlsAllowPlainEnvvar, "")
	setEnv(brokerPort, "1000")
	setEnv(brokerPortTLS, "1001")
	setEnv(taskNameEnvvar, "a-task")
	setEnv(frameworkHostEnvvar, "a-framework")
	setEnv(ipEnvvar, "127.0.0.1")
	setEnv(kerberosPrimaryEnvvar, "a-kerberos")

	asrt.NoError(calculateSettings())
}

func TestCalculateSettingsListenersError(t *testing.T) {
	asrt := assert.New(t)

	setEnv(kerberosEnvvar, "nope")
	setEnv(tlsEncryptionEnvvar, "")
	setEnv(tlsAllowPlainEnvvar, "")
	setEnv(brokerPort, "1000")
	setEnv(brokerPortTLS, "1001")
	setEnv(taskNameEnvvar, "a-task")
	setEnv(frameworkHostEnvvar, "a-framework")
	setEnv(ipEnvvar, "127.0.0.1")
	setEnv(kerberosPrimaryEnvvar, "a-kerberos")

	asrt.NoError(calculateSettings())
}

var listenerTests = []struct {
	kerberosEnvvarValue         string
	tlsEncryptionEnvvarValue    string
	tlsAllowPlainEnvvarValue    string
	expectedListeners           string
	expectedAdvertisedListeners string
}{
	{ // Everything false
		kerberosEnvvarValue:         "false",
		tlsEncryptionEnvvarValue:    "false",
		tlsAllowPlainEnvvarValue:    "false",
		expectedListeners:           "listeners=PLAINTEXT://127.0.0.1:1000",
		expectedAdvertisedListeners: "advertised.listeners=PLAINTEXT://a-task.a-framework:1000",
	},
	{ // None of the booleans set.
		expectedListeners:           "listeners=PLAINTEXT://127.0.0.1:1000",
		expectedAdvertisedListeners: "advertised.listeners=PLAINTEXT://a-task.a-framework:1000",
	},
	{ // Kerberos enabled, no TLS
		kerberosEnvvarValue:         "true",
		tlsEncryptionEnvvarValue:    "false",
		tlsAllowPlainEnvvarValue:    "false",
		expectedListeners:           "listeners=SASL_PLAINTEXT://127.0.0.1:1000",
		expectedAdvertisedListeners: "advertised.listeners=SASL_PLAINTEXT://a-task.a-framework:1000",
	},
	{ // Kerberos enabled, TLS enabled, No Plaintext
		kerberosEnvvarValue:         "true",
		tlsEncryptionEnvvarValue:    "true",
		tlsAllowPlainEnvvarValue:    "false",
		expectedListeners:           "listeners=SASL_SSL://127.0.0.1:1001",
		expectedAdvertisedListeners: "advertised.listeners=SASL_SSL://a-task.a-framework:1001",
	},
	{ // Kerberos enabled, TLS enabled, Plaintext allowed
		kerberosEnvvarValue:         "true",
		tlsEncryptionEnvvarValue:    "true",
		tlsAllowPlainEnvvarValue:    "true",
		expectedListeners:           "listeners=SASL_SSL://127.0.0.1:1001,SASL_PLAINTEXT://127.0.0.1:1000",
		expectedAdvertisedListeners: "advertised.listeners=SASL_SSL://a-task.a-framework:1001,SASL_PLAINTEXT://a-task.a-framework:1000",
	},
	{ // Kerberos disabled, TLS enabled, No plaintext
		kerberosEnvvarValue:         "false",
		tlsEncryptionEnvvarValue:    "true",
		tlsAllowPlainEnvvarValue:    "false",
		expectedListeners:           "listeners=SSL://127.0.0.1:1001",
		expectedAdvertisedListeners: "advertised.listeners=SSL://a-task.a-framework:1001",
	},
	{ // Kerberos disabled, TLS enabled, Plaintext allowed
		kerberosEnvvarValue:         "false",
		tlsEncryptionEnvvarValue:    "true",
		tlsAllowPlainEnvvarValue:    "true",
		expectedListeners:           "listeners=SSL://127.0.0.1:1001,PLAINTEXT://127.0.0.1:1000",
		expectedAdvertisedListeners: "advertised.listeners=SSL://a-task.a-framework:1001,PLAINTEXT://a-task.a-framework:1000",
	},
}

func TestSetListeners(t *testing.T) {
	asrt := assert.New(t)
	for _, test := range listenerTests {
		log.Print(test)

		cleanUpWDFile(listenersProperty)
		cleanUpWDFile(advertisedListenersProperty)

		// Set the envvars
		os.Clearenv()
		setEnv(kerberosEnvvar, test.kerberosEnvvarValue)
		setEnv(tlsEncryptionEnvvar, test.tlsEncryptionEnvvarValue)
		setEnv(tlsAllowPlainEnvvar, test.tlsAllowPlainEnvvarValue)
		setEnv(brokerPort, "1000")
		setEnv(brokerPortTLS, "1001")
		setEnv(taskNameEnvvar, "a-task")
		setEnv(frameworkHostEnvvar, "a-framework")
		setEnv(ipEnvvar, "127.0.0.1")
		setEnv(kerberosPrimaryEnvvar, "a-kerberos")

		err := setListeners()
		asrt.NoError(err)

		out, err := readWDFile(listenersProperty)
		asrt.NoError(err)
		asrt.Equal(test.expectedListeners, string(out))

		out, err = readWDFile(advertisedListenersProperty)
		asrt.NoError(err)
		asrt.Equal(test.expectedAdvertisedListeners, string(out))
	}

	// Don't leave a trace.
	cleanUpWDFile(listenersProperty)
	cleanUpWDFile(advertisedListenersProperty)
}
func TestGetBooleanEnvvar(t *testing.T) {
	asrt := assert.New(t)
	os.Clearenv()

	asrt.False(getBooleanEnvvar("test"))

	os.Setenv("test", "false")
	asrt.False(getBooleanEnvvar("test"))

	os.Setenv("test", "true")
	asrt.True(getBooleanEnvvar("test"))
}

func TestGetListener(t *testing.T) {
	asrt := assert.New(t)

	os.Setenv(ipEnvvar, "127.0.0.1")
	os.Setenv(brokerPort, "1000")

	asrt.Equal("PLAINTEXT://127.0.0.1:1000", getListener("PLAINTEXT", brokerPort))
	os.Clearenv()
}

func TestGetListenerTLS(t *testing.T) {
	asrt := assert.New(t)

	os.Setenv(ipEnvvar, "127.0.0.2")
	os.Setenv(brokerPortTLS, "1001")

	asrt.Equal("SSL://127.0.0.2:1001", getListener("SSL", brokerPortTLS))
	os.Clearenv()
}

func TestGetAdvertisedListener(t *testing.T) {
	asrt := assert.New(t)

	os.Setenv(taskNameEnvvar, "a-task")
	os.Setenv(frameworkHostEnvvar, "a-framework")
	os.Setenv(brokerPort, "1000")

	asrt.Equal("PLAINTEXT://a-task.a-framework:1000", getAdvertisedListener("PLAINTEXT", brokerPort))
	os.Clearenv()
}

func TestGetAdvertisedListenerTLS(t *testing.T) {
	asrt := assert.New(t)

	os.Setenv(taskNameEnvvar, "a-task")
	os.Setenv(frameworkHostEnvvar, "a-framework")
	os.Setenv(brokerPortTLS, "1001")

	asrt.Equal("SSL://a-task.a-framework:1001", getAdvertisedListener("SSL", brokerPortTLS))
	os.Clearenv()
}

func TestWriteToWorkingDirectory(t *testing.T) {
	asrt := assert.New(t)

	// Make sure the file isn't there by happenstance.
	cleanUpWDFile(t.Name())
	defer func() {
		// Ensure the file gets cleaned up.
		cleanUpWDFile(t.Name())
	}()

	writeToWorkingDirectory(t.Name(), "a test :)")
	out, err := readWDFile(t.Name())
	asrt.NoError(err)
	asrt.Equal("a test :)", string(out))
}

var brokerProtocolTests = []struct {
	kerberosEnvvarValue string
	tlsEnvvarValue      string
	expectedProtocol    string
}{
	{ // Kerberos on, tls off
		kerberosEnvvarValue: "true",
		tlsEnvvarValue:      "false",
		expectedProtocol:    "security.inter.broker.protocol=SASL_PLAINTEXT",
	},
	{ // Kerberos on, tls on
		kerberosEnvvarValue: "true",
		tlsEnvvarValue:      "true",
		expectedProtocol:    "security.inter.broker.protocol=SASL_SSL",
	},
	{ // Kerberos off, tls on
		kerberosEnvvarValue: "false",
		tlsEnvvarValue:      "true",
		expectedProtocol:    "security.inter.broker.protocol=SSL",
	},
}

func TestSetInterBrokerProtocol(t *testing.T) {
	asrt := assert.New(t)
	for _, test := range brokerProtocolTests {
		// Wipe environment.
		os.Clearenv()
		cleanUpWDFile(interBrokerProtocolProperty)

		log.Print(test)

		// Set environment
		setEnv(kerberosEnvvar, test.kerberosEnvvarValue)
		setEnv(tlsEncryptionEnvvar, test.tlsEnvvarValue)

		err := setInterBrokerProtocol()
		asrt.NoError(err)

		out, err := readWDFile(interBrokerProtocolProperty)
		asrt.NoError(err)
		asrt.Equal(test.expectedProtocol, string(out))
	}

	// Leave no trace.
	cleanUpWDFile(interBrokerProtocolProperty)
}

var setSuperUserTests = []struct {
	description    string
	kerberos       string
	sslAuth        string
	authz          string
	superUsers     string
	expectedOutput string
}{
	{
		description: "No envvars set, should create empty super.users file.",
	},
	{
		description: "Kerberos enabled, sslAuth enabled, authz disabled => empty super.users file",
		kerberos:    "true",
		sslAuth:     "true",
	},
	{
		description:    "Kerberos enabled, sslAuth enabled, authz enabled => kerberos super.users file",
		kerberos:       "true",
		sslAuth:        "true",
		authz:          "true",
		expectedOutput: "User:kafka",
	},
	{
		description:    "Kerberos enabled, sslAuth enabled, authz enabled, with super users => kerberos super.users file appended to",
		kerberos:       "true",
		sslAuth:        "true",
		authz:          "true",
		superUsers:     "User:evan;User:ben",
		expectedOutput: "User:evan;User:ben;User:kafka",
	},
	{
		description:    "Kerberos disabled, sslAuth enabled, authz enabled => ssl auth super.users file",
		kerberos:       "false",
		sslAuth:        "true",
		authz:          "true",
		expectedOutput: "User:kafka-0-broker.framework",
	},
	{
		description:    "Kerberos disabled, sslAuth enabled, authz enabled, with super users => ssl auth super.users file appended to",
		kerberos:       "false",
		sslAuth:        "true",
		authz:          "true",
		superUsers:     "User:evan;User:ben",
		expectedOutput: "User:evan;User:ben;User:kafka-0-broker.framework",
	},
}

func TestSetSuperUsers(t *testing.T) {
	asrt := assert.New(t)

	for _, test := range setSuperUserTests {
		cleanUpWDFile(superUsersProperty)
		os.Clearenv()

		os.Setenv(brokerCountEnvvar, "1")
		os.Setenv(frameworkNameEnvvar, "framework")
		os.Setenv(kerberosPrimaryEnvvar, "kafka")

		os.Setenv(kerberosEnvvar, test.kerberos)
		os.Setenv(sslAuthEnvvar, test.sslAuth)
		os.Setenv(authorizationEnvvar, test.authz)
		os.Setenv(superUsersEnvvar, test.superUsers)

		setSuperUsers()

		out, err := readWDFile(superUsersProperty)
		asrt.NoError(err)
		asrt.Equal(test.expectedOutput, string(out))
	}

	// Leave no trace!
	cleanUpWDFile(superUsersProperty)
}

var brokerSuperUserTests = []struct {
	brokerCount    string
	frameworkName  string
	expectedOutput string
}{
	{
		brokerCount:    "not an int",
		frameworkName:  "framework",
		expectedOutput: "",
	},
	{
		brokerCount:    "0",
		frameworkName:  "framework",
		expectedOutput: "",
	},
	{
		brokerCount:    "1",
		frameworkName:  "framework",
		expectedOutput: "User:kafka-0-broker.framework",
	},
	{
		brokerCount:    "2",
		frameworkName:  "framework",
		expectedOutput: "User:kafka-0-broker.framework;User:kafka-1-broker.framework",
	},
	{
		brokerCount:    "1",
		frameworkName:  "a/long/framework/with/slashes/that/is/very/long/friends/seriously/so/long",
		expectedOutput: "User:er.alongframeworkwithslashesthatisverylongfriendsseriouslysolong",
	},
}

func TestGetBrokerSSLSuperUsers(t *testing.T) {
	asrt := assert.New(t)

	for _, test := range brokerSuperUserTests {
		os.Clearenv()
		os.Setenv(frameworkNameEnvvar, test.frameworkName)
		os.Setenv(brokerCountEnvvar, test.brokerCount)

		asrt.Equal(test.expectedOutput, strings.Join(getBrokerSSLSuperUsers(), ";"))
	}

}

func cleanUpWDFile(file string) {
	wd, _ := os.Getwd()
	os.Remove(path.Join(wd, file))
}

func readWDFile(file string) ([]byte, error) {
	wd, _ := os.Getwd()
	return ioutil.ReadFile(path.Join(wd, file))
}

func setEnv(envvar string, value string) {
	if value != "" {
		os.Setenv(envvar, value)
	}
}
