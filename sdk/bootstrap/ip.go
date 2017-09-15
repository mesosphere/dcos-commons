package main

import (
	"fmt"
	"net"
	"os"
)

// This helper function gives the current container's IP address, when run in a
// given network, mnt and UTS namespace.  The function first checks the presence of
// `LIBPROCESS_IP` if present it will return this as the container's IP address
// else it will look at the `hostname` setup for the container to pick out the
// IP address associated with the hostname.
func ContainerIP() (containerIP net.IP, err error) {
	ip := os.Getenv("LIBPROCESS_IP")
	if ip != "" {
		containerIP = net.ParseIP(ip)
		if containerIP == nil {
			err = fmt.Errorf("Invalid LIBPROCESS_IP found: %s", ip)
			return
		}

		if containerIP.String() != "0.0.0.0" && containerIP.String() != "::" {
			return
		}
	}

	// LIBPROCESS_IP is empty or is set to 0.0.0.0 or ::
	hostName, err := os.Hostname()
	if err != nil {
		err = fmt.Errorf("Unable to retrieve hostname: %s", err)
		return
	}

	ipAddr, err := net.ResolveIPAddr("ip", hostName)
	if err != nil {
		err = fmt.Errorf("Unable to resolve hostname(%s): %s", hostName, err)
		return
	}

	containerIP = ipAddr.IP
	return
}
