package spartan

import (
	"encoding/json"
	"fmt"
	"log"
	"net"

	"github.com/containernetworking/cni/pkg/ip"
	"github.com/containernetworking/cni/pkg/ipam"
	"github.com/containernetworking/cni/pkg/ns"
	"github.com/containernetworking/cni/pkg/skel"
	"github.com/containernetworking/cni/pkg/types/current"

	"github.com/vishvananda/netlink"
)

var ipNetMask_32 net.IPMask = net.IPv4Mask(0xff, 0xff, 0xff, 0xff)

type Error string

func (err Error) Error() string {
	return "spartan: " + string(err)
}

func setupContainerVeth(netns, ifName string, mtu int, pr current.Result, spartanIPs []net.IPNet) (string, error) {
	// The IPAM result will be something like IP=192.168.3.5/24,
	// GW=192.168.3.1. What we want is really a point-to-point link but
	// veth does not support IFF_POINTOPONT. So we set the veth
	// interface to 192.168.3.5/32. Since the device netmask is set to
	// /32, this would not add any routes to the main routing table.
	// Therefore, in order to reach the spartan interfaces, we will have
	// to explicitly set routes to the spartan interface through this
	// device.

	var hostVethName string

	err := ns.WithNetNSPath(netns, func(hostNS ns.NetNS) error {
		hostVeth, _, err := ip.SetupVeth(ifName, mtu, hostNS)
		if err != nil {
			return err
		}

		containerVeth, err := netlink.LinkByName(ifName)
		if err != nil {
			return fmt.Errorf("failed to lookup container VETH %q: %s", ifName, err)
		}

		// Configure the container veth with IP address returned by the
		// IPAM, but set the netmask to a /32.
		if err := netlink.LinkSetUp(containerVeth); err != nil {
			return fmt.Errorf("failed to set %q UP: %s", ifName, err)
		}

		// Set the netmask to a /32.
		pr.IPs[0].Address.Mask = ipNetMask_32

		addr := &netlink.Addr{IPNet: &pr.IPs[0].Address, Label: ""}
		if err = netlink.AddrAdd(containerVeth, addr); err != nil {
			return fmt.Errorf("failed to add IP address to %q: %s", ifName, err)
		}

		// Add routes to the spartan interfaces through this interface.
		for _, spartanIP := range spartanIPs {
			spartanRoute := netlink.Route{
				LinkIndex: containerVeth.Attrs().Index,
				Dst:       &spartanIP,
				Scope:     netlink.SCOPE_LINK,
				Src:       pr.IPs[0].Address.IP,
			}

			if err = netlink.RouteAdd(&spartanRoute); err != nil {
				return fmt.Errorf("failed to add spartan route %s: %s", spartanRoute, err)
			}
		}

		hostVethName = hostVeth.Name

		return nil
	})

	return hostVethName, err
}

func CniAdd(args *skel.CmdArgs) error {
	// Delegate plugin seems to be successful, install the spartan
	// network.
	spartanNetConf, err := json.Marshal(Config)
	if err != nil {
		return Error(fmt.Sprintf("failed to marshall the `spartan-network` IPAM configuration: %s", err))
	}

	// Run the IPAM plugin for the spartan network.
	ipamResult, err := ipam.ExecAdd(Config.IPAM.Type, spartanNetConf)
	if err != nil {
		return Error(fmt.Sprintf("failed to get IP address:%s", err))
	}

	result, err := current.NewResultFromResult(ipamResult)
	if err != nil {
		return Error(fmt.Sprintf("unable to parse IPAM result:%s", err))
	}

	if result.IPs == nil {
		return Error("IPAM plugin returned missing IPv4 config")
	}

	// Make sure we got only one IP and that it is IPv4
	switch {
	case len(result.IPs) > 1:
		return Error("Expecting a single IPv4 address from IPAM")
	case result.IPs[0].Address.IP.To4() == nil:
		return Error("Expecting a IPv4 address from IPAM")
	}

	hostVethName, err := setupContainerVeth(args.Netns, Config.Interface, 0, *result, IPs)
	if err != nil {
		return Error(fmt.Sprintf("unable to create veth pair: %s", err))
	}

	hostVeth, err := netlink.LinkByName(hostVethName)
	if err != nil {
		return Error(fmt.Sprintf("failed to lookup host VETH %s: %s", hostVethName, err))
	}

	containerRoute := netlink.Route{
		LinkIndex: hostVeth.Attrs().Index,
		Dst: &net.IPNet{
			IP:   result.IPs[0].Address.IP,
			Mask: ipNetMask_32,
		},
		Scope: netlink.SCOPE_LINK,
	}

	if err = netlink.RouteAdd(&containerRoute); err != nil {
		return Error(fmt.Sprintf("failed to add spartan route %s: %s", containerRoute, err))
	}

	return nil
}

func CniDel(args *skel.CmdArgs) error {
	spartanNetConf, err := json.Marshal(Config)
	if err != nil {
		return Error(fmt.Sprintf("failed to marshall the `spartan-network` IPAM configuration: %s", err))
	}

	if err = ipam.ExecDel(Config.IPAM.Type, spartanNetConf); err != nil {
		return Error(fmt.Sprintf("IPAM unable to invoke DEL:%s", err))
	}

	if args.Netns == "" {
		return nil
	}

	// Ideally, the kernel would clean up the veth and routes within the
	// network namespace when the namespace is destroyed. We are still
	// explicitly deleting the interface here since we don't want to the
	// delegate plugin to see any interfaces during delete that it does
	// not expect.
	err = ns.WithNetNSPath(args.Netns, func(_ ns.NetNS) error {
		// We just need to delete the interface, the associated routes
		// will get deleted by themselves.
		_, err := ip.DelLinkByNameAddr(Config.Interface, netlink.FAMILY_V4)
		if err != nil {
			return err
		}

		return nil
	})

	if err != nil {
		log.Printf("failed to delete spartan interface in container: %s", err)
	}

	return nil
}
