package main

import (
	"fmt"
	"net"
	"os"
)

var libprocessIP = "LIBPROCESS_IP"
var mesosContainerIP = "MESOS_CONTAINER_IP"

// ContainerIP This helper function gives the current container's IP address, when run in a
// given network, mnt and UTS namespace.
func ContainerIP() (net.IP, error) {
	// First, try MESOS_CONTAINER_IP (DC/OS 1.10+, when running the default executor)
	ip, err := validateIP(mesosContainerIP)
	if ip != nil {
		return ip, err
	}

	// Fall through to LIBPROCESS_IP (< DC/OS 1.10, custom executor)
	ip, err = validateIP(libprocessIP)
	if ip != nil {
		return ip, err
	}

	// LIBPROCESS_IP is empty or is set to 0.0.0.0 or ::
	hostName, err := os.Hostname()
	if err != nil {
		return nil, fmt.Errorf("Can't get hostName: %s", err)
	}

	ipAddr, err := net.ResolveIPAddr("ip", hostName)
	if err != nil {
		return nil, fmt.Errorf("Can't get IP address: %s", err)
	}

	return ipAddr.IP, nil
}

func validateIP(envvar string) (net.IP, error) {
	ip := os.Getenv(envvar)
	if ip == "" {
		return nil, nil
	}

	validIP := net.ParseIP(ip)
	if validIP == nil {
		return nil, fmt.Errorf("Invalid %s found: %s", envvar, ip)
	}

	if validIP.String() == "0.0.0.0" || validIP.String() == "::" {
		return nil, fmt.Errorf("%s is INADDR_ANY: %s", envvar, ip)
	}

	return validIP, nil

}
