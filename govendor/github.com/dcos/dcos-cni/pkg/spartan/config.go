package spartan

import (
	"net"

	"github.com/containernetworking/cni/pkg/types"
)

type NetConf struct {
	Enable bool `json:"enable", omitempty"`
}

type IPAM struct {
	Type       string      `json:"type"`
	RangeStart net.IP      `json:"rangeStart"`
	RangeEnd   net.IP      `json:"rangeEnd"`
	Subnet     types.IPNet `json:"subnet"`
}

type Network struct {
	Name      string `json:"name"`
	Interface string `json:"spartanInterface"`
	IPAM      IPAM   `json:"ipam"`
}

const IfName string = "spartan"

// TODO(asridharan): This needs to be derived from the spartan
// configuration.
var IPs = []net.IPNet{
	net.IPNet{
		IP:   net.IPv4(198, 51, 100, 1),
		Mask: net.IPv4Mask(0xff, 0xff, 0xff, 0xff),
	},
	net.IPNet{
		IP:   net.IPv4(198, 51, 100, 2),
		Mask: net.IPv4Mask(0xff, 0xff, 0xff, 0xff),
	},
	net.IPNet{
		IP:   net.IPv4(198, 51, 100, 3),
		Mask: net.IPv4Mask(0xff, 0xff, 0xff, 0xff),
	},
}

// TODO(asridharan): This needs to be derived from the spartan
// configuration.
var Config = Network{
	Name:      "spartan-network",
	Interface: IfName,
	IPAM: IPAM{
		Type:       "host-local",
		RangeStart: net.IPv4(198, 51, 100, 10),
		RangeEnd:   net.IPv4(198, 51, 100, 253),
		Subnet: types.IPNet{
			IP:   net.IPv4(198, 51, 100, 0),
			Mask: net.IPv4Mask(0xff, 0xff, 0xff, 0),
		},
	},
}
