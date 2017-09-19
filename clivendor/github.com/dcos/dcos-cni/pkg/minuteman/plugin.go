package minuteman

import (
	"encoding/json"
	"fmt"
	"io/ioutil"
	"log"
	"os"

	"github.com/containernetworking/cni/pkg/ns"
	"github.com/containernetworking/cni/pkg/skel"

	"github.com/vishvananda/netlink"
)

const DefaultPath = "/var/run/dcos/cni/l4lb"
const IfName = "minuteman"

func setupInterface(netns string) error {
	err := ns.WithNetNSPath(netns, func(_ ns.NetNS) error {
		dummy := &netlink.Dummy{
			LinkAttrs: netlink.LinkAttrs{
				Name: IfName,
			},
		}

		err := netlink.LinkAdd(dummy)
		if err != nil {
			return fmt.Errorf("failed to create dummy interface: %s", err)
		}

		// Bring up the interface
		err = netlink.LinkSetUp(dummy)
		if err != nil {
			return fmt.Errorf("unable to bring the dummy interface up: %s", err)
		}

		return nil
	})

	if err != nil {
		return fmt.Errorf("unable to configure minuteman interface in netns(%s): %s", netns, err)
	}

	return nil
}

func tearDownInterface(netns string) error {
	err := ns.WithNetNSPath(netns, func(_ ns.NetNS) error {
		iface, err := netlink.LinkByName(IfName)
		if err != nil {
			return fmt.Errorf("failed to lookup %s: %s", IfName, err)
		}

		if err = netlink.LinkDel(iface); err != nil {
			return fmt.Errorf("failed to delete %s: %s", IfName, err)
		}

		return nil
	})

	if err != nil {
		return fmt.Errorf("unable to remove minuteman interface in netns(%s): %s", netns, err)
	}

	return nil
}

func CniAdd(args *skel.CmdArgs) error {
	conf := &NetConf{}
	if err := json.Unmarshal(args.StdinData, conf); err != nil {
		return fmt.Errorf("failed to load minuteman netconf: %s", err)
	}

	if conf.Path == "" {
		conf.Path = DefaultPath
	}

	// Create the directory where minuteman will search for the
	// registered containers.
	if err := os.MkdirAll(conf.Path, 0644); err != nil {
		return fmt.Errorf("couldn't create directory for storing minuteman container registration information:%s", err)
	}

	log.Println("Registering netns for containerID", args.ContainerID, " at path: ", conf.Path)

	// Create a file with name `ContainerID` and write the network
	// namespace into this file.
	if err := ioutil.WriteFile(conf.Path+"/"+args.ContainerID, []byte(args.Netns), 0644); err != nil {
		return fmt.Errorf("couldn't checkout point the network namespace for containerID:%s for minuteman", args.ContainerID)
	}

	log.Println("Creating minuteman interface ", IfName)
	// Create a `minuteman` interface.
	if err := setupInterface(args.Netns); err != nil {
		return fmt.Errorf("failure in creating minuteman interface: %s", err)
	}

	return nil
}

func CniDel(args *skel.CmdArgs) error {
	conf := &NetConf{}
	if err := json.Unmarshal(args.StdinData, conf); err != nil {
		return fmt.Errorf("failed to load minuteman netconf: %s", err)
	}

	// For failures just log to `stderr` instead of  failing with an
	// error.
	if conf.Path == "" {
		conf.Path = DefaultPath
	}

	// Remove the container registration.
	if err := os.Remove(conf.Path + "/" + args.ContainerID); err != nil {
		fmt.Fprintf(os.Stderr, "Unable to remove registration for contianerID:%s from minuteman", args.ContainerID)
	}

	log.Println("Removing minuteman interface ", IfName)
	// Deleate the `minuteman` interface.
	if err := tearDownInterface(args.Netns); err != nil {
		return fmt.Errorf("failure in deleting the minuteman interface: %s", err)
	}

	return nil
}
