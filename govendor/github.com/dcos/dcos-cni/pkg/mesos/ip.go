package mesos

import (
	"fmt"
	"net"
	"os"
)

// This helper function gives a Mesos container's IP address, when used from
// the container's network namespace.
//
// The function first checks the presence of
// `MESOS_CONTAINER_IP`  and than `LIBPROCESS_IP` (in that order).  The
// expectation is that only one of these variables would be set.  Starting
// Mesos 1.4 the `default-executor` setsup the container's IP address in
// `MESOS_CONTAINER_IP`. However, prior to Mesos 1.4 if the container was
// running on the host network the IP address would be set in `LIBPROCESS_IP
// and if the container was running on a CNI network the `LIBPROCESS_IP` would
// be set to 0.0.0.0 forcing a hostname resolution to resolv the container's
// CNI IP address.
func ContainerIP() (containerIP net.IP, err error) {
	// NOTE: Make sure to maintain the order of the environment variables.
	envs := [2]string{"MESOS_CONTAINER_IP", "LIBPROCESS_IP"}
	for _, env := range envs {
		ip := os.Getenv(env)
		if ip != "" {
			containerIP = net.ParseIP(ip)
			if containerIP == nil {
				err = fmt.Errorf("Invalid %s found: %s", env, ip)
				return
			}

			// Make sure its an IPv4 address.
			if containerIP.To4() == nil {
				err = fmt.Errorf("Expecting a valid IPv4 address in `%s` got %s", env, containerIP)
				return
			}

			// Got a valid IP. We only look at the first valid IP,
			// hence the order of parsing the environment variables
			// is important here.
			break
		}
	}

	// The expectation is that either the `MESOS_CONTAINER_IP` or the `LIBPROCESS_IP` should be set.
	if containerIP == nil {
		err = fmt.Errorf("Cannot find container IP address. Either `MESOS_CONTAINER_IP` or `LIBPROCESS_IP` should be set")
		return
	}

	// Ideally we should only see the `MESOS_CONTAINER_IP` env variable to
	// decipher the container's IP address. However, pre Mesos 1.4, for
	// container's running on CNI networks the `LIBPROCESS_IP` is set to
	// 0.0.0.0 and the expectation is to decipher the container's IP
	// address by hostname resolution. Handling pre-Mesos 1.4 containers
	// running on CNI networks over here.
	if containerIP.Equal(net.ParseIP("0.0.0.0")) {
		hostName, _err := os.Hostname()
		if _err != nil {
			err = fmt.Errorf("Unable to retrieve hostname: %s", _err)
			return
		}

		ipAddr, _err := net.ResolveIPAddr("ip", hostName)
		if _err != nil {
			err = fmt.Errorf("Unable to resolve hostname(%s): %s", hostName, _err)
			return
		}

		containerIP = ipAddr.IP
	}

	return
}
