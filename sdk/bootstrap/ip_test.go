package main

import "testing"
import "os"

func assertIP(t *testing.T, expectedIP string, expectedError bool) {
	ip, err := ContainerIP()
	if expectedError {
		if err == nil {
			t.Errorf("An error was expected: %s", os.Environ())
		}

		return
	}

	if expectedIP != ip.String() {
		t.Errorf("Expected IP: %s. Calculted IP: %s", expectedIP, ip)
	}
}

var ipTests = []struct {
	mesosContainerIP string
	libProcessIP     string
	expectedIP       string
	errorExpected    bool
}{
	{"1.1.1.1", "", "1.1.1.1", false},
	{"banana", "", "", true},
	{"0.0.0.0", "", "", true},
	{"", "1.1.1.1", "1.1.1.1", false},
	{"", "banana", "", true},
	{"", "0.0.0.0", "", true},
	{"", "", "", true},
}

func TestIPLookups(t *testing.T) {
	for _, testCase := range ipTests {
		os.Setenv("MESOS_CONTAINER_IP", testCase.mesosContainerIP)
		os.Setenv("LIBPROCESS_IP", testCase.libProcessIP)
		assertIP(t, testCase.expectedIP, testCase.errorExpected)
	}
}
